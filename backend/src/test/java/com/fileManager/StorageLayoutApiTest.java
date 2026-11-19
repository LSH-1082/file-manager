package com.fileManager;

import com.fileManager.category.application.CategoryService;
import com.fileManager.file.infrastructure.LocalFileStorage;
import com.fileManager.file.presentation.FileController;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;

/** 저장 루트 아래 실제 폴더·파일 구조를 HTTP 호출로 확인한다. */
class StorageLayoutApiTest extends ApiTestSupport {
    @Autowired CategoryService categoryService;
    @Autowired PlatformTransactionManager tm;

    private Path at(String... parts) {
        Path p = storageRoot();
        for (String s : parts) p = p.resolve(s);
        return p;
    }

    private static Set<String> names(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return Set.of();
        try (Stream<Path> s = Files.list(dir)) {
            return new TreeSet<>(s.map(p -> p.getFileName().toString()).toList());
        }
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    private void assertDownload(String fileId, String content) {
        Response r = get("/api/files/" + fileId);
        assertEquals(200, r.status());
        assertEquals(content, r.text());
    }

    private static JsonNode latest(JsonNode doc) {
        JsonNode vs = doc.get("versions");
        return vs.get(vs.size() - 1);
    }

    private static String fileId(JsonNode version, int index) {
        return version.get("files").get(index).get("id").asString();
    }

    private String storageKey(String fileId) {
        return jdbc.sql("SELECT storage_key FROM stored_files WHERE id = :id").param("id", fileId)
                .query(String.class).single();
    }

    /** 파일의 올린 시각(응답 uploadedAt)을 서버 시간대 날짜로 바꾼 yyyyMMdd. 저장 이름의 일자와 같은 기준이다. */
    private static String day(JsonNode fileRev) {
        return DateTimeFormatter.BASIC_ISO_DATE.format(
                LocalDate.ofInstant(Instant.parse(fileRev.get("uploadedAt").asString()), ZoneId.systemDefault()));
    }

    /** 저장 이름: {stem}_{올린 날}_김철수{ext}. */
    private static String st(JsonNode fileRev, String stem, String ext) {
        return stem + "_" + day(fileRev) + "_김철수" + ext;
    }

    private static JsonNode orig(JsonNode doc) {
        return latest(doc).get("files").get(0);
    }

    private JsonNode docById(String id) {
        for (JsonNode d : get("/api/documents").json()) {
            if (d.get("id").asString().equals(id)) return d;
        }
        return fail("문서 없음: " + id);
    }

    private void assertAllDownloadable(JsonNode doc) {
        for (JsonNode v : doc.get("versions")) {
            for (JsonNode f : v.get("files")) {
                assertEquals(200, get("/api/files/" + f.get("id").asString()).status());
                if (f.has("revision")) {
                    assertEquals(200, get("/api/files/" + f.get("revision").get("id").asString()).status());
                }
            }
        }
    }

    private static String disposition(Response r) {
        return r.headers().getFirst("Content-Disposition");
    }

    @Test
    void categoryCreatesFolder() throws IOException {
        category("계약");
        assertTrue(Files.isDirectory(at("계약")));
        assertEquals(Set.of("계약"), names(storageRoot()));
    }

    @Test
    void firstUploadStoresOriginalInV1() throws IOException {
        JsonNode doc = newDoc("계약");
        JsonNode f = orig(doc);
        String n = st(f, "계약 v1", ".pdf");
        assertEquals("original", read(at("계약", "v1", n)));
        assertEquals(Set.of(n), names(at("계약", "v1")));
        assertEquals("계약/v1/" + n, storageKey(f.get("id").asString()));
        assertEquals("계약 v1.pdf", f.get("fileName").asString());
        assertEquals(n, f.get("downloadName").asString());
        assertDownload(f.get("id").asString(), "original");
    }

    @Test
    void revisionAndRevisionSheetGoToEditFolder() throws IOException {
        JsonNode doc = newDoc("계약");
        String id = doc.get("id").asString();
        String origName = st(orig(doc), "계약 v1", ".pdf");
        JsonNode d1 = addRevision(id, "김철수").json();
        assertEquals(Set.of(origName, "edit1"), names(at("계약", "v1")));
        Path edit1 = at("계약", "v1", "edit1");
        JsonNode first = latest(d1).get("files").get(1);
        String rev1 = st(first, "계약 v1", ".hwpx");
        String diff1 = st(first.get("revision"), "계약 v1 수정대조표", ".pdf");
        assertEquals(Set.of(rev1, diff1), names(edit1));
        assertEquals("revised", read(edit1.resolve(rev1)));
        assertEquals("diff", read(edit1.resolve(diff1)));

        assertEquals("계약 v1.hwpx", first.get("fileName").asString());
        assertEquals(rev1, first.get("downloadName").asString());
        assertEquals("계약 v1 수정대조표.pdf", first.get("revision").get("fileName").asString());
        assertEquals(diff1, first.get("revision").get("downloadName").asString());

        JsonNode d2 = addRevision(id, "김철수").json();
        assertEquals(Set.of(origName, "edit1", "edit2"), names(at("계약", "v1")));
        Path edit2 = at("계약", "v1", "edit2");
        JsonNode second = latest(d2).get("files").get(2);
        String rev2 = st(second, "계약 v1", ".hwpx");
        String diff2 = st(second.get("revision"), "계약 v1 수정대조표", ".pdf");
        assertEquals(Set.of(rev2, diff2), names(edit2));
        assertEquals("계약/v1/edit2/" + rev2, storageKey(second.get("id").asString()));
        assertDownload(second.get("id").asString(), "revised");
        assertDownload(second.get("revision").get("id").asString(), "diff");
        assertDownload(first.get("id").asString(), "revised");

        assertEquals(FileController.disposition(rev2), disposition(get("/api/files/" + second.get("id").asString())));
        assertEquals(FileController.disposition(diff2),
                disposition(get("/api/files/" + second.get("revision").get("id").asString())));
        assertEquals(FileController.disposition(origName), disposition(get("/api/files/" + fileId(latest(d2), 0))));
    }

    @Test
    void revisionNamesDifferEvenWithSameOriginalNames() throws IOException {
        JsonNode doc = newDoc("계약");
        String id = doc.get("id").asString();
        Response r = multipart(HttpMethod.POST, "/api/documents/" + id + "/revisions", Map.of("worker", "김철수"),
                Map.of("file", Part.of("a.pdf", "x"), "revision", Part.of("a.pdf", "y")));
        assertEquals(200, r.status(), r.text());
        Path edit1 = at("계약", "v1", "edit1");
        JsonNode f = latest(r.json()).get("files").get(1);
        String first = st(f, "계약 v1", ".pdf");
        String second = st(f.get("revision"), "계약 v1 수정대조표", ".pdf");
        assertEquals(Set.of(first, second), names(edit1));
        assertEquals("x", read(edit1.resolve(first)));
        assertEquals("y", read(edit1.resolve(second)));

        assertEquals(200, delete("/api/documents/" + id + "/revisions/" + fileId(latest(r.json()), 1)).status());
        r = multipart(HttpMethod.POST, "/api/documents/" + id + "/revisions", Map.of("worker", "김철수"),
                Map.of("file", Part.of("readme", "x"), "revision", Part.of("diff", "y")));
        assertError(r, 400, "INVALID_INPUT", ".hwpx 또는 .pdf 파일만 올릴 수 있습니다.");
        assertFalse(Files.exists(edit1));
    }

    @Test
    void deletingLatestRevisionRemovesBothFilesAndEditFolder() throws IOException {
        JsonNode doc = newDoc("계약");
        String id = doc.get("id").asString();
        JsonNode withRev = addRevision(id, "김철수").json();
        String revId = fileId(latest(withRev), 1);
        Response r = delete("/api/documents/" + id + "/revisions/" + revId);
        assertEquals(200, r.status(), r.text());
        assertEquals(Set.of(st(orig(doc), "계약 v1", ".pdf")), names(at("계약", "v1")));
        assertFalse(Files.exists(at("계약", "v1", "edit1")));
        assertEquals(1, storedFileRows());
    }

    @Test
    void renamingVersionMovesEditFolder() throws IOException {
        JsonNode doc = newDoc("계약");
        String id = doc.get("id").asString();
        JsonNode withRev = addRevision(id, "김철수").json();
        JsonNode rev = latest(withRev).get("files").get(1);
        Response r = patch("/api/documents/" + id + "/versions/" + latest(withRev).get("id").asString(),
                Map.of("name", "초안"));
        assertEquals(200, r.status(), r.text());
        assertFalse(Files.exists(at("계약", "v1")));
        assertEquals(2, names(at("계약", "초안", "edit1")).size());
        JsonNode renamed = latest(r.json()).get("files").get(1);
        String revName = renamed.get("downloadName").asString();
        assertEquals(st(rev, "계약 초안", ".hwpx"), revName);
        assertEquals("계약/초안/edit1/" + revName, storageKey(rev.get("id").asString()));
        assertDownload(rev.get("id").asString(), "revised");
        assertDownload(rev.get("revision").get("id").asString(), "diff");
    }

    @Test
    void renamingWorkingVersionRenamesFolderAndFiles() throws IOException {
        JsonNode doc = newDoc("계약");
        String id = doc.get("id").asString();
        JsonNode withRev = addRevision(id, "김철수").json();
        JsonNode v = latest(withRev);
        Response r = patch("/api/documents/" + id + "/versions/" + v.get("id").asString(), Map.of("name", "초안"));
        assertEquals(200, r.status(), r.text());
        assertFalse(Files.exists(at("계약", "v1")));
        JsonNode o = orig(r.json());
        String origName = st(o, "계약 초안", ".pdf");
        assertEquals("original", read(at("계약", "초안", origName)));
        assertEquals("계약 초안.pdf", o.get("fileName").asString());
        assertEquals(origName, o.get("downloadName").asString());
        JsonNode f = latest(r.json()).get("files").get(1);
        assertEquals("revised", read(at("계약", "초안", "edit1", st(f, "계약 초안", ".hwpx"))));
        assertEquals("diff", read(at("계약", "초안", "edit1", st(f.get("revision"), "계약 초안 수정대조표", ".pdf"))));
        assertEquals("계약 초안.hwpx", f.get("fileName").asString());
        assertEquals("계약 초안 수정대조표.pdf", f.get("revision").get("fileName").asString());
        assertAllDownloadable(r.json());
    }

    @Test
    void newVersionCreatesFolderAndSharesSubmittedOriginal() throws IOException {
        JsonNode doc = newDoc("계약");
        String id = doc.get("id").asString();
        fixture(latest(doc).get("id").asString(), "sent");
        Response r = post("/api/documents/" + id + "/versions", Map.of("versionName", "v2", "worker", "김철수"));
        assertEquals(200, r.status(), r.text());
        assertTrue(Files.isDirectory(at("계약", "v2")));
        assertEquals(Set.of(), names(at("계약", "v2")));
        JsonNode v2 = latest(r.json());
        String original = fileId(v2, 0);
        assertEquals("계약/v1/" + st(orig(doc), "계약 v1", ".pdf"), storageKey(original));
        assertDownload(original, "original");
    }

    @Test
    void newVersionOriginalCanPointIntoPreviousEditFolder() throws IOException {
        JsonNode doc = newDoc("계약");
        String id = doc.get("id").asString();
        JsonNode withRev = addRevision(id, "김철수").json();
        String revName = latest(withRev).get("files").get(1).get("downloadName").asString();
        fixture(latest(withRev).get("id").asString(), "sent");
        Response r = post("/api/documents/" + id + "/versions", Map.of("versionName", "v2", "worker", "김철수"));
        assertEquals(200, r.status(), r.text());
        String original = fileId(latest(r.json()), 0);
        assertEquals("계약/v1/edit1/" + revName, storageKey(original));
        assertDownload(original, "revised");
    }

    /** v1 발송 뒤 v2에 수정본을 올린 문서: 계약 v1(발송) + v2(작업중, 수정본 1). */
    private JsonNode sentV1WithWorkingV2Revision() {
        JsonNode doc = newDoc("계약");
        String id = doc.get("id").asString();
        fixture(latest(doc).get("id").asString(), "sent");
        assertEquals(200, post("/api/documents/" + id + "/versions", Map.of("versionName", "v2", "worker", "김철수")).status());
        Response r = addRevision(id, "김철수");
        assertEquals(200, r.status(), r.text());
        return r.json();
    }

    @Test
    void renamingSecondVersionKeepsSharedOriginalName() throws IOException {
        JsonNode doc = sentV1WithWorkingV2Revision();
        String id = doc.get("id").asString();
        JsonNode v1Original = doc.get("versions").get(0).get("files").get(0);
        String origName = st(v1Original, "계약 v1", ".pdf");
        Response r = patch("/api/documents/" + id + "/versions/" + latest(doc).get("id").asString(), Map.of("name", "초안"));
        assertEquals(200, r.status(), r.text());
        JsonNode v2 = latest(r.json());
        JsonNode shared = v2.get("files").get(0);
        assertEquals(v1Original.get("id").asString(), shared.get("id").asString());
        assertEquals("계약/v1/" + origName, storageKey(shared.get("id").asString()));
        assertEquals("계약 v1.pdf", shared.get("fileName").asString());
        assertEquals(origName, shared.get("downloadName").asString());
        JsonNode f = v2.get("files").get(1);
        assertEquals("계약 초안.hwpx", f.get("fileName").asString());
        assertEquals("계약 초안 수정대조표.pdf", f.get("revision").get("fileName").asString());
        assertEquals("계약/초안/edit1/" + st(f, "계약 초안", ".hwpx"), storageKey(f.get("id").asString()));
        assertEquals("계약/초안/edit1/" + st(f.get("revision"), "계약 초안 수정대조표", ".pdf"),
                storageKey(f.get("revision").get("id").asString()));
        assertAllDownloadable(r.json());
    }

    @Test
    void renamingCategoryFollowsSentAndWorkingVersions() throws IOException {
        JsonNode doc = sentV1WithWorkingV2Revision();
        String id = doc.get("id").asString();
        Response r = put("/api/categories/" + doc.get("categoryId").asString(), Map.of("name", "회계"));
        assertEquals(200, r.status(), r.text());
        JsonNode after = docById(id);
        JsonNode v1Original = after.get("versions").get(0).get("files").get(0);
        JsonNode v2 = latest(after);
        JsonNode f = v2.get("files").get(1);
        assertEquals("회계 v1.pdf", v1Original.get("fileName").asString());
        assertEquals("회계 v1_" + day(v1Original) + "_김철수.pdf", v1Original.get("downloadName").asString());
        assertEquals("회계/v1/" + v1Original.get("downloadName").asString(), storageKey(v1Original.get("id").asString()));
        assertEquals(v1Original.get("id").asString(), v2.get("files").get(0).get("id").asString());
        assertEquals("회계 v2.hwpx", f.get("fileName").asString());
        assertEquals("회계 v2 수정대조표.pdf", f.get("revision").get("fileName").asString());
        assertEquals("회계/v2/edit1/" + f.get("downloadName").asString(), storageKey(f.get("id").asString()));
        assertEquals(Set.of("회계 v2_" + day(f) + "_김철수.hwpx", "회계 v2 수정대조표_" + day(f.get("revision")) + "_김철수.pdf"),
                names(at("회계", "v2", "edit1")));
        assertEquals(Set.of("회계 v1_" + day(v1Original) + "_김철수.pdf"), names(at("회계", "v1")));
        assertAllDownloadable(after);
    }

    @Test
    void forbiddenCharactersInCategoryNameAreReplacedOnDiskOnly() throws IOException {
        String cat = category("재/무?");
        Response up = uploadDoc(cat, "김철수");
        assertEquals(201, up.status(), up.text());
        JsonNode f = orig(up.json());
        String disk = "재_무_ v1_" + day(f) + "_김철수.pdf";
        assertEquals("original", read(at("재_무_", "v1", disk)));
        assertEquals("재/무? v1.pdf", f.get("fileName").asString());
        assertEquals(disk, f.get("downloadName").asString());
        assertDownload(f.get("id").asString(), "original");
    }

    @Test
    void renameCollisionGetsSuffixAndDoesNotFail() throws IOException {
        JsonNode doc = newDoc("계약");
        String id = doc.get("id").asString();
        JsonNode withRev = addRevision(id, "김철수").json();
        JsonNode rev = latest(withRev).get("files").get(1);
        String taken = "계약 초안_" + day(rev) + "_김철수.hwpx";
        Files.writeString(at("계약", "v1", "edit1", taken), "other");
        Response r = patch("/api/documents/" + id + "/versions/" + latest(withRev).get("id").asString(),
                Map.of("name", "초안"));
        assertEquals(200, r.status(), r.text());
        JsonNode f = latest(r.json()).get("files").get(1);
        String expected = "계약 초안_" + day(rev) + "_김철수 (2).hwpx";
        assertEquals(expected, f.get("downloadName").asString());
        assertEquals("계약/초안/edit1/" + expected, storageKey(f.get("id").asString()));
        assertEquals("계약 초안.hwpx", f.get("fileName").asString());
        assertEquals("other", read(at("계약", "초안", "edit1", taken)));
        assertDownload(f.get("id").asString(), "revised");
        assertAllDownloadable(r.json());
    }

    @Test
    void rolledBackCategoryRenameRestoresFolderFilesAndKeys() throws IOException {
        JsonNode doc = newDoc("계약");
        JsonNode withRev = addRevision(doc.get("id").asString(), "김철수").json();
        String cat = doc.get("categoryId").asString();
        String sql = "SELECT CONCAT(id, '|', storage_key, '|', file_name) FROM stored_files ORDER BY id";
        List<String> rows = jdbc.sql(sql).query(String.class).list();
        Set<String> v1Before = names(at("계약", "v1"));
        Set<String> edit1Before = names(at("계약", "v1", "edit1"));

        TransactionTemplate tx = new TransactionTemplate(tm);
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(st -> {
            categoryService.update(cat, "회계");
            assertTrue(Files.isDirectory(at("회계", "v1")));
            throw new IllegalStateException("fail");
        }));

        assertEquals(Set.of("계약"), names(storageRoot()));
        assertEquals(v1Before, names(at("계약", "v1")));
        assertEquals(edit1Before, names(at("계약", "v1", "edit1")));
        assertEquals(rows, jdbc.sql(sql).query(String.class).list());
        assertAllDownloadable(docById(withRev.get("id").asString()));
        assertEquals("계약", docById(withRev.get("id").asString()).get("title").asString());
    }

