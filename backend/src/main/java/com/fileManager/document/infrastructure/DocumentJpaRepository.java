package com.fileManager.document.infrastructure;

import com.fileManager.document.domain.Document;
import com.fileManager.document.domain.DocumentRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DocumentJpaRepository extends JpaRepository<Document, UUID>, DocumentRepository {
    /**
     * 목록용 공유 잠금 읽기. 버전·파일·검토자는 join하지 않고 application.yml의 default_batch_fetch_size로 묶음 조회한다
     * (List 둘을 동시에 fetch join할 수 없고, 잠금 쿼리에 join을 걸지 않는다).
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select d from Document d order by d.createdAt asc, d.id asc")
    List<Document> findAllLockedForList();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.id = :id")
    Optional<Document> findLockedById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.category.id = :categoryId")
    Optional<Document> findLockedByCategoryId(UUID categoryId);
}
