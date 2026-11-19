package com.fileManager.document.presentation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fileManager.document.domain.Document;
import com.fileManager.global.util.IsoTimes;
import java.util.List;

/** 문서(docs/api 5.1 Doc). 키를 빼는 규칙의 예외는 categoryId 하나로, 항상 키가 있고 미분류면 null이다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocResponse(String id, String title,
                          @JsonInclude(JsonInclude.Include.ALWAYS) String categoryId,
                          List<DocVersionResponse> versions, String createdAt, String updatedAt) {
    public static DocResponse from(Document d) {
        return new DocResponse(d.getId().toString(), d.getTitle(),
                d.getCategory() == null ? null : d.getCategory().getId().toString(),
                d.getVersions().stream().map(DocVersionResponse::from).toList(),
                IsoTimes.format(d.getCreatedAt()), IsoTimes.format(d.getUpdatedAt()));
    }
}