    @Test
    void storageRootApiReturnsAbsolutePath() {
        Response r = get("/api/storage-root");
        assertEquals(200, r.status(), r.text());
        assertEquals(storageRoot().toString(), r.json().get("path").asString());
        assertTrue(Path.of(r.json().get("path").asString()).isAbsolute());
    }

    @Test
    void deletingVersionRemovesOnlyItsFolder() throws IOException {
        JsonNode doc = newDoc("계약");
        String id = doc.get("id").asString();
        fixture(latest(doc).get("id").asString(), "sent");
        JsonNode v2Doc = post("/api/documents/" + id + "/versions", Map.of("versionName", "v2", "worker", "김철수")).json();
        assertEquals(200, addRevision(id, "김철수").status());
        assertEquals(Set.of("edit1"), names(at("계약", "v2")));
        Response r = delete("/api/documents/" + id + "/versions/" + latest(v2Doc).get("id").asString());
        assertEquals(200, r.status(), r.text());
        assertFalse(Files.exists(at("계약", "v2")));
        assertEquals("original", read(at("계약", "v1", st(orig(doc), "계약 v1", ".pdf"))));
    }

    @Test
    void resourcesGoToResourceFolder() throws IOException {
        String cat = category("계약");
        Response r = multipart(HttpMethod.POST, "/api/categories/" + cat + "/resources",
                Map.of("kind", "reference", "title", "참고"), Map.of("file", Part.of("a.txt", "hello")));
        assertEquals(201, r.status(), r.text());
        assertEquals("hello", read(at("계약", "_자료", "a.txt")));
        assertDownload(r.json().get("id").asString(), "hello");

        assertEquals(200, put("/api/categories/" + cat, Map.of("name", "회계")).status());
        assertEquals("hello", read(at("회계", "_자료", "a.txt")));
        assertEquals(Set.of("a.txt"), names(at("회계", "_자료")));
        String fileName = jdbc.sql("SELECT file_name FROM stored_files").query(String.class).single();
        assertEquals("a.txt", fileName);
        assertDownload(r.json().get("id").asString(), "hello");
    }

