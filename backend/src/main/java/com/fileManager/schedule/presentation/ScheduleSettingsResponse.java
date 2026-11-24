package com.fileManager.schedule.presentation;

import com.fileManager.schedule.domain.ScheduleSettings;

/** 보기 설정(docs/api 5.1 ScheduleSettings). 다음 색 번호는 응답에 담지 않는다. */
public record ScheduleSettingsResponse(String layout) {
    public static ScheduleSettingsResponse from(ScheduleSettings s) {
        return new ScheduleSettingsResponse(s.getLayout());
    }
}
