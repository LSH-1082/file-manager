package com.fileManager.document.application;

import com.fileManager.category.domain.CategoryDeletingEvent;
import com.fileManager.category.domain.CategoryRenamedEvent;
import com.fileManager.document.domain.Document;
import com.fileManager.document.domain.DocumentRepository;
import com.fileManager.document.domain.DocumentVersion;
import com.fileManager.file.application.StoredFileService;
import com.fileManager.file.domain.FileStorage;
import com.fileManager.file.domain.StorageLayout;
import java.util.Set;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 카테고리 수정·삭제를 같은 트랜잭션 안에서 따라간다(문서 제목·폴더, 이름 변경 때 버전 파일 이름, 미분류로 옮기기). */
@Component
public class DocumentCategoryListener {
    private final DocumentRepository documents;
    private final StoredFileService files;
    private final FileStorage storage;
    private final VersionFileNames names;

    DocumentCategoryListener(DocumentRepository documents, StoredFileService files, FileStorage storage,
                             VersionFileNames names) {
        this.documents = documents;
        this.files = files;
        this.storage = storage;
        this.names = names;
    }

    /** 이름이 그대로여도 오는 이벤트다. 옛 동작대로 문서 제목과 updatedAt을 항상 갱신한다. */
    @EventListener
    public void onCategoryRenamed(CategoryRenamedEvent e) {
        documents.findLockedByCategoryId(e.categoryId()).ifPresent(d -> {
            d.followCategory(e.newName(), e.newDir());
            names.renameAll(d);
        });
    }

    /** 카테고리의 문서는 지워지지 않고 미분류가 된다: 버전 폴더를 _미분류/{제목} 아래로 옮긴다. */
    @EventListener
    public void onCategoryDeleting(CategoryDeletingEvent e) {
        Document d = documents.findLockedByCategoryId(e.categoryId()).orElse(null);
        if (d == null) return;
        String docDir = StorageLayout.join(StorageLayout.UNCLASSIFIED_DIR,
                storage.freeName(StorageLayout.UNCLASSIFIED_DIR, d.getTitle(), Set.of()));
        for (DocumentVersion v : d.getVersions()) {
            String from = StorageLayout.join(d.getDirName(), v.getDirName());
            String to = StorageLayout.join(docDir, v.getDirName());
            storage.moveDir(from, to);
            files.moveKeys(from, to);
        }
        d.unclassify(docDir);
    }
}