    @Test
    void deletingCategoryRemovesResourcesAndMovesDocumentToUnclassified() throws IOException {
        JsonNode doc = newDoc("계약");
        String cat = doc.get("categoryId").asString();
        assertEquals(201, multipart(HttpMethod.POST, "/api/categories/" + cat + "/resources",
                Map.of("kind", "etc", "title", "t"), Map.of("file", Part.of("a.txt", "hello"))).status());
        Response r = delete("/api/categories/" + cat);
        assertEquals(204, r.status());
        assertFalse(Files.exists(at("계약")));
        assertEquals("original", read(at("_미분류", "계약", "v1", st(orig(doc), "계약 v1", ".pdf"))));
        assertEquals(Set.of("_미분류"), names(storageRoot()));
        assertEquals(1, storedFileRows());
        assertDownload(fileId(latest(doc), 0), "original");
    }

    @Test
    void assigningCategoryMovesVersionFoldersAndRenamesFiles() throws IOException {
        JsonNode doc = newDoc("계약");
        String id = doc.get("id").asString();
        JsonNode rev = latest(addRevision(id, "김철수").json()).get("files").get(1);
        String revName = rev.get("downloadName").asString();
        assertEquals(204, delete("/api/categories/" + doc.get("categoryId").asString()).status());
        assertEquals(2, names(at("_미분류", "계약", "v1", "edit1")).size());
        assertEquals("_미분류/계약/v1/edit1/" + revName, storageKey(rev.get("id").asString()));
        Response r = post("/api/documents/" + id + "/category",
                Map.of("newCategory", Map.of("name", "신규")));
        assertEquals(200, r.status(), r.text());
        JsonNode o = orig(r.json());
        String origName = st(o, "신규 v1", ".pdf");
        String newRev = st(rev, "신규 v1", ".hwpx");
        assertEquals("original", read(at("신규", "v1", origName)));
        assertEquals("revised", read(at("신규", "v1", "edit1", newRev)));
        assertEquals("신규/v1/edit1/" + newRev, storageKey(rev.get("id").asString()));
        assertEquals("신규 v1.pdf", o.get("fileName").asString());
        assertEquals(origName, o.get("downloadName").asString());
        assertEquals(newRev, latest(r.json()).get("files").get(1).get("downloadName").asString());
        assertFalse(Files.exists(at("_미분류", "계약")));
        assertDownload(fileId(latest(r.json()), 0), "original");
        assertEquals("신규/v1/" + origName, storageKey(fileId(latest(r.json()), 0)));
        assertAllDownloadable(r.json());
    }

