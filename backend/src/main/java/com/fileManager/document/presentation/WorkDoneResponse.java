package com.fileManager.document.presentation;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 작업완료 기록(docs/api 5.1 DocVersion.workDone). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkDoneResponse(String completedAt) {}
