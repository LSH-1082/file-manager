package com.fileManager.schedule.infrastructure;

import com.fileManager.schedule.domain.Schedule;
import com.fileManager.schedule.domain.ScheduleRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleJpaRepository extends JpaRepository<Schedule, UUID>, ScheduleRepository {
}
