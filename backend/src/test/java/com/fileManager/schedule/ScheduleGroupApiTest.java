package com.fileManager.schedule;

import com.fileManager.ApiTestSupport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;

/** 일정 API(docs/api 8.27~8.30)와 발송 문서의 소속(8.24·8.25의 groupId) 통합 테스트. */
class ScheduleGroupApiTest extends ApiTestSupport {
    private static final String PATH = "/api/schedule-groups";
    private static final String FORMAT = "요청 형식이 올바르지 않습니다.";
    private static final String DOC_404 = "문서를 찾을 수 없습니다.";
    private static final String GROUP_404 = "일정을 찾을 수 없습니다.";
    private static final String SCHEDULE_404 = "발송 문서를 찾을 수 없습니다.";
    private static final String NAME_EMPTY = "일정 이름을 입력하세요.";
    private static final String NAME_TOO_LONG = "일정 이름은 20자까지 쓸 수 있습니다.";
    private static final String START_EMPTY = "시작일을 입력하세요.";
    private static final String END_EMPTY = "종료일을 입력하세요.";
    private static final String DATE_FORMAT = "날짜 형식이 올바르지 않습니다.";
    private static final String PERIOD = "종료일은 시작일과 같거나 뒤여야 합니다.";
    private static final String UUID0 = "00000000-0000-0000-0000-000000000000";

    /** null 값을 넣을 수 있는 본문(키 순서 유지). 인자는 키, 값 쌍. */
    private static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static Map<String, Object> groupBody(Object name, Object start, Object end) {
        return body("name", name, "startDate", start, "endDate", end);
    }

    private JsonNode createGroup(String name, String start, String end) {
        Response r = post(PATH, groupBody(name, start, end));
        assertEquals(201, r.status(), r.text());
        return r.json();
    }

    private JsonNode createSchedule(String docId, String groupId, String start, String planned) {
        Map<String, Object> b = body("documentId", docId, "startDate", start, "plannedSendDate", planned);
        if (groupId != null) b.put("groupId", groupId);
        Response r = post("/api/schedules", b);
        assertEquals(201, r.status(), r.text());
        return r.json();
    }

    private JsonNode listOf(String path) {
        Response r = get(path);
        assertEquals(200, r.status(), r.text());
        return r.json();
    }

    private JsonNode groups() { return listOf(PATH); }

    private JsonNode schedules() { return listOf("/api/schedules"); }

    private JsonNode schedule(String scheduleId) {
        for (JsonNode s : schedules()) if (id(s).equals(scheduleId)) return s;
        return fail("발송 문서가 목록에 없음: " + scheduleId);
    }

