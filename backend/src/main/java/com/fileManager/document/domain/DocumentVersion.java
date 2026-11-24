package com.fileManager.document.domain;

import com.fileManager.file.domain.StoredFile;
import com.fileManager.global.util.IsoTimes;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 문서의 버전. 단계 기록은 열로 두고 단계(stage)는 거기서 계산한다.
 * label은 화면 이름(name, 없으면 v{번호})으로 문서 안에서 UNIQUE이고, dirName은 문서 폴더 안의 버전 폴더 이름이다.
 */
@Entity
@Table(name = "document_versions", uniqueConstraints = {
        @UniqueConstraint(name = "uq_versions_number", columnNames = {"document_id", "number"}),
        @UniqueConstraint(name = "uq_versions_label", columnNames = {"document_id", "label"})})
public class DocumentVersion {
    public static final String ACCEPT = "accept";
    public static final String REJECT = "reject";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false)
    private int number;

    @Column(length = 5)
    private String name;

    @Column(nullable = false, length = 20)
    private String label;

    @Column(name = "dir_name", nullable = false)
    private String dirName;

    @Column(length = 5)
    private String worker;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "work_done_at")
    private Instant workDoneAt;

    @Column(name = "review_started_at")
    private Instant reviewStartedAt;

    @Column(name = "review_result", length = 10)
    private String reviewResult;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /** 결정 때의 현재 수정본·대조표. 그 파일이 지워지면 null로 비운다(document 서비스가 맡는다). */
    @Column(name = "review_file_id")
    private UUID reviewFileId;

    @Column(name = "review_revision_id")
    private UUID reviewRevisionId;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "sent_to", length = 20)
    private String sentTo;

    @Column(name = "sent_memo", columnDefinition = "text")
    private String sentMemo;

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq")
    private List<VersionFile> files = new ArrayList<>();

    /** 승인·거절 결정마다 한 건. 작업완료로 지워지지 않는다. */
    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("roundNo")
    private List<ReviewRound> rounds = new ArrayList<>();

    /** 검토 시작 때 적은 검토자 목록(현재 결정도 이 목록을 쓰고, 회차는 결정 때 복사한다). */
    @ElementCollection
    @CollectionTable(name = "version_reviewers", joinColumns = @JoinColumn(name = "version_id"))
    @OrderColumn(name = "reviewer_order")
    @Column(name = "name", nullable = false, length = 5)
    private List<String> reviewers = new ArrayList<>();

    protected DocumentVersion() {}

    DocumentVersion(Document document, int number, String name, String label, String dirName, String worker) {
        this.document = document;
        this.number = number;
        this.name = name;
        this.label = label;
        this.dirName = dirName;
        this.worker = worker;
        this.createdAt = IsoTimes.now();
    }

    // ---- 단계 ----

    /** working, queued, reviewing, ready, sent. 거절은 working(+rejected)이다. */
    public String stage() {
        if (sentAt != null) return "sent";
        if (ACCEPT.equals(reviewResult)) return "ready";
        if (REJECT.equals(reviewResult)) return "working";
        if (reviewStartedAt != null) return "reviewing";
        if (workDoneAt != null) return "queued";
        return "working";
    }

    public boolean rejected() { return sentAt == null && REJECT.equals(reviewResult); }

    /**
     * 거절됨인데 마지막 거절(reviewedAt) 뒤에 올린 수정본이 없다. 현재 파일이 원본뿐이거나 현재 수정본의 업로드 시각이
     * 거절 시각보다 뒤가 아니면 true. 판정은 파일 id가 아니라 업로드 시각이므로 거절 뒤 올린 수정본을 지워도 풀리지 않는다.
     */
    public boolean needsRevisionSinceRejection() {
        if (!rejected() || reviewedAt == null) return false;
        VersionFile cur = current();
        return cur.getSeq() == 0 || !cur.getFile().getUploadedAt().isAfter(reviewedAt);
    }

    public boolean sent() { return sentAt != null; }

    /**
     * 현재 파일(마지막)과 원본(seq 0). 파일 없이 만든 v1은 files가 비어 있으므로 부르기 전에 getFiles().isEmpty()를 본다.
     * 결정·검토본·거절 판정은 작업완료(파일 0개면 409) 뒤에만 오므로 비어 있지 않다.
     */
    public VersionFile current() { return files.get(files.size() - 1); }

    public VersionFile original() { return files.get(0); }

    // ---- 변경 ----

    public VersionFile addFile(int seq, StoredFile file, StoredFile revision) {
        VersionFile f = new VersionFile(this, seq, file, revision);
        files.add(f);
        return f;
    }

    public void removeFile(VersionFile f) { files.remove(f); }

    public void rename(String name, String label) {
        this.name = name;
        this.label = label;
    }

    public void moveDir(String dirName) { this.dirName = dirName; }

    public void workDone() {
        this.workDoneAt = IsoTimes.now();
        this.reviewStartedAt = null;
        clearDecision();
        this.reviewers.clear();
    }

    public void startReview(List<String> names) {
        this.reviewStartedAt = IsoTimes.now();
        this.reviewers.clear();
        this.reviewers.addAll(names);
    }

    /** 현재 결정을 쓰고 회차를 더한다. reviewCopy는 거절의 검토본(승인이면 null). */
    public void decide(String result, StoredFile reviewCopy) {
        VersionFile cur = current();
        Instant now = IsoTimes.now();
        this.reviewResult = result;
        this.reviewedAt = now;
        this.reviewFileId = cur.fileId();
        this.reviewRevisionId = cur.revisionId();
        int next = rounds.isEmpty() ? 1 : rounds.get(rounds.size() - 1).getRoundNo() + 1;
        rounds.add(new ReviewRound(this, next, result, now, cur.fileId(), cur.revisionId(),
                List.copyOf(reviewers), reviewCopy));
    }

    /** 현재 결정을 지우고 마지막 회차를 이력에서 뺀다. 그 회차의 검토본(없으면 null)을 돌려준다. */
    public StoredFile cancelDecision() {
        clearDecision();
        if (rounds.isEmpty()) return null; // SQL로 만든 결정은 회차가 없을 수 있다
        return rounds.remove(rounds.size() - 1).getReviewCopy();
    }

    /** targetFileId가 같은 회차의 검토본 연결을 비우고 그 파일들을 돌려준다. 회차는 남긴다. */
    public List<StoredFile> detachReviewCopiesOf(UUID fileId) {
        List<StoredFile> out = new ArrayList<>();
        for (ReviewRound r : rounds) {
            if (fileId.equals(r.getTargetFileId()) && r.getReviewCopy() != null) out.add(r.detachReviewCopy());
        }
        return out;
    }

    /** 모든 회차의 검토본. */
    public List<StoredFile> reviewCopies() {
        List<StoredFile> out = new ArrayList<>();
        for (ReviewRound r : rounds) if (r.getReviewCopy() != null) out.add(r.getReviewCopy());
        return out;
    }

    public void send(Instant at, String to, String memo) {
        this.sentAt = at;
        this.sentTo = to;
        this.sentMemo = memo;
    }

    public void cancelWorkDone() {
        this.workDoneAt = null;
    }

    public void cancelReviewStart() {
        this.reviewStartedAt = null;
        this.reviewers.clear();
    }

    public void cancelSend() {
        this.sentAt = null;
        this.sentTo = null;
        this.sentMemo = null;
    }

    public void clearDecision() {
        this.reviewResult = null;
        this.reviewedAt = null;
        this.reviewFileId = null;
        this.reviewRevisionId = null;
    }

    /** 결정 때 기록한 파일이 지워졌다. */
    public void forgetReviewFile(UUID storedFileId) {
        if (storedFileId.equals(reviewFileId)) reviewFileId = null;
        if (storedFileId.equals(reviewRevisionId)) reviewRevisionId = null;
    }

    /** 현재 수정본·대조표가 거절 때 기록한 것과 같은가. 기록 파일이 지워져 null이면 바뀐 것으로 본다. */
    public boolean unchangedSinceDecision() {
        VersionFile cur = current();
        return reviewFileId != null && reviewFileId.equals(cur.fileId())
                && Objects.equals(reviewRevisionId, cur.revisionId());
    }

    // ---- 읽기 ----

    public UUID getId() { return id; }
    public int getNumber() { return number; }
    public String getName() { return name; }
    public String getLabel() { return label; }
    public String getDirName() { return dirName; }
    public String getWorker() { return worker; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getWorkDoneAt() { return workDoneAt; }
    public Instant getReviewStartedAt() { return reviewStartedAt; }
    public String getReviewResult() { return reviewResult; }
    public Instant getReviewedAt() { return reviewedAt; }
    public UUID getReviewFileId() { return reviewFileId; }
    public UUID getReviewRevisionId() { return reviewRevisionId; }
    public Instant getSentAt() { return sentAt; }
    public String getSentTo() { return sentTo; }
    public String getSentMemo() { return sentMemo; }
    public List<VersionFile> getFiles() { return files; }
    public List<String> getReviewers() { return reviewers; }
    public List<ReviewRound> getRounds() { return rounds; }
}
