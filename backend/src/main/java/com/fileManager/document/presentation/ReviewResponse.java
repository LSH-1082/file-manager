package com.fileManager.document.presentation;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** 검토 결정 기록(docs/api 5.1 DocVersion.review). result는 accept 또는 reject. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewResponse(String result, List<String> reviewers, String reviewedAt, String fileId,
                             String revisionId) {}
