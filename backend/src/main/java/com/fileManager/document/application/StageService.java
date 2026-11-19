package com.fileManager.document.application;

import com.fileManager.document.domain.Document;
import com.fileManager.document.domain.DocumentRepository;
import com.fileManager.document.domain.DocumentVersion;
import com.fileManager.file.application.StoredFileService;
import com.fileManager.file.domain.FileUpload;
import com.fileManager.file.domain.StoredFile;
import com.fileManager.global.error.ApiException;
import com.fileManager.global.util.IsoTimes;
import com.fileManager.global.util.Texts;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.fileManager.document.application.VersionRules.CANCEL_PHRASE;
import static com.fileManager.document.application.VersionRules.STAGE_LABEL;

/**
 * 단계 전이: 작업완료, 검토 시작, 승인·거절, 발송, 취소(docs/api 8.11~8.15). 모든 전이는 최신 버전에만 적용하며
 * 거절의 검토본 저장과 결정 취소의 검토본 삭제만 파일을 다룬다(ReviewCopies). 문구와 검사 순서는 docs/api가 정본이다.
 */
@Service
@Transactional
public class StageService {
    private static final int REVIEWER_MAX = 5;
    private static final int SENT_TO_MAX = 20;
    private static final Set<String> STEPS = CANCEL_PHRASE.keySet();

    private final DocumentService documents;
    private final ReviewCopies reviewCopies;
    private final DocumentRepository documentRepository;

    StageService(DocumentService documents, ReviewCopies reviewCopies, DocumentRepository documentRepository) {
        this.documents = documents;
        this.reviewCopies = reviewCopies;
        this.documentRepository = documentRepository;
    }

    static final String REJECTED_NEEDS_REVISION = "거절된 뒤 수정본을 올려야 작업완료할 수 있습니다.";

    public <R> R workDone(String docId, Function<? super Document, R> view) {
        Document doc = lockOpen(docId);
        DocumentVersion v = doc.latest();
        String stage = v.stage();
        if (!"working".equals(stage)) throw VersionRules.already(stage);
        if (v.getNumber() >= 2 && v.getFiles().size() < 2) {
            throw ApiException.conflict("수정본을 올려야 작업완료할 수 있습니다.");
        }
        if (v.needsRevisionSinceRejection()) throw ApiException.conflict(REJECTED_NEEDS_REVISION);
        v.workDone();
        return done(doc, view);
    }

    public <R> R reviewStart(String docId, List<String> reviewers, Function<? super Document, R> view) {
        List<String> names = new ArrayList<>();
        for (String raw : reviewers) {
            String name = Texts.trim(raw);
            if (!name.isEmpty()) names.add(name);
        }
        if (names.isEmpty()) throw ApiException.badRequest("검토자 이름을 한 명 이상 적어야 합니다.");
        if (names.stream().anyMatch(n -> n.length() > REVIEWER_MAX)) {
            throw ApiException.badRequest("검토자 이름은 5자까지 쓸 수 있습니다.");
        }
        Document doc = lockOpen(docId);
        DocumentVersion v = doc.latest();
        String stage = v.stage();
        if ("working".equals(stage)) throw ApiException.conflict("작업완료를 먼저 해야 검토를 시작할 수 있습니다.");
        if (!"queued".equals(stage)) throw VersionRules.already(stage);
        v.startReview(names);
        return done(doc, view);
    }

