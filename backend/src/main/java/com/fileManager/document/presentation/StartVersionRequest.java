package com.fileManager.document.presentation;

/** 새 버전 시작 요청(docs/api 8.17). */
public record StartVersionRequest(String versionName, String worker) {}
