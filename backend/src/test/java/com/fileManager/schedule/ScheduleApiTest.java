package com.fileManager.schedule;

import com.fileManager.ApiTestSupport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;

/** 일정 API(docs/api 8.23~8.26) 통합 테스트. */
class ScheduleApiTest extends ApiTestSupport {
    private static final String FORMAT = "요청 형식이 올바르지 않습니다.";
    private static final String DOC_404 = "문서를 찾을 수 없습니다.";
    private static final String NOT_FOUND = "발송 문서를 찾을 수 없습니다.";
    private static final String START_EMPTY = "시작일을 입력하세요.";
    private static final String PLANNED_EMPTY = "발송 예정일을 입력하세요.";
    private static final String DATE_FORMAT = "날짜 형식이 올바르지 않습니다.";
    private static final String PERIOD = "발송 예정일은 시작일과 같거나 뒤여야 합니다.";
    private static final String MEMO_TOO_LONG = "메모는 20자까지 쓸 수 있습니다.";
    private static final String UUID0 = "00000000-0000-0000-0000-000000000000";

    /** null 값을 넣을 수 있는 본문(키 순서 유지). 인자는 키, 값 쌍. */
    private static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private Response create(String docId, String start, String planned, String memo) {
        Map<String, Object> b = body("documentId", docId, "startDate", start, "plannedSendDate", planned);
        if (memo != null) b.put("memo", memo);
        return post("/api/schedules", b);
    }

    private JsonNode createOk(String docId, String start, String planned, String memo) {
        Response r = create(docId, start, planned, memo);
        assertEquals(201, r.status(), r.text());
        return r.json();
    }

    private JsonNode list() {
        Response r = get("/api/schedules");
        assertEquals(200, r.status(), r.text());
        return r.json();
    }

    private long scheduleRows() {
        return jdbc.sql("SELECT count(*) FROM schedules").query(Long.class).single();
    }

    private static String id(JsonNode j) { return j.get("id").asString(); }

