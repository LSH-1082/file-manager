package com.fileManager.document.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DocumentVersionRepository {
    /** 검토 결정 기록이 가리키는 파일(지울 파일의 참조를 비우기 위해). */
    List<DocumentVersion> findByReviewFiles(Collection<UUID> ids);
}