    @Test
    void dangerousNamesStayInsideRoot() throws IOException {
        Path parent = storageRoot().getParent();
        Set<String> before = names(parent);
        for (String name : List.of("../밖", "a:b*c", "CON", "_미분류")) {
            Response r = post("/api/categories", Map.of("name", name));
            assertEquals(201, r.status(), r.text());
        }
        String cat = category("정상");
        Response up = multipart(HttpMethod.POST, "/api/documents", Map.of("categoryId", cat, "worker", "김철수"),
                Map.of("file", Part.of("..\\x.pdf", "x")));
        assertEquals(201, up.status(), up.text());

        assertEquals(before, names(parent));
        Set<String> inRoot = names(storageRoot());
        assertEquals(5, inRoot.size(), inRoot.toString());
        assertTrue(inRoot.contains("a_b_c"), inRoot.toString());
        assertTrue(inRoot.contains("_CON"), inRoot.toString());
        assertTrue(inRoot.contains("_미분류 (2)"), inRoot.toString());
        assertTrue(inRoot.stream().noneMatch(n -> n.contains("/") || n.contains("\\") || n.equals("..")));
        assertFalse(Files.exists(at("_미분류")), "예약 폴더는 카테고리가 쓰지 않는다");
        assertEquals("x", read(at("정상", "v1", st(orig(up.json()), "정상 v1", ".pdf"))));
        try (Stream<Path> s = Files.walk(storageRoot())) {
            assertTrue(s.allMatch(p -> p.normalize().startsWith(storageRoot())));
        }
    }

