package com.fileManager.document.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {
    /** 목록용. documents 행을 공유 잠금하고 만든 순서로 돌려준다. 진행 중인 변경이 커밋된 뒤 읽는다. 버전·파일·검토자는 묶음 조회한다(infrastructure가 정한다). */
    List<Document> findAllLockedForList();

    /** SELECT ... FOR UPDATE. 잠금 순서는 categories -> documents(docs/db 5절). */
    Optional<Document> findLockedById(UUID id);

    Optional<Document> findLockedByCategoryId(UUID categoryId);

    boolean existsByCategoryId(UUID categoryId);

    Document save(Document d);

    void delete(Document d);

    void flush();
}
