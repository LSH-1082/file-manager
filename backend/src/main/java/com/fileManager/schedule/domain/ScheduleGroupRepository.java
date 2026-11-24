package com.fileManager.schedule.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleGroupRepository {
    /** docs/api 1절의 일정 정렬: 시작일 → 종료일 → 생성 시각(같으면 id). */
    List<ScheduleGroup> findAllByOrderByStartDateAscEndDateAscCreatedAtAscIdAsc();

    /** SELECT ... FOR UPDATE. 일정 수정·삭제용. 잠금 순서는 documents -> schedule_groups(docs/db 5절). */
    Optional<ScheduleGroup> findLockedById(UUID id);

    /** 공유 잠금. 발송 문서를 넣는 동안 소속 일정이 지워지지 않게 한다. */
    Optional<ScheduleGroup> findSharedById(UUID id);

    ScheduleGroup save(ScheduleGroup g);

    void delete(ScheduleGroup g);

    void flush();
}
