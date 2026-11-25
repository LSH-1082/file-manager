package com.fileManager.schedule;

import com.fileManager.ApiTestSupport;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;

/** 보기 설정 API(docs/api 8.31~8.32) 통합 테스트. */
class ScheduleSettingsApiTest extends ApiTestSupport {
    private static final String PATH = "/api/schedule-settings";
    private static final String FORMAT = "요청 형식이 올바르지 않습니다.";

    private void assertLayout(String expected, Response r) {
        assertEquals(200, r.status(), r.text());
        assertEquals("{\"layout\":\"" + expected + "\"}", r.text());
    }

    private int createGroupColor(String name) {
        Response r = post("/api/schedule-groups", Map.of("name", name, "startDate", "2026-11-01", "endDate", "2026-11-30"));
        assertEquals(201, r.status(), r.text());
        JsonNode g = r.json();
        return g.get("colorNo").asInt();
    }

    @Test
    void startsWithGroupAndSavesLayout() {
        // when & then: 처음에는 group이다
        assertLayout("group", get(PATH));

        // when & then: band로 저장한다
        assertLayout("band", put(PATH, Map.of("layout", "band")));
        assertLayout("band", get(PATH));

        // when & then: 같은 값을 다시 저장해도 된다
        assertLayout("band", put(PATH, Map.of("layout", "band")));

        // when & then: group으로 되돌린다
        assertLayout("group", put(PATH, Map.of("layout", "group")));
        assertLayout("group", get(PATH));
    }

    @Test
    void rejectsAnyOtherLayoutAndKeepsSavedValue() {
        // given
        assertLayout("band", put(PATH, Map.of("layout", "band")));

        // when & then: 다른 값은 모두 거절한다
        assertError(put(PATH, Map.of()), 400, "INVALID_INPUT", FORMAT);
        assertError(put(PATH, Collections.singletonMap("layout", null)), 400, "INVALID_INPUT", FORMAT);
        for (Object bad : List.of(1, true, List.of("group"), Map.of("v", "group"), "", " ", "GROUP", "BAND", "Band", " band ",
                "band ", "x")) {
            assertError(put(PATH, Map.of("layout", bad)), 400, "INVALID_INPUT", FORMAT);
        }
        assertError(put(PATH, "[]"), 400, "INVALID_INPUT", FORMAT);
        assertError(put(PATH, "null"), 400, "INVALID_INPUT", FORMAT);
        assertError(put(PATH, "{"), 400, "INVALID_INPUT", FORMAT);

        // then: 저장한 값은 그대로다
        assertLayout("band", get(PATH));
    }

    @Test
    void ignoresOtherKeysAndDoesNotChangeNextColor() {
        // when & then: 일정 2개를 만든다
        assertEquals(0, createGroupColor("가"));
        assertEquals(1, createGroupColor("나"));

        // when & then: 다른 키를 넣어 보기 설정을 바꿔도 다음 색 번호는 그대로다
        assertLayout("band", put(PATH, Map.of("layout", "band", "nextColorNo", 5, "id", 2, "x", "y")));
        assertEquals(2, createGroupColor("다"));

        // when & then: group으로 바꾼다
        assertLayout("group", put(PATH, Map.of("layout", "group")));
        assertEquals(3, createGroupColor("라"));

        // then: 일정 등록도 보기 설정을 바꾸지 않는다
        assertLayout("group", get(PATH));
        assertEquals(1L, jdbc.sql("SELECT count(*) FROM schedule_settings").query(Long.class).single());
    }
}