    public <R> R reviewDecision(String docId, String decision, String reviewer, List<FileUpload> fileParts,
                                Function<? super Document, R> view) {
        boolean reject = "reject".equals(decision);
        if (!reject && !"approve".equals(decision)) throw ApiException.badRequest("승인 또는 거절만 할 수 있습니다.");
        FileUpload file = reject ? StoredFileService.single(fileParts) : null;
        Document doc = lockOpen(docId);
        DocumentVersion v = doc.latest();
        String stage = v.stage();
        if ("working".equals(stage) || "queued".equals(stage)) {
            throw ApiException.conflict("검토를 시작해야 승인하거나 거절할 수 있습니다.");
        }
        if (!"reviewing".equals(stage)) throw VersionRules.already(stage);
        if (!reject) {
            v.decide(DocumentVersion.ACCEPT, null);
            return done(doc, view);
        }
        if (file == null) throw ApiException.badRequest(VersionRules.REVIEW_COPY_REQUIRED);
        VersionRules.requireDocFile(file);
        String by = Texts.trim(reviewer == null ? "" : reviewer);
        if (!v.getReviewers().contains(by)) throw ApiException.badRequest(VersionRules.REVIEWER_NOT_LISTED);
        StoredFile copy = reviewCopies.store(doc, v, file, by);
        v.decide(DocumentVersion.REJECT, copy);
        return done(doc, view);
    }

    public <R> R send(String docId, String sentAt, String sentTo, String sentMemo, Function<? super Document, R> view) {
        Instant at = IsoTimes.parse(sentAt);
        String to = Texts.trim(sentTo);
        if (to.length() > SENT_TO_MAX) throw ApiException.badRequest("발송처는 20자까지 입력할 수 있습니다.");
        Document doc = lockOpen(docId);
        DocumentVersion v = doc.latest();
        switch (v.stage()) {
            case "working" -> throw ApiException.conflict(v.rejected()
                    ? "검토에서 거절되었습니다. 승인을 받아야 발송할 수 있습니다."
                    : "작업완료와 검토 승인을 거쳐야 발송할 수 있습니다.");
            case "queued" -> throw ApiException.conflict("검토를 시작하고 승인을 받아야 발송할 수 있습니다.");
            case "reviewing" -> throw ApiException.conflict("검토중입니다. 승인을 받아야 발송할 수 있습니다.");
            default -> v.send(at, to, Texts.trim(sentMemo));
        }
        return done(doc, view);
    }

    public <R> R cancel(String docId, String step, Function<? super Document, R> view) {
        if (step == null || !STEPS.contains(step)) throw ApiException.format();
        Document doc = documents.lockDoc(docId);
        DocumentVersion v = doc.latest();
        if (!step.equals(available(v))) {
            if ("reject".equals(step) && v.rejected()) {
                throw ApiException.conflict("거절 뒤 수정본이나 수정대조표가 바뀌어 거절 취소를 할 수 없습니다.");
            }
            throw ApiException.conflict("지금 단계(" + STAGE_LABEL.get(v.stage()) + (v.rejected() ? ", 거절됨" : "")
                    + ")에서는 " + CANCEL_PHRASE.get(step) + "할 수 없습니다.");
        }
        switch (step) {
            case "workDone" -> v.cancelWorkDone();
            case "reviewStart" -> v.cancelReviewStart();
            case "send" -> v.cancelSend();
            default -> { // approve, reject
                StoredFile copy = v.cancelDecision();
                if (copy != null) reviewCopies.delete(List.of(copy));
            }
        }
        return done(doc, view);
    }

    /** 지금 단계에서 할 수 있는 취소(단계마다 하나). 없으면 null. */
    private static String available(DocumentVersion v) {
        return switch (v.stage()) {
            case "sent" -> "send";
            case "ready" -> "approve";
            case "reviewing" -> "reviewStart";
            case "queued" -> "workDone";
            default -> v.rejected() && v.unchangedSinceDecision() ? "reject" : null;
        };
    }

    /** 문서를 잠그고 최신 버전이 발송되지 않았는지 본다. */
    private Document lockOpen(String docId) {
        Document doc = documents.lockDoc(docId);
        if (doc.latest().sent()) throw ApiException.conflict(VersionRules.SENT_EDIT);
        return doc;
    }

    private <R> R done(Document doc, Function<? super Document, R> view) {
        doc.touch();
        documentRepository.flush(); // 새 회차 id가 채워진 뒤 응답을 만든다
        return view.apply(doc);
    }
}
