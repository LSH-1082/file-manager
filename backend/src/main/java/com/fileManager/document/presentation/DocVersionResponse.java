package com.fileManager.document.presentation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fileManager.document.domain.DocumentVersion;
import com.fileManager.document.domain.VersionFile;
import com.fileManager.global.util.IsoTimes;
import java.util.List;
import java.util.UUID;

/** 문서의 버전(docs/api 5.1 DocVersion). 값이 없는 선택 필드는 키를 뺀다. status는 sent_at이 있으면 sent, 없으면 working이다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocVersionResponse(String id, int number, String name, String status, String worker,
                                 WorkDoneResponse workDone, ReviewStartResponse reviewStart, ReviewResponse review,
                                 List<ReviewRoundResponse> reviews,
                                 List<FileRevResponse> files, String createdAt, String sentAt, String sentTo,
                                 String sentMemo) {
    static DocVersionResponse from(DocumentVersion v) {
        List<String> reviewers = List.copyOf(v.getReviewers());
        WorkDoneResponse workDone = v.getWorkDoneAt() == null ? null
                : new WorkDoneResponse(IsoTimes.format(v.getWorkDoneAt()));
        ReviewStartResponse reviewStart = v.getReviewStartedAt() == null ? null
                : new ReviewStartResponse(IsoTimes.format(v.getReviewStartedAt()), reviewers);
        ReviewResponse review = v.getReviewResult() == null ? null
                : new ReviewResponse(v.getReviewResult(), reviewers, IsoTimes.format(v.getReviewedAt()),
                        str(v.getReviewFileId()), str(v.getReviewRevisionId()));
        return new DocVersionResponse(v.getId().toString(), v.getNumber(), v.getName(),
                v.sent() ? "sent" : "working", v.getWorker(), workDone, reviewStart, review,
                v.getRounds().stream().map(ReviewRoundResponse::from).toList(),
                v.getFiles().stream().map(DocVersionResponse::file).toList(), IsoTimes.format(v.getCreatedAt()),
                IsoTimes.format(v.getSentAt()), v.getSentTo(), v.getSentMemo());
    }

    private static FileRevResponse file(VersionFile f) {
        FileRevResponse revision = f.getRevisionFile() == null ? null : FileRevResponse.of(f.getRevisionFile(), null);
        return FileRevResponse.of(f.getFile(), revision);
    }

    private static String str(UUID id) { return id == null ? null : id.toString(); }
}
