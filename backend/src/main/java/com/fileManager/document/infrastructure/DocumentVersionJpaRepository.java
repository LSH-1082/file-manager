package com.fileManager.document.infrastructure;

import com.fileManager.document.domain.DocumentVersion;
import com.fileManager.document.domain.DocumentVersionRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DocumentVersionJpaRepository extends JpaRepository<DocumentVersion, UUID>, DocumentVersionRepository {
    @Query("select v from DocumentVersion v where v.reviewFileId in :ids or v.reviewRevisionId in :ids")
    List<DocumentVersion> findByReviewFiles(Collection<UUID> ids);
}
