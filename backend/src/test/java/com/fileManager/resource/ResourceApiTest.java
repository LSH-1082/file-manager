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
        String cat = category();
        Response r = create(cat, Map.of("kind", "reference", "title", "참고"), Map.of("file", Part.of("a.txt", "hello")));
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
        String cat = category();
        Map<String, Part> file = Map.of("file", Part.of("a.txt", "x"));
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
        String cat = category();
        for (String name : List.of("a.exe", "A.EXE")) {
            assertError(create(cat, Map.of("kind", "etc", "title", "t"), Map.of("file", Part.of(name, "x"))),
                    400, "INVALID_INPUT", EXE_BLOCKED);
        }
        assertEquals(0, storedFiles());
        assertEquals(0, storedBodies());
        assertEquals(0, get("/api/resources").json().size());
        assertError(create(java.util.UUID.randomUUID().toString(), Map.of("kind", "etc", "title", "t"),
                Map.of("file", Part.of("a.exe", "x"))), 404, "NOT_FOUND", "카테고리를 찾을 수 없습니다.");
        assertError(create(cat, Map.of("kind", "bogus", "title", "t"), Map.of("file", Part.of("a.exe", "x"))),
                400, "INVALID_INPUT", FORMAT);
    }

    @Test
    void createAcceptsNonExe() {
        String cat = category();
        for (String name : List.of("a.txt", "a.docx", "a.pdf", "a")) {
            Response r = create(cat, Map.of("kind", "etc", "title", "t"), Map.of("file", Part.of(name, "x")));
            assertEquals(201, r.status(), r.text());
        }
    }

    @Test
    void updateRejectsExeAndKeepsFile() {
        String cat = category();
        String id = create(cat, Map.of("kind", "etc", "title", "t"), Map.of("file", Part.of("a.txt", "hello"))).json().get("id").asString();
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
        Response ok = multipart(HttpMethod.PUT, "/api/resources/" + id, Map.of("kind", "etc", "title", "t"),
                Map.of("file", Part.of("b.docx", "new")));
        assertEquals(200, ok.status(), ok.text());
        assertEquals("b.docx", ok.json().get("fileName").asString());
    }

    @Test
    void updateTextOnlyKeepsFile() {
        String cat = category();
        var created = create(cat, Map.of("kind", "etc", "title", "t"), Map.of("file", Part.of("a.txt", "hello"))).json();
        String id = created.get("id").asString();
        Response r = multipart(HttpMethod.PUT, "/api/resources/" + id,
                Map.of("kind", "template", "title", "새 제목", "note", "메모"), null);
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
        String cat = category();
        String id = create(cat, Map.of("kind", "etc", "title", "t"), Map.of("file", Part.of("a.txt", "old"))).json().get("id").asString();
        Response r = multipart(HttpMethod.PUT, "/api/resources/" + id, Map.of("kind", "etc", "title", "t"),
                Map.of("file", Part.of("한글 b.txt", "newer")));
        assertEquals(200, r.status(), r.text());
        assertEquals("한글 b.txt", r.json().get("fileName").asString());
        assertEquals(5, r.json().get("fileSize").asInt());
        assertEquals("newer", get("/api/files/" + id).text());
        assertEquals(1, storedFiles());
        assertEquals(1, storedBodies());
    }

    @Test
    void updateRejections() {
        String cat = category();
        String id = create(cat, Map.of("kind", "etc", "title", "t"), Map.of("file", Part.of("a", "x"))).json().get("id").asString();
        assertError(multipart(HttpMethod.PUT, "/api/resources/" + java.util.UUID.randomUUID(), Map.of("kind", "etc", "title", "t"), null),
                404, "NOT_FOUND", "자료를 찾을 수 없습니다.");
        assertError(multipart(HttpMethod.PUT, "/api/resources/zzz", Map.of("kind", "etc", "title", "t"), null),
                404, "NOT_FOUND", "자료를 찾을 수 없습니다.");
        assertError(multipart(HttpMethod.PUT, "/api/resources/" + id, Map.of("kind", "bogus", "title", "t"), null),
                400, "INVALID_INPUT", FORMAT);
    }

    @Test
    void deleteRemovesRowAndBodyAndIsIdempotent() throws Exception {
        String cat = category();
        String id = create(cat, Map.of("kind", "etc", "title", "t"), Map.of("file", Part.of("a", "x"))).json().get("id").asString();
        assertEquals(1, storedBodies());
        assertEquals(204, delete("/api/resources/" + id).status());
        assertEquals(0, get("/api/resources").json().size());
        assertEquals(0, storedBodies());
        assertEquals(204, delete("/api/resources/" + id).status());
        assertEquals(204, delete("/api/resources/zzz").status());
    }

    @Test
    void listIsOrderedByCreation() {
        String cat = category();
        for (String t : List.of("첫째", "둘째", "셋째"))
            create(cat, Map.of("kind", "etc", "title", t), Map.of("file", Part.of("a", "x")));
        var l = get("/api/resources").json();
        assertEquals("첫째", l.get(0).get("title").asString());
        assertEquals("셋째", l.get(2).get("title").asString());
    }

    @Test
    void tooLargeFileIs413() throws Exception {
        String cat = category();
        Response r = create(cat, Map.of("kind", "etc", "title", "t"), Map.of("file", Part.of("big", "x".repeat(2000))));
        assertError(r, 413, "PAYLOAD_TOO_LARGE", "파일이 너무 큽니다. 100MB까지 올릴 수 있습니다.");
        assertEquals(0, storedFiles());
        assertEquals(0, storedBodies());
    }
}
