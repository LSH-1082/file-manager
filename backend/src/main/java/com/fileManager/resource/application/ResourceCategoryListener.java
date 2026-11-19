package com.fileManager.resource.application;

import com.fileManager.category.domain.CategoryDeletingEvent;
import com.fileManager.file.application.StoredFileService;
import com.fileManager.file.domain.StoredFile;
import com.fileManager.resource.domain.CategoryResource;
import com.fileManager.resource.domain.CategoryResourceRepository;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 카테고리를 지우면 그 양식·참고자료와 파일을 함께 지운다(같은 트랜잭션). 카테고리 폴더는 category가 커밋 뒤에 지운다. */
@Component
public class ResourceCategoryListener {
    private final CategoryResourceRepository resources;
    private final StoredFileService files;

    ResourceCategoryListener(CategoryResourceRepository resources, StoredFileService files) {
        this.resources = resources;
        this.files = files;
    }

    @EventListener
    public void onCategoryDeleting(CategoryDeletingEvent e) {
        List<CategoryResource> own = resources.findByCategoryId(e.categoryId());
        if (own.isEmpty()) return;
        List<StoredFile> stored = own.stream().map(CategoryResource::getFile).toList();
        own.forEach(resources::delete);
        resources.flush();
        files.delete(stored);
    }
}