    @Test
    void sameCategoryNamesGetSuffix() throws IOException {
        category("이름");
        category("이름");
        assertEquals(Set.of("이름", "이름 (2)"), names(storageRoot()));
    }

    @Test
    void renamingCategoryRenamesFolderAndFiles() throws IOException {
        JsonNode doc = newDoc("계약");
        String id = doc.get("id").asString();
        String cat = doc.get("categoryId").asString();
        JsonNode rev = latest(addRevision(id, "김철수").json()).get("files").get(1);
        Response r = put("/api/categories/" + cat, Map.of("name", "Contract"));
        assertEquals(200, r.status(), r.text());
        String origName = st(orig(doc), "Contract v1", ".pdf");
        String revName = st(rev, "Contract v1", ".hwpx");
        String diffName = st(rev.get("revision"), "Contract v1 수정대조표", ".pdf");
        assertEquals("revised", read(at("Contract", "v1", "edit1", revName)));
        assertEquals("diff", read(at("Contract", "v1", "edit1", diffName)));
        assertEquals("original", read(at("Contract", "v1", origName)));
        assertEquals(Set.of("Contract"), names(storageRoot()));
        JsonNode after = docById(id);
        JsonNode f = latest(after).get("files").get(1);
        assertEquals("Contract v1.pdf", orig(after).get("fileName").asString());
        assertEquals(origName, orig(after).get("downloadName").asString());
        assertEquals("Contract v1.hwpx", f.get("fileName").asString());
        assertEquals(revName, f.get("downloadName").asString());
        assertEquals("Contract v1 수정대조표.pdf", f.get("revision").get("fileName").asString());
        assertEquals(diffName, f.get("revision").get("downloadName").asString());
        assertDownload(rev.get("id").asString(), "revised");
        assertDownload(fileId(latest(doc), 0), "original");

        r = put("/api/categories/" + cat, Map.of("name", "contract"));
        assertEquals(200, r.status(), r.text());
        assertEquals(Set.of("contract"), names(storageRoot()));
        assertEquals(Set.of(st(orig(doc), "contract v1", ".pdf"), "edit1"), names(at("contract", "v1")));
        assertEquals(Set.of(st(rev, "contract v1", ".hwpx"), st(rev.get("revision"), "contract v1 수정대조표", ".pdf")),
                names(at("contract", "v1", "edit1")));
        assertEquals("contract v1.pdf", orig(docById(id)).get("fileName").asString());
        assertAllDownloadable(docById(id));
    }

