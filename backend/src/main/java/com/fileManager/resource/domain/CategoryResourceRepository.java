package com.fileManager.resource.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryResourceRepository {
    List<CategoryResource> findAllByOrderByCreatedAtAscIdAsc();

    List<CategoryResource> findByCategoryId(UUID categoryId);

    Optional<CategoryResource> findLockedById(UUID id);

    Optional<CategoryResource> findById(UUID id);

    CategoryResource save(CategoryResource r);

    void delete(CategoryResource r);

    void flush();
}
