package com.fileManager.schedule.infrastructure;

import com.fileManager.schedule.domain.ScheduleGroup;
import com.fileManager.schedule.domain.ScheduleGroupRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ScheduleGroupJpaRepository extends JpaRepository<ScheduleGroup, UUID>, ScheduleGroupRepository {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from ScheduleGroup g where g.id = :id")
    Optional<ScheduleGroup> findLockedById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select g from ScheduleGroup g where g.id = :id")
    Optional<ScheduleGroup> findSharedById(UUID id);
}
