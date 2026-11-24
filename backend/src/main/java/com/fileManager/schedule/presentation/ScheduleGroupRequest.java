package com.fileManager.schedule.presentation;

import tools.jackson.databind.JsonNode;

/** 일정 요청 본문(docs/api 5.2 ScheduleGroupInput). POST와 PUT이 같다. colorNo를 포함한 다른 키는 읽지 않는다. */
record ScheduleGroupRequest(String name, String startDate, String endDate) {
    static ScheduleGroupRequest from(JsonNode body) {
        ScheduleBodies.requireObject(body);
        return new ScheduleGroupRequest(ScheduleBodies.optionalString(body, "name"),
                ScheduleBodies.optionalString(body, "startDate"), ScheduleBodies.optionalString(body, "endDate"));
    }
}