    private static List<String> ids(JsonNode list) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : list) out.add(id(n));
        return out;
    }

    private long rows(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private static String id(JsonNode j) { return j.get("id").asString(); }

    private static int color(JsonNode g) { return g.get("colorNo").asInt(); }

    // ---- 일정 ----

    @Test
    void createReturnsAllFields() {
        // when
        JsonNode g = createGroup("1차 납품", "2026-11-23", "2026-12-11");

        // then
        assertTrue(id(g).matches("[0-9a-f-]{36}"));
        assertEquals("1차 납품", g.get("name").asString());
        assertEquals("2026-11-23", g.get("startDate").asString());
        assertEquals("2026-12-11", g.get("endDate").asString());
        assertTrue(g.get("colorNo").isInt());
        assertEquals(0, color(g));
        assertTrue(g.get("createdAt").asString().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"));
        assertEquals(g.get("createdAt").asString(), g.get("updatedAt").asString());
        assertEquals(7, g.size());
        assertEquals(g, groups().get(0));
    }

    @Test
    void listIsEmptyThenSortedByStartEndCreated() {
        // when & then: 처음 목록은 비어 있다
        assertEquals(0, groups().size());

        // when: 일정 4개를 만든다
        String g1 = id(createGroup("가", "2026-11-10", "2026-11-20"));
        String g2 = id(createGroup("나", "2026-11-01", "2026-11-30"));
        String g3 = id(createGroup("다", "2026-11-10", "2026-11-15"));
        String g4 = id(createGroup("라", "2026-11-10", "2026-11-20")); // g1과 기간이 같고 뒤에 만듦

        // then: 시작일·종료일·만든 순으로 정렬된다
        assertEquals(List.of(g2, g3, g1, g4), ids(groups()));
    }

    @Test
    void sameNameAndOverlapAndSameDayAndAnyYearAreAllowed() {
        // when
        createGroup("같음", "2026-11-01", "2026-11-30");
        createGroup("같음", "2026-11-01", "2026-11-30");
        createGroup("하루", "2026-11-23", "2026-11-23");
        createGroup("옛날", "1990-01-01", "2090-12-31");

        // then
        assertEquals(4, groups().size());
    }

    @Test
    void nameBoundaries() {
        // given
        String twenty = "가".repeat(20);

        // when & then: 20자까지 받는다
        assertEquals(twenty, createGroup(twenty, "2026-11-01", "2026-11-02").get("name").asString());
        // 앞뒤 공백은 지운 뒤 세고 저장한다
        assertEquals(twenty, createGroup("  " + twenty + " ", "2026-11-01", "2026-11-02").get("name").asString());
        assertEquals("납 품", createGroup("　납 품\t", "2026-11-01", "2026-11-02").get("name").asString());

        // when & then: 21자와 빈 이름은 거절한다
        assertError(post(PATH, groupBody("가".repeat(21), "2026-11-01", "2026-11-02")), 400, "INVALID_INPUT", NAME_TOO_LONG);
        assertError(post(PATH, body("startDate", "2026-11-01", "endDate", "2026-11-02")), 400, "INVALID_INPUT", NAME_EMPTY);
        for (String blank : new String[] {null, "", "   ", "　"}) {
            assertError(post(PATH, groupBody(blank, "2026-11-01", "2026-11-02")), 400, "INVALID_INPUT", NAME_EMPTY);
        }

        // then: 받은 3개만 저장됐다
        assertEquals(3, groups().size());
    }

    @Test
    void createRejectionsInOrder() {
        // when & then: 검사 순서대로 거절한다
        // ③ 시작일
        assertError(post(PATH, body("name", "가", "endDate", "2026-11-02")), 400, "INVALID_INPUT", START_EMPTY);
        assertError(post(PATH, groupBody("가", null, "2026-11-02")), 400, "INVALID_INPUT", START_EMPTY);
        assertError(post(PATH, groupBody("가", "", "")), 400, "INVALID_INPUT", START_EMPTY);
        // ④ 종료일(형식보다 먼저)
        assertError(post(PATH, body("name", "가", "startDate", "2026-11-01")), 400, "INVALID_INPUT", END_EMPTY);
        assertError(post(PATH, groupBody("가", "2026-11-01", null)), 400, "INVALID_INPUT", END_EMPTY);
        assertError(post(PATH, groupBody("가", "bad", "")), 400, "INVALID_INPUT", END_EMPTY);
        // ⑤ 날짜 형식(공백을 지우지 않는다)
        for (String bad : List.of("2026-02-30", "2026-13-01", "2026-1-01", " 2026-11-23", "2026-11-23 ", "   ",
                "2026/11/23", "20261123")) {
            assertError(post(PATH, groupBody("가", bad, "2026-12-31")), 400, "INVALID_INPUT", DATE_FORMAT);
            assertError(post(PATH, groupBody("가", "2026-01-01", bad)), 400, "INVALID_INPUT", DATE_FORMAT);
        }
        assertError(post(PATH, groupBody("가", "2026-11-02", "2026-02-30")), 400, "INVALID_INPUT", DATE_FORMAT);
        // ⑥ 기간
        assertError(post(PATH, groupBody("가", "2026-11-02", "2026-11-01")), 400, "INVALID_INPUT", PERIOD);
        // 이름이 날짜보다 먼저
        assertError(post(PATH, groupBody("", "", "")), 400, "INVALID_INPUT", NAME_EMPTY);
        assertError(post(PATH, groupBody("가".repeat(21), "2026-02-30", "")), 400, "INVALID_INPUT", NAME_TOO_LONG);
        assertError(post(PATH, groupBody("가".repeat(21), "2026-11-02", "2026-11-01")), 400, "INVALID_INPUT", NAME_TOO_LONG);
        // 형식(2.2)은 가장 먼저
        assertError(post(PATH, groupBody(3, "", "")), 400, "INVALID_INPUT", FORMAT);
        assertError(post(PATH, groupBody("", 20261101, "2026-11-02")), 400, "INVALID_INPUT", FORMAT);
        assertError(post(PATH, groupBody("", "", true)), 400, "INVALID_INPUT", FORMAT);
        assertError(post(PATH, groupBody(List.of("가"), "2026-11-01", "2026-11-02")), 400, "INVALID_INPUT", FORMAT);
        assertError(post(PATH, "[]"), 400, "INVALID_INPUT", FORMAT);
        assertError(post(PATH, "null"), 400, "INVALID_INPUT", FORMAT);
        assertError(post(PATH, "{"), 400, "INVALID_INPUT", FORMAT);

        // then: 아무것도 저장하지 않았다
        assertEquals(0, rows("schedule_groups"));
    }

    @Test
    void updateReplacesNameAndPeriodOnly() throws Exception {
        // given
        createGroup("먼저", "2026-10-01", "2026-10-31");
        JsonNode g = createGroup("수정 전", "2026-11-01", "2026-11-30");
        assertEquals(1, color(g));
        Thread.sleep(5);

        Map<String, Object> b = groupBody("  수정 후 ", "2026-12-01", "2026-12-01");
        b.put("colorNo", 4);
        b.put("id", UUID0);

        // when
        Response r = put(PATH + "/" + id(g), b);

        // then
        assertEquals(200, r.status(), r.text());
        JsonNode u = r.json();
        assertEquals(id(g), id(u));
        assertEquals("수정 후", u.get("name").asString());
        assertEquals("2026-12-01", u.get("startDate").asString());
        assertEquals("2026-12-01", u.get("endDate").asString());
        assertEquals(1, color(u));
        assertEquals(g.get("createdAt").asString(), u.get("createdAt").asString());
        assertNotEquals(g.get("updatedAt").asString(), u.get("updatedAt").asString());
        assertEquals(u, groups().get(1));

        // when & then: 전체 교체라 빠진 값은 검사에 걸린다
        String path = PATH + "/" + id(g);
        assertError(put(path, body("startDate", "2026-12-01", "endDate", "2026-12-02")), 400, "INVALID_INPUT", NAME_EMPTY);
        assertError(put(path, groupBody("가".repeat(21), "", "")), 400, "INVALID_INPUT", NAME_TOO_LONG);
        assertError(put(path, body("name", "가", "endDate", "2026-12-02")), 400, "INVALID_INPUT", START_EMPTY);
        assertError(put(path, body("name", "가", "startDate", "2026-12-02")), 400, "INVALID_INPUT", END_EMPTY);
        assertError(put(path, groupBody("가", "2026-02-30", "2026-12-02")), 400, "INVALID_INPUT", DATE_FORMAT);
        assertError(put(path, groupBody("가", "2026-12-03", "2026-12-02")), 400, "INVALID_INPUT", PERIOD);
        assertError(put(path, groupBody(1, "2026-12-03", "2026-12-04")), 400, "INVALID_INPUT", FORMAT);

        // then: 거절된 요청은 아무것도 바꾸지 않았다
        assertEquals(u, groups().get(1));
    }

    @Test
    void updateMissingGroupIs404BeforeInputButAfterFormat() {
        // when & then
        for (String missing : List.of(UUID0, "abc")) {
            String path = PATH + "/" + missing;
            assertError(put(path, groupBody("가", "2026-11-01", "2026-11-02")), 404, "NOT_FOUND", GROUP_404);
            assertError(put(path, groupBody("", "2026-02-30", "")), 404, "NOT_FOUND", GROUP_404);
            assertError(put(path, body()), 404, "NOT_FOUND", GROUP_404);
            assertError(put(path, groupBody(5, "2026-11-01", "2026-11-02")), 400, "INVALID_INPUT", FORMAT);
            assertError(put(path, "[]"), 400, "INVALID_INPUT", FORMAT);
        }
    }

    @Test
    void deleteIsIdempotentAndTouchesNothingElse() {
        // given
        JsonNode a = createGroup("가", "2026-11-01", "2026-11-02");
        JsonNode b = createGroup("나", "2026-11-03", "2026-11-04");

        // when: 하나를 지운다
        Response deleted = delete(PATH + "/" + id(a));

        // then: 나머지는 그대로다
        assertEquals(204, deleted.status());
        assertEquals(List.of(b), List.of(groups().get(0)));
        assertEquals(1, groups().size());

        // when & then: 다시 지우거나 없는 id를 지워도 204다
        assertEquals(204, delete(PATH + "/" + id(a)).status());
        assertEquals(204, delete(PATH + "/" + UUID0).status());
        assertEquals(204, delete(PATH + "/abc").status());

        // then: 남은 일정은 그대로다
        assertEquals(1, groups().size());

        // when & then: 지운 일정은 고칠 수 없다
        assertError(put(PATH + "/" + id(a), groupBody("가", "2026-11-01", "2026-11-02")), 404, "NOT_FOUND", GROUP_404);
    }

    // ---- 색 번호 ----

    @Test
    void colorsAreAssignedInTurnAndWrap() {
        // given
        List<Integer> colors = new ArrayList<>();

        // when
        for (int i = 0; i < 7; i++) colors.add(color(createGroup("일정" + i, "2026-11-01", "2026-11-30")));

        // then
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 0), colors);
    }

    @Test
    void deletingOrRejectingOrUpdatingDoesNotChangeColors() {
        // given
        JsonNode a = createGroup("가", "2026-11-01", "2026-11-02");
        JsonNode b = createGroup("나", "2026-11-01", "2026-11-02");
        JsonNode c = createGroup("다", "2026-11-01", "2026-11-02");

        // when & then: 일정을 지운다
        assertEquals(204, delete(PATH + "/" + id(b)).status());

        // when & then: 거절된 등록은 번호를 쓰지 않는다
        assertError(post(PATH, groupBody("", "2026-11-01", "2026-11-02")), 400, "INVALID_INPUT", NAME_EMPTY);
        assertError(post(PATH, groupBody("라", "2026-11-03", "2026-11-02")), 400, "INVALID_INPUT", PERIOD);
        assertError(post(PATH, groupBody(1, "2026-11-01", "2026-11-02")), 400, "INVALID_INPUT", FORMAT);

        // when & then: 일정을 고친다
        assertEquals(200, put(PATH + "/" + id(a), groupBody("가2", "2026-11-01", "2026-11-05")).status());

        // when & then: 본문의 colorNo는 값도 타입도 보지 않는다
        Map<String, Object> withColor = groupBody("라", "2026-11-01", "2026-11-02");
        withColor.put("colorNo", 0);
        assertEquals(3, color(post(PATH, withColor).json()));
        withColor.put("colorNo", "x");
        assertEquals(4, color(post(PATH, withColor).json()));

        // then: 지우거나 고친 일정의 색은 그대로다
        Map<String, Integer> byId = new LinkedHashMap<>();
        for (JsonNode g : groups()) byId.put(id(g), color(g));
        assertEquals(0, byId.get(id(a)));
        assertEquals(2, byId.get(id(c)));
        assertFalse(byId.containsKey(id(b)));
    }

    /** 설정 행 잠금으로 동시 등록이 같은 번호를 읽지 않는다. */
    @Test
    @Timeout(60)
    void concurrentCreatesGetDistinctColors() throws Exception {
        // given
        groups(); // 요청 도구(client)를 스레드들이 쓰기 전에 만들어 둔다
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Response>> pending = new ArrayList<>();

            // when
            for (int i = 0; i < 6; i++) {
                String name = "동시" + i;
                pending.add(pool.submit(() -> {
                    go.await();
                    return post(PATH, groupBody(name, "2026-11-01", "2026-11-30"));
                }));
            }
            go.countDown();

            // then
            List<Integer> colors = new ArrayList<>();
            for (Future<Response> f : pending) {
                Response r = f.get(30, TimeUnit.SECONDS);
                assertEquals(201, r.status(), r.text());
                colors.add(color(r.json()));
            }
            colors.sort(null);
            assertEquals(List.of(0, 1, 2, 3, 4, 5), colors);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- 발송 문서의 소속 ----

    @Test
    void scheduleKeepsGroupIdAndOmitsKeyWithoutGroup() {
        // given
        String docId = id(newDoc("소속"));
        String groupId = id(createGroup("납품", "2026-11-01", "2026-11-30"));

        // when: 일정을 지정해 등록한다
        JsonNode in = createSchedule(docId, groupId, "2026-11-01", "2026-11-05");

        // then
        assertEquals(groupId, in.get("groupId").asString());
        assertEquals(groupId, schedule(id(in)).get("groupId").asString());

        // when: 일정 없이 등록한다
        JsonNode none = createSchedule(docId, null, "2026-11-02", "2026-11-05");

        // then
        assertFalse(none.has("groupId"));

        // when: groupId를 null로 등록한다
        Response nullGroup = post("/api/schedules", body("documentId", docId, "groupId", null,
                "startDate", "2026-11-03", "plannedSendDate", "2026-11-05"));

        // then
        assertEquals(201, nullGroup.status(), nullGroup.text());
        assertFalse(nullGroup.json().has("groupId"));
        assertFalse(schedule(id(none)).has("groupId"));
        assertFalse(schedule(id(nullGroup.json())).has("groupId"));
    }

    @Test
    void createWithUnknownGroupIs404AfterInputAndDocument() {
        // given
        String docId = id(newDoc("검사"));
        String groupId = id(createGroup("납품", "2026-11-01", "2026-11-30"));

        // when & then: 없는 일정은 404다
        for (String missing : List.of(UUID0, "abc", "", " ", groupId + " ")) {
            assertError(post("/api/schedules", body("documentId", docId, "groupId", missing,
                    "startDate", "2026-11-01", "plannedSendDate", "2026-11-02")), 404, "NOT_FOUND", GROUP_404);
        }
        // 날짜·메모 → 문서 → 일정 순서
        assertError(post("/api/schedules", body("documentId", docId, "groupId", UUID0,
                "startDate", "2026-02-30", "plannedSendDate", "2026-11-02")), 400, "INVALID_INPUT", DATE_FORMAT);
        assertError(post("/api/schedules", body("documentId", docId, "groupId", UUID0, "startDate", "2026-11-01",
                "plannedSendDate", "2026-11-02", "memo", "가".repeat(21))),
                400, "INVALID_INPUT", "메모는 20자까지 쓸 수 있습니다.");
        assertError(post("/api/schedules", body("documentId", UUID0, "groupId", UUID0,
                "startDate", "2026-11-01", "plannedSendDate", "2026-11-02")), 404, "NOT_FOUND", DOC_404);
        // 형식(2.2)은 가장 먼저
        for (Object bad : List.of(5, true, List.of(groupId), Map.of("id", groupId))) {
            assertError(post("/api/schedules", body("documentId", docId, "groupId", bad,
                    "startDate", "", "plannedSendDate", "2026-11-02")), 400, "INVALID_INPUT", FORMAT);
        }

        // then: 아무것도 저장하지 않았다
        assertEquals(0, rows("schedules"));
    }

    @Test
    void datesOutsideGroupPeriodAreSavedEvenAfterShrinking() {
        // given
        String docId = id(newDoc("기간밖"));
        JsonNode g = createGroup("납품", "2026-11-10", "2026-11-20");

        // when
        JsonNode outside = createSchedule(docId, id(g), "2026-10-01", "2026-12-31");
        JsonNode inside = createSchedule(docId, id(g), "2026-11-12", "2026-11-18");

        // then
        assertEquals("2026-10-01", outside.get("startDate").asString());
        assertEquals("2026-12-31", outside.get("plannedSendDate").asString());

        // when: 일정 기간을 줄인다
        Response shrunk = put(PATH + "/" + id(g), groupBody("납품", "2026-11-15", "2026-11-15"));

        // then: 발송 문서는 그대로다
        assertEquals(200, shrunk.status());
        assertEquals(outside, schedule(id(outside)));
        assertEquals(inside, schedule(id(inside)));

        // when: 발송 문서를 기간 밖 날짜로 고친다. 수정도 기간과 비교하지 않는다
        Response r = put("/api/schedules/" + id(inside),
                body("groupId", id(g), "startDate", "2027-01-01", "plannedSendDate", "2027-01-02"));

        // then
        assertEquals(200, r.status(), r.text());
        assertEquals(id(g), r.json().get("groupId").asString());
    }

    @Test
    void sameDocumentCanBeInSeveralGroupsAndNone() {
        // given
        String docId = id(newDoc("여러"));
        String g1 = id(createGroup("일", "2026-11-01", "2026-11-30"));
        String g2 = id(createGroup("이", "2026-12-01", "2026-12-31"));

        // when
        createSchedule(docId, g1, "2026-11-01", "2026-11-02");
        createSchedule(docId, g2, "2026-11-02", "2026-11-03");
        createSchedule(docId, null, "2026-11-03", "2026-11-04");
        createSchedule(docId, g1, "2026-11-04", "2026-11-05");

        // then
        JsonNode all = schedules();
        assertEquals(4, all.size());
        assertEquals(g1, all.get(0).get("groupId").asString());
        assertEquals(g2, all.get(1).get("groupId").asString());
        assertFalse(all.get(2).has("groupId"));
        assertEquals(g1, all.get(3).get("groupId").asString());
    }

    @Test
    void updateReplacesGroupId() {
        // given
        String docId = id(newDoc("이동"));
        String g1 = id(createGroup("일", "2026-11-01", "2026-11-30"));
        String g2 = id(createGroup("이", "2026-12-01", "2026-12-31"));
        String s = id(createSchedule(docId, g1, "2026-11-01", "2026-11-02"));
        Map<String, Object> dates = body("startDate", "2026-11-01", "plannedSendDate", "2026-11-03");

        // when: 다른 일정으로 옮긴다. 날짜가 새 일정 기간 밖이어도 받는다(D-34)
        Map<String, Object> toG2 = new LinkedHashMap<>(dates);
        toG2.put("groupId", g2);
        Response r = put("/api/schedules/" + s, toG2);

        // then
        assertEquals(200, r.status(), r.text());
        assertEquals(g2, r.json().get("groupId").asString());
        assertEquals(g2, schedule(s).get("groupId").asString());

        // when & then: 전체 교체: groupId를 빼거나 null이면 일정과 관계없는 문서가 된다
        assertFalse(put("/api/schedules/" + s, dates).json().has("groupId"));
        assertFalse(schedule(s).has("groupId"));
        Map<String, Object> toG1 = new LinkedHashMap<>(dates);
        toG1.put("groupId", g1);
        assertEquals(g1, put("/api/schedules/" + s, toG1).json().get("groupId").asString());
        Map<String, Object> nul = new LinkedHashMap<>(dates);
        nul.put("groupId", null);
        assertFalse(put("/api/schedules/" + s, nul).json().has("groupId"));

        // when & then: 없는 일정·UUID 아님·빈 문자열은 404, 문자열이 아니면 형식 오류
        put("/api/schedules/" + s, toG1);
        for (Object bad : new Object[] {"", "abc", UUID0}) {
            Map<String, Object> b = new LinkedHashMap<>(dates);
            b.put("groupId", bad);
            assertError(put("/api/schedules/" + s, b), 404, "NOT_FOUND", GROUP_404);
        }
        for (Object bad : new Object[] {5, List.of(g2)}) {
            Map<String, Object> b = new LinkedHashMap<>(dates);
            b.put("groupId", bad);
            assertError(put("/api/schedules/" + s, b), 400, "INVALID_INPUT", FORMAT);
        }

        // then: 실패하면 소속은 그대로다
        assertEquals(g1, schedule(s).get("groupId").asString());

        // when & then: 발송 문서의 404가 일정 404보다 먼저다
        assertError(put("/api/schedules/" + UUID0, toG1), 404, "NOT_FOUND", SCHEDULE_404);
    }

    @Test
    void deletingGroupKeepsItsSchedulesWithoutGroupId() throws Exception {
        // given
        String docId = id(newDoc("남김"));
        String g1 = id(createGroup("일", "2026-11-01", "2026-11-30"));
        String g2 = id(createGroup("이", "2026-12-01", "2026-12-31"));
        JsonNode a = createSchedule(docId, g1, "2026-11-01", "2026-11-02");
        JsonNode b = createSchedule(docId, g2, "2026-12-01", "2026-12-02");
        JsonNode c = createSchedule(docId, null, "2026-12-03", "2026-12-04");
        Thread.sleep(5);

        // when: g1을 지운다
        Response deleted = delete(PATH + "/" + g1);

        // then: 발송 문서는 소속만 빠지고 남는다
        assertEquals(204, deleted.status());
        assertEquals(List.of(g2), ids(groups()));
        assertEquals(3, schedules().size());
        JsonNode kept = schedule(id(a));
        assertFalse(kept.has("groupId"));
        assertEquals(a.get("updatedAt").asString(), kept.get("updatedAt").asString());
        assertEquals(a.get("startDate").asString(), kept.get("startDate").asString());
        assertEquals(a.get("plannedSendDate").asString(), kept.get("plannedSendDate").asString());
        assertEquals(b, schedule(id(b)));
        assertEquals(c, schedule(id(c)));

        // when: 남은 발송 문서를 고친다
        Response r = put("/api/schedules/" + id(a), body("startDate", "2026-11-01", "plannedSendDate", "2026-11-05"));

        // then: 지워진 일정으로 되돌아가지 않는다
        assertEquals(200, r.status(), r.text());
        assertFalse(r.json().has("groupId"));
    }

    @Test
    void deletingScheduleKeepsGroup() {
        // given
        String docId = id(newDoc("막대삭제"));
        JsonNode g = createGroup("일", "2026-11-01", "2026-11-30");
        String s = id(createSchedule(docId, id(g), "2026-11-01", "2026-11-02"));

        // when
        Response deleted = delete("/api/schedules/" + s);

        // then
        assertEquals(204, deleted.status());
        assertEquals(0, schedules().size());
        assertEquals(g, groups().get(0));
    }

    @Test
    void deletingOnlyVersionDeletesMemberSchedulesButKeepsGroup() {
        // given
        JsonNode doc = newDoc("문서삭제");
        JsonNode other = newDoc("다른");
        JsonNode g = createGroup("일", "2026-11-01", "2026-11-30");
        createSchedule(id(doc), id(g), "2026-11-01", "2026-11-02");
        String kept = id(createSchedule(id(other), id(g), "2026-11-03", "2026-11-04"));

        // when
        Response deleted = delete("/api/documents/" + id(doc) + "/versions/" + latestVersionId(doc));

        // then
        assertEquals(204, deleted.status());
        assertEquals(List.of(kept), ids(schedules()));
        assertEquals(id(g), schedule(kept).get("groupId").asString());
        assertEquals(1, groups().size());
        assertEquals(g, groups().get(0));
    }

    /** V3 적용 전 행과 같은 모양(group_id를 쓰지 않은 행)은 일정과 관계없는 문서로 조회된다. */
    @Test
    void rowInsertedWithoutGroupIdIsListedWithoutGroupKey() {
        // given
        String docId = id(newDoc("기존"));
        String scheduleId = newId();
        jdbc.sql("""
                INSERT INTO schedules (id, document_id, start_date, planned_send_date, memo, created_at, updated_at)
                VALUES (:i, :d, DATE '2026-11-01', DATE '2026-11-20', '기존 막대',
                        TIMESTAMP '2026-11-01 01:02:03', TIMESTAMP '2026-11-01 01:02:03')""")
                .param("i", scheduleId).param("d", docId).update();

        // when
        JsonNode s = schedule(scheduleId);

        // then
        assertFalse(s.has("groupId"));
        assertEquals(docId, s.get("documentId").asString());
        assertEquals("2026-11-01", s.get("startDate").asString());
        assertEquals("2026-11-20", s.get("plannedSendDate").asString());
        assertEquals("기존 막대", s.get("memo").asString());
        assertEquals(1, schedules().size());
    }
}
