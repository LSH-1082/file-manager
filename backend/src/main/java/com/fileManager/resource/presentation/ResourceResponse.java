package com.fileManager.resource.presentation;

import com.fileManager.global.util.IsoTimes;
import com.fileManager.resource.domain.CategoryResource;

/** 양식·참고자료 응답(docs/api 5.1 CategoryResource). 선택 필드가 없어 모든 키가 항상 있다. */
public record ResourceResponse(String id, String categoryId, String kind, String title, String note,
                               String fileName, long fileSize, String mimeType, String createdAt, String updatedAt) {
    public static ResourceResponse from(CategoryResource r) {
        return new ResourceResponse(r.getId().toString(), r.getCategory().getId().toString(), r.getKind(),
                r.getTitle(), r.getNote(), r.getFile().getFileName(), r.getFile().getSizeBytes(),
                r.getFile().getMimeType(), IsoTimes.format(r.getCreatedAt()), IsoTimes.format(r.getUpdatedAt()));
    }
}
