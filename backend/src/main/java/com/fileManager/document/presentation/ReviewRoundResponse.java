package com.fileManager.document.presentation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fileManager.document.domain.ReviewRound;
import com.fileManager.global.util.IsoTimes;
import java.util.List;

/** 검토 회차 한 건(docs/api 5.1 DocVersion.reviews). 검토본이 없거나 지워졌으면 reviewFile 키를 뺀다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewRoundResponse(int round, List<String> reviewers, String result, String reviewedAt, String fileId,
                                  String revisionId, FileRevResponse reviewFile) {
    static ReviewRoundResponse from(ReviewRound r) {
        return new ReviewRoundResponse(r.getRoundNo(), List.copyOf(r.getReviewers()), r.getResult(),
                IsoTimes.format(r.getReviewedAt()), r.getTargetFileId().toString(),
                r.getTargetRevisionId() == null ? null : r.getTargetRevisionId().toString(),
                r.getReviewCopy() == null ? null : FileRevResponse.of(r.getReviewCopy(), null));
    }
}
