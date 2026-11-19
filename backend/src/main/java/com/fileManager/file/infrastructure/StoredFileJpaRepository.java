package com.fileManager.file.infrastructure;

import com.fileManager.file.domain.StoredFile;
import com.fileManager.file.domain.StoredFileRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StoredFileJpaRepository extends JpaRepository<StoredFile, UUID>, StoredFileRepository {
    @Query("select s from StoredFile s where s.storageKey like :pattern escape '!'")
    List<StoredFile> findByKeyPattern(String pattern);
}
