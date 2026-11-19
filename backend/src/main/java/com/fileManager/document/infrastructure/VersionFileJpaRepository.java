package com.fileManager.document.infrastructure;

import com.fileManager.document.domain.VersionFile;
import com.fileManager.document.domain.VersionFileRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VersionFileJpaRepository extends JpaRepository<VersionFile, UUID>, VersionFileRepository {
    @Query("select count(f) from VersionFile f where f.file.id = :id or f.revisionFile.id = :id")
    long countReferences(UUID id);
}
