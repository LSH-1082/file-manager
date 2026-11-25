package com.fileManager.file;

import com.fileManager.ApiTestSupport;
import com.fileManager.file.application.StoredFileService;
import com.fileManager.file.domain.FileUpload;
import com.fileManager.file.domain.StoredFile;
import com.fileManager.file.presentation.FileController;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

class FileApiTest extends ApiTestSupport {
    private static final String NOT_FOUND = "저장된 파일을 찾을 수 없습니다.";
    @Autowired StoredFileService storedFileService;
    @Autowired PlatformTransactionManager tm;

    private static FileUpload upload(String name, String content) {
        byte[] bytes = content.getBytes();
        return new FileUpload(name, "text/plain", bytes.length, () -> new ByteArrayInputStream(bytes));
    }

    private String resource(String fileName, byte[] bytes, String mime) {
        String cat = post("/api/categories", Map.of("name", "c")).json().get("id").asString();
        Response r = multipart(HttpMethod.POST, "/api/categories/" + cat + "/resources",
                Map.of("kind", "etc", "title", "t"), Map.of("file", new Part(fileName, bytes, mime)));
        assertEquals(201, r.status(), r.text());
        return r.json().get("id").asString();
    }

    @Test
    void downloadByResourceIdWithHeaders() {
        // given
        byte[] bytes = {0, 1, 2, (byte) 255, 10};
        String id = resource("a.bin", bytes, "application/octet-stream");

        // when
        Response r = get("/api/files/" + id);

        // then
        assertEquals(200, r.status());
        assertArrayEquals(bytes, r.bytes());
        assertEquals("application/octet-stream", r.headers().getContentType().toString());
        assertEquals(5, r.headers().getContentLength());
        assertEquals("attachment; filename=\"a.bin\"; filename*=UTF-8''a.bin", r.headers().getFirst("Content-Disposition"));
        assertEquals("no-store", r.headers().getFirst("Cache-Control"));
    }

    @Test
    void koreanFileNameDisposition() {
        // given
        String id = resource("수정본 (최종).docx", "x".getBytes(), "text/plain");

        // when & then: 한글 이름은 글자마다 '_'로 대체한다
        // docs/api 4절 예시의 ASCII 대체 이름은 '__ (__)'지만 한글 3글자는 '___'이 맞다(명세 예시 오타, 규칙은 글자마다 '_')
        assertEquals("attachment; filename=\"___ (__).docx\"; filename*=UTF-8''"
                        + "%EC%88%98%EC%A0%95%EB%B3%B8%20%28%EC%B5%9C%EC%A2%85%29.docx",
                get("/api/files/" + id).headers().getFirst("Content-Disposition"));

        // when & then: 따옴표·역슬래시와 특수문자도 대체하거나 인코딩한다
        assertEquals("a_b_c.txt", FileController.disposition("a\"b\\c.txt").replaceAll("^.*?filename=\"([^\"]*)\".*$", "$1"));
        assertTrue(FileController.disposition("a*b'c").endsWith("a%2Ab%27c"));
    }

    @Test
    void downloadByStoredFileId() {
        // given
        resource("a.txt", "hi".getBytes(), "text/plain");
        String fileId = jdbc.sql("SELECT id FROM stored_files").query(String.class).single();

        // when
        Response r = get("/api/files/" + fileId);

        // then
        assertEquals(200, r.status());
        assertEquals("hi", r.text());
    }

    @Test
    void missingOrMalformedIdIs404() {
        // when & then
        assertError(get("/api/files/" + UUID.randomUUID()), 404, "NOT_FOUND", NOT_FOUND);
        assertError(get("/api/files/zzz"), 404, "NOT_FOUND", NOT_FOUND);
    }

    @Test
    void missingBodyIs404() throws Exception {
        // given
        String id = resource("a.txt", "hi".getBytes(), "text/plain");
        try (var s = Files.walk(storageRoot())) {
            for (Path p : s.filter(Files::isRegularFile).toList()) Files.delete(p);
        }

        // when & then
        assertError(get("/api/files/" + id), 404, "NOT_FOUND", NOT_FOUND);
    }

