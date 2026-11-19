package com.fileManager.category;

import com.fileManager.ApiTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.*;

class CategoryApiTest extends ApiTestSupport {
    private String create(String name) {
        Response r = post("/api/categories", Map.of("name", name));
        assertEquals(201, r.status(), r.text());
        return r.json().get("id").asString();
    }

    @Test
    void createTrimsAndAllowsDuplicateNames() {
        Response r = post("/api/categories", Map.of("name", "  계약서 "));
        assertEquals(201, r.status());
        assertEquals("계약서", r.json().get("name").asString());
        assertFalse(r.json().has("color"));
        assertTrue(r.json().get("createdAt").asString().matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{3}Z"));
        create("계약서");
        assertEquals(2, get("/api/categories").json().size());
    }

    @Test
    void createRejectsEmptyNameAndBrokenInput() {
        assertError(post("/api/categories", Map.of("name", " \u00A0 ")), 400, "INVALID_INPUT", "이름을 입력하세요.");
        assertError(post("/api/categories", Map.of("other", "a")), 400, "INVALID_INPUT", "요청 형식이 올바르지 않습니다.");
        assertError(post("/api/categories", "{broken"), 400, "INVALID_INPUT", "요청 형식이 올바르지 않습니다.");
    }

    @Test
    void unknownColorKeyIsIgnored() {
        Response r = post("/api/categories", Map.of("name", "x", "color", "#123"));
        assertEquals(201, r.status(), r.text());
        assertFalse(r.json().has("color"));
        r = put("/api/categories/" + r.json().get("id").asString(), Map.of("name", "y", "color", "#456"));
        assertEquals(200, r.status(), r.text());
        assertEquals("y", r.json().get("name").asString());
        assertFalse(r.json().has("color"));
    }

    @Test
    void listIsOrderedByCreation() {
        create("가");
        create("나");
        create("다");
        var list = get("/api/categories").json();
        assertEquals("가", list.get(0).get("name").asString());
        assertEquals("다", list.get(2).get("name").asString());
    }

    @Test
    void updateChangesNameAndSyncsDocumentTitle() {
        String id = create("옛이름");
        jdbc.sql("INSERT INTO documents (id, category_id, title, dir_name, created_at, updated_at) VALUES (:id, :c, '옛이름', '옛이름', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))").param("id", newId()).param("c", id).update();
        Response r = put("/api/categories/" + id, Map.of("name", " 새이름 "));
        assertEquals(200, r.status(), r.text());
        assertEquals("새이름", r.json().get("name").asString());
        assertFalse(r.json().has("color"));
        assertEquals("새이름", jdbc.sql("SELECT title FROM documents").query(String.class).single());
    }

    @Test
    void updateRejections() {
        String id = create("a");
        Map<String, String> ok = Map.of("name", "b");
        assertError(put("/api/categories/" + java.util.UUID.randomUUID(), ok), 404, "NOT_FOUND", "카테고리를 찾을 수 없습니다.");
        assertError(put("/api/categories/not-a-uuid", ok), 404, "NOT_FOUND", "카테고리를 찾을 수 없습니다.");
        assertError(put("/api/categories/" + id, Map.of("name", " ")), 400, "INVALID_INPUT", "이름을 입력하세요.");
    }

    @Test
    void deleteIsIdempotentAndCascadesToResources() {
        String id = create("지울것");
        jdbc.sql("INSERT INTO documents (id, category_id, title, dir_name, created_at, updated_at) VALUES (:id, :c, '지울것', '지울것', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))").param("id", newId()).param("c", id).update();
        Response res = multipart(HttpMethod.POST, "/api/categories/" + id + "/resources",
                Map.of("kind", "template", "title", "양식"), Map.of("file", Part.of("a.txt", "hello")));
        assertEquals(201, res.status(), res.text());
        String resId = res.json().get("id").asString();
        assertEquals(200, get("/api/files/" + resId).status());

        assertEquals(204, delete("/api/categories/" + id).status());
        assertEquals(0, get("/api/categories").json().size());
        assertEquals(0, get("/api/resources").json().size());
        assertEquals(0, jdbc.sql("SELECT count(*) FROM stored_files").query(Integer.class).single());
        assertError(get("/api/files/" + resId), 404, "NOT_FOUND", "저장된 파일을 찾을 수 없습니다.");
        // 문서는 남고 미분류가 된다, 제목은 그대로
        assertEquals(1, jdbc.sql("SELECT count(*) FROM documents WHERE category_id IS NULL").query(Integer.class).single());
        assertEquals("지울것", jdbc.sql("SELECT title FROM documents").query(String.class).single());

        assertEquals(204, delete("/api/categories/" + id).status());
        assertEquals(204, delete("/api/categories/not-a-uuid").status());
    }
}
