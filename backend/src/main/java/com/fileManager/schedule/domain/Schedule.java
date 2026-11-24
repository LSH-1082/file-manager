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

/**
 * 문서에 딸린 발송 문서(시작일·발송 예정일·메모). 문서와 소속 일정의 연관은 두지 않고 id만 가진다.
 * 문서가 지워지면 DB의 fk_schedules_document ON DELETE CASCADE가 발송 문서를 지운다(Document는 발송 문서를 모른다).
 * 소속 일정이 지워지면 DB의 fk_schedules_group ON DELETE SET NULL이 group_id를 비운다.
 */
@Entity
@Table(name = "schedules")
public class Schedule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    /** 소속 일정. null이면 일정과 관계없는 문서. 수정은 요청의 값(공유 잠금으로 확인한 일정 또는 null)으로만 바꾼다(docs/db 5절). */
    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "planned_send_date", nullable = false)
    private LocalDate plannedSendDate;

    @Column(length = 20)
    private String memo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Schedule() {}

    /** 새 발송 문서. groupId가 null이면 일정과 관계없는 문서다. id는 저장할 때 새 UUID가 된다(다른 엔티티와 같은 매핑). createdAt과 updatedAt은 같은 now다. memo가 null이면 메모 없음. */
    public static Schedule create(UUID documentId, UUID groupId, LocalDate startDate, LocalDate plannedSendDate,
                                  String memo, Instant now) {
        Schedule s = new Schedule();
        s.documentId = documentId;
        s.groupId = groupId;
        s.startDate = startDate;
        s.plannedSendDate = plannedSendDate;
        s.memo = memo;
        s.createdAt = now;
        s.updatedAt = now;
        return s;
    }

    /** 소속 일정·날짜·메모를 바꾼다(D-33). documentId·createdAt은 그대로다(D-28). groupId가 null이면 일정과 관계없는 문서다. */
    public void update(UUID groupId, LocalDate startDate, LocalDate plannedSendDate, String memo, Instant now) {
        this.groupId = groupId;
        this.startDate = startDate;
        this.plannedSendDate = plannedSendDate;
        this.memo = memo;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getDocumentId() { return documentId; }
    public UUID getGroupId() { return groupId; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getPlannedSendDate() { return plannedSendDate; }
    public String getMemo() { return memo; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
