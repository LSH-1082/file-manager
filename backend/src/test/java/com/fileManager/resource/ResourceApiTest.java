package com.fileManager.resource;

import com.fileManager.ApiTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.*;

class ResourceApiTest extends ApiTestSupport {
    private static final String FORMAT = "요청 형식이 올바르지 않습니다.";

    private String category() {
        return post("/api/categories", Map.of("name", "c")).json().get("id").asString();
    }

    private Response create(String cat, Map<String, String> f, Map<String, Part> files) {
        return multipart(HttpMethod.POST, "/api/categories/" + cat + "/resources", f, files);
    }

    private long storedFiles() {
        return jdbc.sql("SELECT count(*) FROM stored_files").query(Long.class).single();
    }

    @Test
    void createReturnsAllFields() {
        // given
        String cat = category();

        // when
        Response r = create(cat, Map.of("kind", "reference", "title", "참고"), Map.of("file", Part.of("a.txt", "hello")));

        // then
        assertEquals(201, r.status(), r.text());
        var j = r.json();
        assertEquals(cat, j.get("categoryId").asString());
        assertEquals("reference", j.get("kind").asString());
        assertEquals("참고", j.get("title").asString());
        assertEquals("", j.get("note").asString());
        assertEquals("a.txt", j.get("fileName").asString());
        assertEquals(5, j.get("fileSize").asInt());
        assertEquals("text/plain", j.get("mimeType").asString());
        assertEquals(j.get("createdAt").asString(), j.get("updatedAt").asString());
    }

    @Test
    void createRejections() throws Exception {
        // given
        String cat = category();
        Map<String, Part> file = Map.of("file", Part.of("a.txt", "x"));

        // when & then
        assertError(create(java.util.UUID.randomUUID().toString(), Map.of("kind", "etc", "title", "t"), file),
                404, "NOT_FOUND", "카테고리를 찾을 수 없습니다.");
        assertError(create(cat, Map.of("kind", "bogus", "title", "t"), file), 400, "INVALID_INPUT", FORMAT);
        assertError(create(cat, Map.of("kind", "etc", "title", "t"), Map.of()), 400, "INVALID_INPUT", FORMAT);
        assertError(create(cat, Map.of("title", "t"), file), 400, "INVALID_INPUT", FORMAT);
        assertError(multipartMulti(HttpMethod.POST, "/api/categories/" + cat + "/resources",
                Map.of("kind", "etc", "title", "t"),
                Map.of("file", List.of(Part.of("a", "1"), Part.of("b", "2")))), 400, "INVALID_INPUT", FORMAT);
        assertEquals(0, storedFiles());
        assertEquals(0, storedBodies());
    }

    private static final String EXE_BLOCKED = ".exe 파일은 올릴 수 없습니다.";

    @Test
    void createRejectsExe() {
        // given
        String cat = category();

        // when & then: .exe는 대소문자와 관계없이 거절되고 아무것도 남지 않는다
        for (String name : List.of("a.exe", "A.EXE")) {
            assertError(create(cat, Map.of("kind", "etc", "title", "t"), Map.of("file", Part.of(name, "x"))),
                    400, "INVALID_INPUT", EXE_BLOCKED);
        }
        assertEquals(0, storedFiles());
        assertEquals(0, storedBodies());
        assertEquals(0, get("/api/resources").json().size());

        // when & then: 카테고리·형식 검사가 .exe 검사보다 먼저다
        assertError(create(java.util.UUID.randomUUID().toString(), Map.of("kind", "etc", "title", "t"),
                Map.of("file", Part.of("a.exe", "x"))), 404, "NOT_FOUND", "카테고리를 찾을 수 없습니다.");
        assertError(create(cat, Map.of("kind", "bogus", "title", "t"), Map.of("file", Part.of("a.exe", "x"))),
                400, "INVALID_INPUT", FORMAT);
    }

    @Test
    void createAcceptsNonExe() {
        // given
        String cat = category();

        // when & then
        for (String name : List.of("a.txt", "a.docx", "a.pdf", "a")) {
            Response r = create(cat, Map.of("kind", "etc", "title", "t"), Map.of("file", Part.of(name, "x")));
            assertEquals(201, r.status(), r.text());
        }
    }

    @Test
    void updateRejectsExeAndKeepsFile() {
        // given
        String cat = category();
        String id = create(cat, Map.of("kind", "etc", "title", "t"), Map.of("file", Part.of("a.txt", "hello"))).json().get("id").asString();

        // when & then: .exe로 바꾸면 거절되고 파일·제목이 그대로다
        for (String name : List.of("a.exe", "A.EXE")) {
            assertError(multipart(HttpMethod.PUT, "/api/resources/" + id, Map.of("kind", "etc", "title", "새 제목"),
                    Map.of("file", Part.of(name, "x"))), 400, "INVALID_INPUT", EXE_BLOCKED);
        }
        var item = get("/api/resources").json().get(0);
        assertEquals("a.txt", item.get("fileName").asString());
        assertEquals("t", item.get("title").asString());
        assertEquals("hello", get("/api/files/" + id).text());
        assertEquals(1, storedFiles());
        assertEquals(1, storedBodies());

        // when: 다른 파일로 바꾼다
        Response ok = multipart(HttpMethod.PUT, "/api/resources/" + id, Map.of("kind", "etc", "title", "t"),
                Map.of("file", Part.of("b.docx", "new")));

        // then: 파일이 바뀐다
        assertEquals(200, ok.status(), ok.text());
        assertEquals("b.docx", ok.json().get("fileName").asString());
    }