    // 1
    @Test
    void createOnCategoryDocumentReturnsAllFieldsAndAllowsMany() {
        JsonNode doc = newDoc("계약");
        String docId = id(doc);
        String catId = doc.get("categoryId").asString();

        JsonNode s = createOk(docId, "2026-11-01", "2026-11-20", "초안");
        assertTrue(s.get("id").asString().matches("[0-9a-f-]{36}"));
        assertEquals(docId, s.get("documentId").asString());
        assertEquals("계약", s.get("documentTitle").asString());
        assertEquals(catId, s.get("categoryId").asString());
        assertEquals("working", s.get("stage").asString());
        assertEquals("2026-11-01", s.get("startDate").asString());
        assertEquals("2026-11-20", s.get("plannedSendDate").asString());
        assertEquals("초안", s.get("memo").asString());
        assertEquals(s.get("createdAt").asString(), s.get("updatedAt").asString());
        assertTrue(s.get("createdAt").asString().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"));

        JsonNode s2 = createOk(docId, "2026-12-01", "2026-12-10", null);
        assertNotEquals(id(s), id(s2));
        assertEquals(docId, s2.get("documentId").asString());
        assertFalse(s2.has("memo"));
        assertEquals(2, list().size());
        assertEquals(2, scheduleRows());
    }

    // 2, 5
    @Test
    void uncategorizedDocumentKeepsAndAcceptsSchedules() {
        JsonNode doc = newDoc("재무");
        String docId = id(doc);
        JsonNode before = createOk(docId, "2026-11-01", "2026-11-05", null);

        assertEquals(204, delete("/api/categories/" + doc.get("categoryId").asString()).status());

        JsonNode all = list();
        assertEquals(1, all.size());
        assertEquals(id(before), id(all.get(0)));
        assertTrue(all.get(0).has("categoryId"));
        assertTrue(all.get(0).get("categoryId").isNull());
        assertEquals("재무", all.get(0).get("documentTitle").asString());

        JsonNode s = createOk(docId, "2026-11-10", "2026-11-12", null);
        assertTrue(s.has("categoryId"));
        assertTrue(s.get("categoryId").isNull());
        assertEquals("재무", s.get("documentTitle").asString());
        assertEquals(2, list().size());
    }

    // 3
    @Test
    void listIsEmptyThenSortedByStartPlannedCreated() {
        assertEquals(0, list().size());
        String a = id(newDoc("가"));
        String b = id(newDoc("나"));
        String s1 = id(createOk(a, "2026-11-10", "2026-11-20", null));
        String s2 = id(createOk(b, "2026-11-01", "2026-11-30", null));
        String s3 = id(createOk(a, "2026-11-10", "2026-11-15", null));
        String s4 = id(createOk(b, "2026-11-10", "2026-11-20", null)); // s1과 날짜가 같고 뒤에 만듦

        JsonNode all = list();
        assertEquals(List.of(s2, s3, s1, s4), List.of(id(all.get(0)), id(all.get(1)), id(all.get(2)), id(all.get(3))));
    }

    // 4
    @Test
    void renamingCategoryChangesDocumentTitle() {
        JsonNode doc = newDoc("옛이름");
        createOk(id(doc), "2026-11-01", "2026-11-02", null);
        Response r = put("/api/categories/" + doc.get("categoryId").asString(), Map.of("name", "새이름"));
        assertEquals(200, r.status(), r.text());
        assertEquals("새이름", list().get(0).get("documentTitle").asString());
    }

    // 6
    @Test
    void deletingOnlyVersionDeletesDocumentSchedules() {
        JsonNode doc = newDoc("삭제");
        JsonNode other = newDoc("남김");
        createOk(id(doc), "2026-11-01", "2026-11-02", null);
        createOk(id(doc), "2026-11-03", "2026-11-04", null);
        String kept = id(createOk(id(other), "2026-11-05", "2026-11-06", null));

        Response r = delete("/api/documents/" + id(doc) + "/versions/" + latestVersionId(doc));
        assertEquals(204, r.status(), r.text());

        JsonNode all = list();
        assertEquals(1, all.size());
        assertEquals(kept, id(all.get(0)));
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM schedules WHERE document_id = :d")
                .param("d", id(doc)).query(Long.class).single());
        assertEquals(1, scheduleRows());
    }

    // 7
    @Test
    void stageFollowsLatestVersion() {
        JsonNode doc = newDoc("단계");
        createOk(id(doc), "2026-11-01", "2026-11-02", null);
        Response r = post("/api/documents/" + id(doc) + "/work-done", Map.of());
        assertEquals(200, r.status(), r.text());
        assertEquals("queued", list().get(0).get("stage").asString());
    }

    // 8
    @Test
    void sameDayAndMemoBoundaries() {
        String docId = id(newDoc("경계"));
        JsonNode same = createOk(docId, "2026-11-23", "2026-11-23", null);
        assertEquals("2026-11-23", same.get("startDate").asString());
        assertEquals("2026-11-23", same.get("plannedSendDate").asString());

        String twenty = "가".repeat(20);
        assertEquals(twenty, createOk(docId, "2026-11-01", "2026-11-02", twenty).get("memo").asString());
        assertError(create(docId, "2026-11-01", "2026-11-02", "가".repeat(21)), 400, "INVALID_INPUT", MEMO_TOO_LONG);
        // 앞뒤 공백은 지운 뒤 센다
        assertEquals(twenty, createOk(docId, "2026-11-01", "2026-11-02", "  " + twenty + " ").get("memo").asString());

        JsonNode blank = createOk(docId, "2026-11-01", "2026-11-02", "   ");
        assertFalse(blank.has("memo"));
        assertEquals("메모", createOk(docId, "2026-11-01", "2026-11-02", "  메모 ").get("memo").asString());
        // 공백만 있는 메모는 DB에도 NULL로 남는다(행은 있다)
        assertEquals(1L, jdbc.sql("SELECT count(*) FROM schedules WHERE id = :i AND memo IS NULL")
                .param("i", id(blank)).query(Long.class).single());
    }