    // ---- 검토본 ----

    /** 검토 시작(박검토)까지 진행한 뒤 박검토가 memo.pdf로 거절한 문서. withRevision이면 수정본 1이 있다. */
    private JsonNode rejectedDoc(String category, boolean withRevision) {
        JsonNode doc = newDoc(category);
        String id = doc.get("id").asString();
        if (withRevision) assertEquals(200, addRevision(id, "김철수").status());
        assertEquals(200, post("/api/documents/" + id + "/work-done", Map.of()).status());
        assertEquals(200, post("/api/documents/" + id + "/review-start", Map.of("reviewers", List.of("박검토"))).status());
        Response r = reject(id, "박검토", Part.of("memo.pdf", "copy-body"));
        assertEquals(200, r.status(), r.text());
        return r.json();
    }

    private static JsonNode reviewFile(JsonNode doc) {
        return latest(doc).get("reviews").get(0).get("reviewFile");
    }

    private void assertReviewCopy(JsonNode doc, String title, String label, String... folder) {
        JsonNode rf = reviewFile(doc);
        String stored = title + " " + label + " 검토본_" + day(rf) + "_박검토.pdf";
        assertEquals(title + " " + label + " 검토본.pdf", rf.get("fileName").asString());
        assertEquals(stored, rf.get("downloadName").asString());
        Path p = at(folder).resolve(stored);
        assertTrue(Files.exists(p), p.toString());
        assertDownload(rf.get("id").asString(), "copy-body");
    }

