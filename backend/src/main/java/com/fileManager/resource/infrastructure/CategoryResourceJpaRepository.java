package com.fileManager.resource.infrastructure;

import com.fileManager.resource.domain.CategoryResource;
import com.fileManager.resource.domain.CategoryResourceRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CategoryResourceJpaRepository extends JpaRepository<CategoryResource, UUID>, CategoryResourceRepository {
    @EntityGraph(attributePaths = "file")
    List<CategoryResource> findAllByOrderByCreatedAtAscIdAsc();

    @EntityGraph(attributePaths = "file")
    Optional<CategoryResource> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CategoryResource r where r.id = :id")
    Optional<CategoryResource> findLockedById(UUID id);
}
