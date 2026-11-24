package com.fileManager.schedule.domain;

import java.util.Optional;

public interface ScheduleSettingsRepository {
    /** 설정 행(id 1). */
    Optional<ScheduleSettings> findCurrent();

    /** 설정 행을 SELECT ... FOR UPDATE로 읽는다. 색 번호 배정과 설정 변경이 서로 덮어쓰지 않게 한다. */
    Optional<ScheduleSettings> findCurrentLocked();
}
