package com.fileManager.document.presentation;

/** 분류 지정 요청: categoryId 또는 newCategory 정확히 하나(docs/api 8.5). */
public record AssignRequest(String categoryId, NewCategory newCategory) {
    public record NewCategory(String name) {}
}