    @Test
    void renamingCategoryRenamesReviewCopy() {
        JsonNode doc = rejectedDoc("재무", true);
        String cat = doc.get("categoryId").asString();
        assertEquals(200, put("/api/categories/" + cat, Map.of("name", "회계")).status());
        assertReviewCopy(docById(doc.get("id").asString()), "회계", "v1", "회계", "v1", "edit1");
    }

    @Test
    void renamingWorkingVersionRenamesReviewCopy() {
        JsonNode doc = rejectedDoc("재무", true);
        String id = doc.get("id").asString();
        Response r = patch("/api/documents/" + id + "/versions/" + latest(doc).get("id").asString(), Map.of("name", "초안"));
        assertEquals(200, r.status(), r.text());
        assertReviewCopy(r.json(), "재무", "초안", "재무", "초안", "edit1");
    }

    @Test
    void assigningCategoryRenamesReviewCopy() {
        JsonNode doc = rejectedDoc("재무", true);
        String id = doc.get("id").asString();
        assertEquals(204, delete("/api/categories/" + doc.get("categoryId").asString()).status());
        Response r = post("/api/documents/" + id + "/category",
                Map.of("newCategory", Map.of("name", "감사")));
        assertEquals(200, r.status(), r.text());
        assertReviewCopy(r.json(), "감사", "v1", "감사", "v1", "edit1");
    }