    /** 롤백이면 새로 쓴 본문을 지운다(실패 유도). */
    @Test
    void rolledBackInsertRemovesBody() {
        // given
        TransactionTemplate tx = new TransactionTemplate(tm);

        // when & then: 본문을 쓴 트랜잭션이 실패하면 본문과 행이 남지 않는다
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(st -> {
            storedFileService.insert(new FileUpload("a.txt", "text/plain", 1, () -> new ByteArrayInputStream("x".getBytes())), null, "t");
            assertEquals(1, storedBodies());
            throw new IllegalStateException("fail");
        }));
        assertEquals(0, storedBodies());
        assertEquals(0, jdbc.sql("SELECT count(*) FROM stored_files").query(Integer.class).single());
    }

    /** 커밋 전에는 본문을 남기고 커밋 뒤에 지운다. 롤백되면 행도 본문도 그대로다. */
    @Test
    void deleteHappensAfterCommit() {
        // given
        String id = resource("a.txt", "hi".getBytes(), "text/plain");
        TransactionTemplate tx = new TransactionTemplate(tm);

        // when: 고아 본문을 하나 더 쓴다
        StoredFile orphan = tx.execute(st -> storedFileService.insert(upload("b.txt", "y"), null, "t"));

        // then: 본문이 둘이다
        assertEquals(2, storedBodies());

        // when & then: 지우는 트랜잭션 안에서는 본문이 남고 커밋 뒤에 지워진다
        tx.executeWithoutResult(st -> {
            storedFileService.delete(List.of(orphan));
            assertEquals(2, storedBodies());
        });
        assertEquals(1, storedBodies());
        assertEquals("hi", get("/api/files/" + id).text());

        // when & then: 지운 뒤 롤백되면 행도 본문도 그대로다
        StoredFile orphan2 = tx.execute(st -> storedFileService.insert(upload("c.txt", "z"), null, "t"));
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(st -> {
            storedFileService.delete(List.of(orphan2));
            throw new IllegalStateException("fail");
        }));
        assertEquals(2, storedBodies());
        assertTrue(storedFileService.find(orphan2.getId()).isPresent());
    }

    private List<String> diskNames(String dir) {
        try (var st = Files.list(Path.of(storedFileService.storageRoot()).resolve(dir))) {
            return st.map(p -> p.getFileName().toString()).sorted().toList();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** 표시 이름과 저장 이름이 따로 저장된다. */
    @Test
    void insertStoresDisplayAndStoredNamesSeparately() {
        // given
        TransactionTemplate tx = new TransactionTemplate(tm);
        java.time.Instant at = java.time.Instant.parse("2026-11-03T03:00:00Z");

        // when
        StoredFile f = tx.execute(st -> storedFileService.insert(upload("결산표.PDF", "z"), "김재무", "재무/v1",
                "재무 v1.PDF", "재무 v1_20261103_김재무.PDF", at));

        // then
        assertEquals("재무 v1.PDF", jdbc.sql("SELECT file_name FROM stored_files").query(String.class).single());
        assertEquals("재무/v1/재무 v1_20261103_김재무.PDF",
                jdbc.sql("SELECT storage_key FROM stored_files").query(String.class).single());
        assertEquals("재무 v1_20261103_김재무.PDF", f.downloadName());
        assertEquals(at, f.getUploadedAt());
        assertEquals(List.of("재무 v1_20261103_김재무.PDF"), diskNames("재무/v1"));
    }

    /** 이름 변경 뒤 롤백되면 디스크 이름과 storage_key가 원래대로다. */
    @Test
    void rolledBackRenameRestoresNameAndKey() {
        // given
        TransactionTemplate tx = new TransactionTemplate(tm);
        StoredFile f = tx.execute(st -> storedFileService.insert(upload("a.txt", "z"), "김", "d",
                "재무 v1.txt", "재무 v1_20261103_김.txt", java.time.Instant.parse("2026-11-03T03:00:00Z")));

        // when & then: 이름을 바꾼 뒤 롤백되면 디스크 이름과 storage_key가 원래대로다
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(st -> {
            StoredFile cur = storedFileService.find(f.getId()).orElseThrow();
            storedFileService.rename(cur, "회계 v1.txt", "회계 v1_20261103_김.txt");
            assertEquals(List.of("회계 v1_20261103_김.txt"), diskNames("d"));
            throw new IllegalStateException("fail");
        }));
        assertEquals(List.of("재무 v1_20261103_김.txt"), diskNames("d"));
        assertEquals("d/재무 v1_20261103_김.txt", jdbc.sql("SELECT storage_key FROM stored_files").query(String.class).single());
        assertEquals("재무 v1.txt", jdbc.sql("SELECT file_name FROM stored_files").query(String.class).single());
    }
}
