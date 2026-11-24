package com.fileManager.schedule.presentation;

import com.fileManager.global.error.ApiException;
import tools.jackson.databind.JsonNode;

/** 보기 설정 요청 본문(docs/api 5.2 ScheduleSettings). layout 밖의 키는 읽지 않는다. 값 범위는 서비스가 검사한다. */
record ScheduleSettingsRequest(String layout) {
    static ScheduleSettingsRequest from(JsonNode body) {
        ScheduleBodies.requireObject(body);
        String layout = ScheduleBodies.optionalString(body, "layout");
        if (layout == null) throw ApiException.format();
        return new ScheduleSettingsRequest(layout);
    }
}
