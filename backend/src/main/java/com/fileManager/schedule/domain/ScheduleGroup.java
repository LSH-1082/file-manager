package com.fileManager.schedule.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** 일정(기간 묶음). 발송 문서({@link Schedule})가 groupId로 선택해 속한다. 색 번호는 등록할 때 정해지고 바뀌지 않는다(D-32). */
@Entity
@Table(name = "schedule_groups")
public class ScheduleGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "color_no", nullable = false, updatable = false)
    private int colorNo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ScheduleGroup() {}

    /** 새 일정. id는 저장할 때 새 UUID가 된다(다른 엔티티와 같은 매핑). createdAt과 updatedAt은 같은 now다. */
    public static ScheduleGroup create(String name, LocalDate startDate, LocalDate endDate, int colorNo, Instant now) {
        ScheduleGroup g = new ScheduleGroup();
        g.name = name;
        g.startDate = startDate;
        g.endDate = endDate;
        g.colorNo = colorNo;
        g.createdAt = now;
        g.updatedAt = now;
        return g;
    }

    /** 이름·기간만 바꾼다. colorNo·createdAt은 그대로다. */
    public void update(String name, LocalDate startDate, LocalDate endDate, Instant now) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public int getColorNo() { return colorNo; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
