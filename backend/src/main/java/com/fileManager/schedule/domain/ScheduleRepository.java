package com.fileManager.schedule.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleRepository {
    /** docs/api 1절의 일정 정렬: 시작일 → 발송 예정일 → 생성 시각(같으면 id). */
    List<Schedule> findAllByOrderByStartDateAscPlannedSendDateAscCreatedAtAscIdAsc();

    Optional<Schedule> findById(UUID id);

    Schedule save(Schedule s);

    /** 없는 id면 아무것도 하지 않는다. */
    void deleteById(UUID id);

    void flush();
}
