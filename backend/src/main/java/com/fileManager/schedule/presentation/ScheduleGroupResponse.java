package com.fileManager.schedule.presentation;

import com.fileManager.global.util.IsoTimes;
import com.fileManager.schedule.domain.ScheduleGroup;

/** 일정(docs/api 5.1 ScheduleGroup). 선택 필드가 없다. 날짜는 YYYY-MM-DD, 시각은 1절 ISO 형식이다. */
public record ScheduleGroupResponse(String id, String name, String startDate, String endDate, int colorNo,
                                    String createdAt, String updatedAt) {
    public static ScheduleGroupResponse from(ScheduleGroup g) {
        return new ScheduleGroupResponse(g.getId().toString(), g.getName(), g.getStartDate().toString(),
                g.getEndDate().toString(), g.getColorNo(),
                IsoTimes.format(g.getCreatedAt()), IsoTimes.format(g.getUpdatedAt()));
    }
}
