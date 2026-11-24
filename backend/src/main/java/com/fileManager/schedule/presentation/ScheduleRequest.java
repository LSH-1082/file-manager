package com.fileManager.schedule.presentation;

import com.fileManager.global.error.ApiException;
import tools.jackson.databind.JsonNode;

/**
 * 발송 문서 요청 본문(docs/api 5.2 ScheduleInput·발송 문서 수정). 값의 타입 검사 방식은 {@link ScheduleBodies}에 있다.
 * 이 형식 검사(400 요청 형식)는 서비스의 404·날짜·메모 검사보다 먼저다.
 */
record ScheduleRequest(String documentId, String groupId, String startDate, String plannedSendDate, String memo) {
    /**
     * POST 본문. documentId는 문자열이어야 한다(없거나 null이거나 다른 타입이면 형식 오류).
     * groupId는 없거나 null이면 null이고, 빈 문자열은 그대로 넘겨 서비스에서 없는 일정(404)이 된다.
     */
    static ScheduleRequest forCreate(JsonNode body) {
        ScheduleBodies.requireObject(body);
        JsonNode id = body.get("documentId");
        if (id == null || !id.isString()) throw ApiException.format();
        return new ScheduleRequest(id.asString(), ScheduleBodies.optionalString(body, "groupId"),
                ScheduleBodies.optionalString(body, "startDate"),
                ScheduleBodies.optionalString(body, "plannedSendDate"), ScheduleBodies.optionalString(body, "memo"));
    }

    /** PUT 본문. documentId(D-28)는 읽지 않는다. groupId는 전체 교체라 없거나 null이면 일정과 관계없는 문서가 된다(D-33). */
    static ScheduleRequest forUpdate(JsonNode body) {
        ScheduleBodies.requireObject(body);
        return new ScheduleRequest(null, ScheduleBodies.optionalString(body, "groupId"), ScheduleBodies.optionalString(body, "startDate"),
                ScheduleBodies.optionalString(body, "plannedSendDate"), ScheduleBodies.optionalString(body, "memo"));
    }
}