    // 9
    @Test
    void createRejectionsInOrder() {
        String docId = id(newDoc("검사"));
        assertError(post("/api/schedules", body("documentId", docId, "plannedSendDate", "2026-11-02")),
                400, "INVALID_INPUT", START_EMPTY);
        assertError(create(docId, null, "2026-11-02", null), 400, "INVALID_INPUT", START_EMPTY);
        assertError(create(docId, "", "2026-11-02", null), 400, "INVALID_INPUT", START_EMPTY);
        assertError(create(docId, "", "", null), 400, "INVALID_INPUT", START_EMPTY);
        assertError(post("/api/schedules", body("documentId", docId, "startDate", "2026-11-01")),
                400, "INVALID_INPUT", PLANNED_EMPTY);
        assertError(create(docId, "2026-11-01", null, null), 400, "INVALID_INPUT", PLANNED_EMPTY);
        assertError(create(docId, "bad", "", null), 400, "INVALID_INPUT", PLANNED_EMPTY);
        for (String bad : List.of("2026-02-30", "2026-13-01", "2026-1-01", " 2026-11-23", "2026-11-23 ", "   ",
                "2026/11/23", "20261123")) {
            assertError(create(docId, bad, "2026-12-31", null), 400, "INVALID_INPUT", DATE_FORMAT);
            assertError(create(docId, "2026-01-01", bad, null), 400, "INVALID_INPUT", DATE_FORMAT);
        }
        assertError(create(docId, "2026-11-02", "2026-11-01", null), 400, "INVALID_INPUT", PERIOD);
        // 형식 오류가 기간보다 먼저, 기간이 메모보다 먼저
        assertError(create(docId, "2026-11-02", "2026-02-30", null), 400, "INVALID_INPUT", DATE_FORMAT);
        assertError(create(docId, "2026-11-02", "2026-11-01", "가".repeat(21)), 400, "INVALID_INPUT", PERIOD);
        // 날짜가 틀리면 문서 확인(404)보다 먼저
        assertError(create(UUID0, "2026-02-30", "2026-11-01", null), 400, "INVALID_INPUT", DATE_FORMAT);
        assertError(create("abc", "", "2026-11-01", null), 400, "INVALID_INPUT", START_EMPTY);
        // 날짜가 맞으면 문서 없음
        assertError(create(UUID0, "2026-11-01", "2026-11-02", null), 404, "NOT_FOUND", DOC_404);
        assertError(create("abc", "2026-11-01", "2026-11-02", null), 404, "NOT_FOUND", DOC_404);
        // 형식(2.2)은 가장 먼저
        assertError(post("/api/schedules", body("startDate", "", "plannedSendDate", "2026-11-02")),
                400, "INVALID_INPUT", FORMAT);
        assertError(post("/api/schedules", body("documentId", null, "startDate", "2026-11-01", "plannedSendDate", "2026-11-02")),
                400, "INVALID_INPUT", FORMAT);
        assertError(post("/api/schedules", body("documentId", 5, "startDate", "2026-11-01", "plannedSendDate", "2026-11-02")),
                400, "INVALID_INPUT", FORMAT);
        assertError(post("/api/schedules", body("documentId", docId, "startDate", 20261101, "plannedSendDate", "2026-11-02")),
                400, "INVALID_INPUT", FORMAT);
        assertError(post("/api/schedules", body("documentId", docId, "startDate", "2026-11-01", "plannedSendDate", "2026-11-02",
                "memo", 3)), 400, "INVALID_INPUT", FORMAT);
        assertError(post("/api/schedules", "[]"), 400, "INVALID_INPUT", FORMAT);
        assertError(post("/api/schedules", "{"), 400, "INVALID_INPUT", FORMAT);

        assertEquals(0, scheduleRows());
    }

