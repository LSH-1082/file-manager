package com.fileManager.document.presentation;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** 검토 시작 기록(docs/api 5.1 DocVersion.reviewStart). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewStartResponse(String startedAt, List<String> reviewers) {}