    @Test
    void deletingLatestRevisionDeletesReviewCopyKeepsRound() {
        JsonNode doc = rejectedDoc("재무", true);
        String id = doc.get("id").asString();
        Response r = delete("/api/documents/" + id + "/revisions/" + fileId(latest(doc), 1));
        assertEquals(200, r.status(), r.text());
        JsonNode reviews = latest(r.json()).get("reviews");
        assertEquals(1, reviews.size());
        assertFalse(reviews.get(0).has("reviewFile"));
        assertEquals("reject", latest(r.json()).get("review").get("result").asString());
        assertEquals(1, storedFileRows());
        assertEquals(0, jdbc.sql("SELECT count(*) FROM stored_files WHERE file_name LIKE '%검토본%'").query(Long.class).single());
        assertFalse(Files.exists(at("재무", "v1", "edit1")));
    }

    @Test
    void deletingVersionRemovesRoundsAndReviewCopies() {
        JsonNode doc = rejectedDoc("재무", false);
        String id = doc.get("id").asString();
        assertEquals(204, delete("/api/documents/" + id + "/versions/" + latest(doc).get("id").asString()).status());
        assertEquals(0, jdbc.sql("SELECT count(*) FROM version_review_rounds").query(Long.class).single());
        assertEquals(0, jdbc.sql("SELECT count(*) FROM review_round_reviewers").query(Long.class).single());
        assertEquals(0, storedFileRows());
        assertFalse(Files.exists(at("재무", "v1")));
    }
}

/** 저장 루트 설정 값(실행 인자와 같은 속성)이 저장소 루트가 된다. */
@ActiveProfiles("test")
@SpringBootTest(properties = "fileManager.storage-root=build/test-storage-override")
class StorageRootOverrideTest {
    @Autowired LocalFileStorage storage;

    @Test
    void rootFollowsConfiguration() {
        assertEquals(Path.of("build/test-storage-override").toAbsolutePath().normalize(), storage.root());
        assertTrue(Files.isDirectory(storage.root()));
    }
}