    // 10
    @Test
    void updateChangesDatesAndMemoOnly() throws Exception {
        String docId = id(newDoc("수정"));
        String otherDoc = id(newDoc("다른"));
        JsonNode s = createOk(docId, "2026-11-01", "2026-11-05", "처음");
        Thread.sleep(5);

        Response r = put("/api/schedules/" + id(s),
                body("documentId", otherDoc, "startDate", "2026-12-01", "plannedSendDate", "2026-12-01", "memo", " 다음 "));
        assertEquals(200, r.status(), r.text());
        JsonNode u = r.json();
        assertEquals(id(s), id(u));
        assertEquals(docId, u.get("documentId").asString());
        assertEquals("수정", u.get("documentTitle").asString());
        assertEquals("2026-12-01", u.get("startDate").asString());
        assertEquals("2026-12-01", u.get("plannedSendDate").asString());
        assertEquals("다음", u.get("memo").asString());
        assertEquals(s.get("createdAt").asString(), u.get("createdAt").asString());
        assertNotEquals(s.get("updatedAt").asString(), u.get("updatedAt").asString());

        // memo 키를 빼면 메모가 지워진다(전체 교체)
        JsonNode cleared = put("/api/schedules/" + id(s), body("startDate", "2026-12-01", "plannedSendDate", "2026-12-02")).json();
        assertFalse(cleared.has("memo"));
        JsonNode listed = list().get(0);
        assertFalse(listed.has("memo"));
        assertEquals(docId, listed.get("documentId").asString());
        assertEquals("2026-12-02", listed.get("plannedSendDate").asString());

        // 검사(①~⑤)
        assertError(put("/api/schedules/" + id(s), body("plannedSendDate", "2026-12-02")), 400, "INVALID_INPUT", START_EMPTY);
        assertError(put("/api/schedules/" + id(s), body("startDate", "2026-12-01")), 400, "INVALID_INPUT", PLANNED_EMPTY);
        assertError(put("/api/schedules/" + id(s), body("startDate", "2026-02-30", "plannedSendDate", "2026-12-02")),
                400, "INVALID_INPUT", DATE_FORMAT);
        assertError(put("/api/schedules/" + id(s), body("startDate", "2026-12-03", "plannedSendDate", "2026-12-02")),
                400, "INVALID_INPUT", PERIOD);
        assertError(put("/api/schedules/" + id(s), body("startDate", "2026-12-01", "plannedSendDate", "2026-12-02",
                "memo", "가".repeat(21))), 400, "INVALID_INPUT", MEMO_TOO_LONG);
        assertError(put("/api/schedules/" + id(s), body("startDate", 1, "plannedSendDate", "2026-12-02")),
                400, "INVALID_INPUT", FORMAT);

        // 없는 일정은 입력 검사보다 404가 먼저
        Map<String, Object> ok = body("startDate", "2026-12-01", "plannedSendDate", "2026-12-02");
        assertError(put("/api/schedules/" + UUID0, ok), 404, "NOT_FOUND", NOT_FOUND);
        assertError(put("/api/schedules/abc", ok), 404, "NOT_FOUND", NOT_FOUND);
        assertError(put("/api/schedules/" + UUID0, body("startDate", "2026-02-30")), 404, "NOT_FOUND", NOT_FOUND);
        assertError(put("/api/schedules/abc", body()), 404, "NOT_FOUND", NOT_FOUND);

        // 거절된 요청은 아무것도 바꾸지 않았다
        JsonNode after = list().get(0);
        assertEquals("2026-12-01", after.get("startDate").asString());
        assertEquals("2026-12-02", after.get("plannedSendDate").asString());
    }

    // 11
    @Test
    void deleteIsIdempotentAndTouchesNothingElse() {
        JsonNode doc = newDoc("지움");
        String a = id(createOk(id(doc), "2026-11-01", "2026-11-02", null));
        String b = id(createOk(id(doc), "2026-11-03", "2026-11-04", null));
        String docBefore = get("/api/documents").text();

        assertEquals(204, delete("/api/schedules/" + a).status());
        JsonNode all = list();
        assertEquals(1, all.size());
        assertEquals(b, id(all.get(0)));

        assertEquals(204, delete("/api/schedules/" + a).status());
        assertEquals(204, delete("/api/schedules/" + UUID0).status());
        assertEquals(204, delete("/api/schedules/abc").status());
        assertEquals(1, list().size());
        assertEquals(docBefore, get("/api/documents").text());
    }
}