    @Test
    void updateTextOnlyKeepsFile() {
        // given
        String cat = category();
        var created = create(cat, Map.of("kind", "etc", "title", "t"), Map.of("file", Part.of("a.txt", "hello"))).json();
        String id = created.get("id").asString();

        // when
        Response r = multipart(HttpMethod.PUT, "/api/resources/" + id,
                Map.of("kind", "template", "title", "새 제목", "note", "메모"), null);

        // then
        assertEquals(200, r.status(), r.text());
        assertEquals(id, r.json().get("id").asString());
        assertEquals("template", r.json().get("kind").asString());
        assertEquals("새 제목", r.json().get("title").asString());
        assertEquals("메모", r.json().get("note").asString());
        assertEquals("a.txt", r.json().get("fileName").asString());
        assertEquals("hello", get("/api/files/" + id).text());
    }

    @Test
    void updateWithFileReplacesBodyAndDeletesOld() throws Exception {
        // given
        String cat = category();
        String id = create(cat, Map.of("kind", "etc", "title", "t"), Map.of("file", Part.of("a.txt", "old"))).json().get("id").asString();

        // when
        Response r = multipart(HttpMethod.PUT, "/api/resources/" + id, Map.of("kind", "etc", "title", "t"),
                Map.of("file", Part.of("한글 b.txt", "newer")));

        // then
        assertEquals(200, r.status(), r.text());
        assertEquals("한글 b.txt", r.json().get("fileName").asString());
        assertEquals(5, r.json().get("fileSize").asInt());
        assertEquals("newer", get("/api/files/" + id).text());
        assertEquals(1, storedFiles());
        assertEquals(1, storedBodies());
    }

    @Test
    void updateRejections() {
        // given
        String cat = category();
        String id = create(cat, Map.of("kind", "etc", "title", "t"), Map.of("file", Part.of("a", "x"))).json().get("id").asString();

        // when & then
        assertError(multipart(HttpMethod.PUT, "/api/resources/" + java.util.UUID.randomUUID(), Map.of("kind", "etc", "title", "t"), null),
                404, "NOT_FOUND", "자료를 찾을 수 없습니다.");
        assertError(multipart(HttpMethod.PUT, "/api/resources/zzz", Map.of("kind", "etc", "title", "t"), null),
                404, "NOT_FOUND", "자료를 찾을 수 없습니다.");
        assertError(multipart(HttpMethod.PUT, "/api/resources/" + id, Map.of("kind", "bogus", "title", "t"), null),
                400, "INVALID_INPUT", FORMAT);
    }

    @Test
    void deleteRemovesRowAndBodyAndIsIdempotent() throws Exception {
        // given
        String cat = category();

        // when: 자료를 올린다
        String id = create(cat, Map.of("kind", "etc", "title", "t"), Map.of("file", Part.of("a", "x"))).json().get("id").asString();

        // then: 본문이 저장된다
        assertEquals(1, storedBodies());

        // when & then: 지우면 행과 본문이 지워진다
        assertEquals(204, delete("/api/resources/" + id).status());
        assertEquals(0, get("/api/resources").json().size());
        assertEquals(0, storedBodies());

        // when & then: 다시 지우거나 없는 id를 지워도 204다
        assertEquals(204, delete("/api/resources/" + id).status());
        assertEquals(204, delete("/api/resources/zzz").status());
    }

    @Test
    void listIsOrderedByCreation() {
        // given
        String cat = category();
        for (String t : List.of("첫째", "둘째", "셋째"))
            create(cat, Map.of("kind", "etc", "title", t), Map.of("file", Part.of("a", "x")));

        // when
        var l = get("/api/resources").json();

        // then
        assertEquals("첫째", l.get(0).get("title").asString());
        assertEquals("셋째", l.get(2).get("title").asString());
    }

    @Test
    void tooLargeFileIs413() throws Exception {
        // given
        String cat = category();

        // when
        Response r = create(cat, Map.of("kind", "etc", "title", "t"), Map.of("file", Part.of("big", "x".repeat(2000))));

        // then
        assertError(r, 413, "PAYLOAD_TOO_LARGE", "파일이 너무 큽니다. 100MB까지 올릴 수 있습니다.");
        assertEquals(0, storedFiles());
        assertEquals(0, storedBodies());
    }
}
