package com.fileManager.category.presentation;

import com.fileManager.category.domain.Category;
import com.fileManager.global.util.IsoTimes;

public record CategoryResponse(String id, String name, String createdAt) {
    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getId().toString(), c.getName(), IsoTimes.format(c.getCreatedAt()));
    }
}
