package com.fileManager.schedule.infrastructure;

import com.fileManager.schedule.domain.ScheduleSettings;
import com.fileManager.schedule.domain.ScheduleSettingsRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ScheduleSettingsJpaRepository
        extends JpaRepository<ScheduleSettings, Integer>, ScheduleSettingsRepository {
    @Query("select s from ScheduleSettings s where s.id = 1")
    Optional<ScheduleSettings> findCurrent();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ScheduleSettings s where s.id = 1")
    Optional<ScheduleSettings> findCurrentLocked();
}
