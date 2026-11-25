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
        // when: 앞뒤 공백이 있는 이름으로 만든다
        Response r = post("/api/categories", Map.of("name", "  계약서 "));

        // then: 이름을 다듬어 저장한다
        assertEquals(201, r.status());
        assertEquals("계약서", r.json().get("name").asString());
        assertFalse(r.json().has("color"));
        assertTrue(r.json().get("createdAt").asString().matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{3}Z"));

        // when: 같은 이름으로 하나 더 만든다
        create("계약서");

        // then: 같은 이름도 받는다
        assertEquals(2, get("/api/categories").json().size());
    }

    @Test
    void createRejectsEmptyNameAndBrokenInput() {
        // when & then
        assertError(post("/api/categories", Map.of("name", " \u00A0 ")), 400, "INVALID_INPUT", "이름을 입력하세요.");
        assertError(post("/api/categories", Map.of("other", "a")), 400, "INVALID_INPUT", "요청 형식이 올바르지 않습니다.");
        assertError(post("/api/categories", "{broken"), 400, "INVALID_INPUT", "요청 형식이 올바르지 않습니다.");
    }

    @Test
    void unknownColorKeyIsIgnored() {
        // when: color를 넣어 만든다
        Response r = post("/api/categories", Map.of("name", "x", "color", "#123"));

        // then: color는 무시된다
        assertEquals(201, r.status(), r.text());
        assertFalse(r.json().has("color"));

        // when: color를 넣어 이름을 바꾼다
        r = put("/api/categories/" + r.json().get("id").asString(), Map.of("name", "y", "color", "#456"));

        // then: 이름만 바뀌고 color는 무시된다
        assertEquals(200, r.status(), r.text());
        assertEquals("y", r.json().get("name").asString());
        assertFalse(r.json().has("color"));
    }

    @Test
    void listIsOrderedByCreation() {
        // given
        create("가");
        create("나");
        create("다");

        // when
        var list = get("/api/categories").json();

        // then
        assertEquals("가", list.get(0).get("name").asString());
        assertEquals("다", list.get(2).get("name").asString());
    }

    @Test
    void updateChangesNameAndSyncsDocumentTitle() {
        // given
        String id = create("옛이름");
        jdbc.sql("INSERT INTO documents (id, category_id, title, dir_name, created_at, updated_at) VALUES (:id, :c, '옛이름', '옛이름', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))").param("id", newId()).param("c", id).update();

        // when
        Response r = put("/api/categories/" + id, Map.of("name", " 새이름 "));

        // then
        assertEquals(200, r.status(), r.text());
        assertEquals("새이름", r.json().get("name").asString());
        assertFalse(r.json().has("color"));
        assertEquals("새이름", jdbc.sql("SELECT title FROM documents").query(String.class).single());
    }

    @Test
    void updateRejections() {
        // given
        String id = create("a");
        Map<String, String> ok = Map.of("name", "b");

        // when & then
        assertError(put("/api/categories/" + java.util.UUID.randomUUID(), ok), 404, "NOT_FOUND", "카테고리를 찾을 수 없습니다.");
        assertError(put("/api/categories/not-a-uuid", ok), 404, "NOT_FOUND", "카테고리를 찾을 수 없습니다.");
        assertError(put("/api/categories/" + id, Map.of("name", " ")), 400, "INVALID_INPUT", "이름을 입력하세요.");
    }

    @Test
    void hiddenDefaultsFalseAndToggles() {
        // when: 만든다
        String id = create("숨길것");

        // then: 처음에는 숨김이 아니다
        assertFalse(get("/api/categories").json().get(0).get("hidden").asBoolean());

        // when: 숨긴다
        Response r = put("/api/categories/" + id + "/hidden", Map.of("hidden", true));

        // then: 숨김이 저장된다
        assertEquals(200, r.status(), r.text());
        assertTrue(r.json().get("hidden").asBoolean());
        assertEquals("숨길것", r.json().get("name").asString());
        assertTrue(get("/api/categories").json().get(0).get("hidden").asBoolean());

        // when: 숨김을 푼다
        r = put("/api/categories/" + id + "/hidden", Map.of("hidden", false));

        // then: 숨김이 풀린다
        assertEquals(200, r.status(), r.text());
        assertFalse(r.json().get("hidden").asBoolean());
    }

    @Test
    void renameKeepsHidden() {
        // given
        String id = create("a");
        put("/api/categories/" + id + "/hidden", Map.of("hidden", true));

        // when
        Response r = put("/api/categories/" + id, Map.of("name", "b", "hidden", false));

        // then
        assertEquals(200, r.status(), r.text());
        assertTrue(r.json().get("hidden").asBoolean());
    }

    @Test
    void hiddenRejections() {
        // given
        String id = create("a");
        Map<String, Boolean> ok = Map.of("hidden", true);

        // when & then
        assertError(put("/api/categories/" + java.util.UUID.randomUUID() + "/hidden", ok), 404, "NOT_FOUND", "카테고리를 찾을 수 없습니다.");
        assertError(put("/api/categories/not-a-uuid/hidden", ok), 404, "NOT_FOUND", "카테고리를 찾을 수 없습니다.");
        String fmt = "요청 형식이 올바르지 않습니다.";
        assertError(put("/api/categories/" + id + "/hidden", Map.of("hidden", "true")), 400, "INVALID_INPUT", fmt);
        assertError(put("/api/categories/" + id + "/hidden", Map.of("hidden", 1)), 400, "INVALID_INPUT", fmt);
        assertError(put("/api/categories/" + id + "/hidden", Map.of("other", true)), 400, "INVALID_INPUT", fmt);
        assertError(put("/api/categories/" + id + "/hidden", "{\"hidden\": null}"), 400, "INVALID_INPUT", fmt);
        assertError(put("/api/categories/" + id + "/hidden", "[true]"), 400, "INVALID_INPUT", fmt);
        assertError(put("/api/categories/" + id + "/hidden", "{broken"), 400, "INVALID_INPUT", fmt);
        assertFalse(get("/api/categories").json().get(0).get("hidden").asBoolean());
    }

    @Test
    void deleteIsIdempotentAndCascadesToResources() {
        // given
        String id = create("지울것");
        jdbc.sql("INSERT INTO documents (id, category_id, title, dir_name, created_at, updated_at) VALUES (:id, :c, '지울것', '지울것', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))").param("id", newId()).param("c", id).update();

        // when: 양식을 올린다
        Response res = multipart(HttpMethod.POST, "/api/categories/" + id + "/resources",
                Map.of("kind", "template", "title", "양식"), Map.of("file", Part.of("a.txt", "hello")));

        // then: 양식 파일을 받을 수 있다
        assertEquals(201, res.status(), res.text());
        String resId = res.json().get("id").asString();
        assertEquals(200, get("/api/files/" + resId).status());

        // when & then: 카테고리를 지우면 양식·파일이 지워지고 문서는 미분류로 남는다
        assertEquals(204, delete("/api/categories/" + id).status());
        assertEquals(0, get("/api/categories").json().size());
        assertEquals(0, get("/api/resources").json().size());
        assertEquals(0, jdbc.sql("SELECT count(*) FROM stored_files").query(Integer.class).single());
        assertError(get("/api/files/" + resId), 404, "NOT_FOUND", "저장된 파일을 찾을 수 없습니다.");
        // 문서는 남고 미분류가 된다, 제목은 그대로
        assertEquals(1, jdbc.sql("SELECT count(*) FROM documents WHERE category_id IS NULL").query(Integer.class).single());
        assertEquals("지울것", jdbc.sql("SELECT title FROM documents").query(String.class).single());

        // when & then: 다시 지우거나 잘못된 id를 지워도 204다
        assertEquals(204, delete("/api/categories/" + id).status());
        assertEquals(204, delete("/api/categories/not-a-uuid").status());
    }
}
