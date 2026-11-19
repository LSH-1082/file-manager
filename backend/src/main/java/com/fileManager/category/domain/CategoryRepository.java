package com.fileManager.category.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository {
    List<Category> findAllByOrderByCreatedAtAscIdAsc();

    Optional<Category> findById(UUID id);

    /** SELECT ... FOR UPDATE. 잠금 순서는 categories -> documents(docs/db 5절). */
    Optional<Category> findLockedById(UUID id);

    boolean existsByName(String name);

    Category save(Category c);

    void delete(Category c);

    void flush();
}
