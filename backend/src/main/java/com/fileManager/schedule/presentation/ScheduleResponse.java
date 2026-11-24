package com.fileManager.schedule.presentation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fileManager.global.util.IsoTimes;
import com.fileManager.schedule.application.ScheduleView;
import com.fileManager.schedule.domain.Schedule;

/**
 * 발송 문서(docs/api 5.1 Schedule). 선택 필드는 groupId와 memo이고, 없으면 키를 뺀다.
 * categoryId는 Doc.categoryId와 같이 항상 키가 있고 미분류면 null이다. 날짜는 YYYY-MM-DD, 시각은 1절 ISO 형식이다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScheduleResponse(String id, String documentId, String documentTitle,
                               @JsonInclude(JsonInclude.Include.ALWAYS) String categoryId,
                               String stage, String groupId, String startDate, String plannedSendDate, String memo,
                               String createdAt, String updatedAt) {
    public static ScheduleResponse from(ScheduleView v) {
        Schedule s = v.schedule();
        return new ScheduleResponse(s.getId().toString(), s.getDocumentId().toString(), v.documentTitle(),
                v.categoryId() == null ? null : v.categoryId().toString(), v.stage(),
                s.getGroupId() == null ? null : s.getGroupId().toString(),
                s.getStartDate().toString(), s.getPlannedSendDate().toString(), s.getMemo(),
                IsoTimes.format(s.getCreatedAt()), IsoTimes.format(s.getUpdatedAt()));
    }
}
