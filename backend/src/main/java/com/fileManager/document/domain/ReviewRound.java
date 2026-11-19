package com.fileManager.document.domain;

import com.fileManager.file.domain.StoredFile;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 검토 회차: 승인·거절 결정마다 한 행. 작업완료가 현재 결정을 지워도 남고, 결정 취소가 마지막 회차를 지운다.
 * 대상 파일(targetFileId, targetRevisionId)은 결정 당시의 현재 수정본(없으면 원본)·대조표 id이며 FK 없이 둔다.
 * reviewCopy는 거절 때 올린 검토본이다. 만드는 곳은 DocumentVersion뿐이다.
 */
@Entity
@Table(name = "version_review_rounds")
public class ReviewRound {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private DocumentVersion version;

    @Column(name = "round_no", nullable = false)
    private int roundNo;

    @Column(nullable = false, length = 10)
    private String result;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    @Column(name = "target_file_id", nullable = false)
    private UUID targetFileId;

    @Column(name = "target_revision_id")
    private UUID targetRevisionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_file_id", unique = true)
    private StoredFile reviewCopy;

    @ElementCollection
    @CollectionTable(name = "review_round_reviewers", joinColumns = @JoinColumn(name = "round_id"))
    @OrderColumn(name = "reviewer_order")
    @Column(name = "name", nullable = false, length = 5)
    private List<String> reviewers = new ArrayList<>();

    protected ReviewRound() {}

    ReviewRound(DocumentVersion version, int roundNo, String result, Instant reviewedAt, UUID targetFileId,
                UUID targetRevisionId, List<String> reviewers, StoredFile reviewCopy) {
        if (reviewCopy != null && !DocumentVersion.REJECT.equals(result)) {
            throw new IllegalArgumentException("검토본은 거절 회차에만 둘 수 있습니다.");
        }
        this.version = version;
        this.roundNo = roundNo;
        this.result = result;
        this.reviewedAt = reviewedAt;
        this.targetFileId = targetFileId;
        this.targetRevisionId = targetRevisionId;
        this.reviewers.addAll(reviewers);
        this.reviewCopy = reviewCopy;
    }

    /** 검토본 연결을 비우고 이전 값을 돌려준다(없으면 null). */
    public StoredFile detachReviewCopy() {
        StoredFile old = reviewCopy;
        reviewCopy = null;
        return old;
    }

    public UUID getId() { return id; }
    public int getRoundNo() { return roundNo; }
    public String getResult() { return result; }
    public Instant getReviewedAt() { return reviewedAt; }
    public UUID getTargetFileId() { return targetFileId; }
    public UUID getTargetRevisionId() { return targetRevisionId; }
    public StoredFile getReviewCopy() { return reviewCopy; }
    public List<String> getReviewers() { return reviewers; }
}
