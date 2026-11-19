package com.fileManager.resource.application;

import com.fileManager.file.application.StoredFileAlias;
import com.fileManager.file.domain.StoredFile;
import com.fileManager.resource.domain.CategoryResource;
import com.fileManager.resource.domain.CategoryResourceRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 자료 id로 다운로드하면 그 자료의 파일을 돌려준다(docs/api 4절: 자료 id를 먼저 본다). */
@Component
public class ResourceFileAlias implements StoredFileAlias {
    private final CategoryResourceRepository resources;

    ResourceFileAlias(CategoryResourceRepository resources) {
        this.resources = resources;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredFile> resolve(UUID id) {
        // findById는 file을 함께 읽는다(infrastructure의 EntityGraph). 트랜잭션 밖에서도 파일 필드를 쓸 수 있다
        return resources.findById(id).map(CategoryResource::getFile);
    }
}
