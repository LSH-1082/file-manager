package com.fileManager.category.infrastructure;

import com.fileManager.category.domain.Category;
import com.fileManager.category.domain.CategoryRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CategoryJpaRepository extends JpaRepository<Category, UUID>, CategoryRepository {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Category c where c.id = :id")
    Optional<Category> findLockedById(UUID id);
}
