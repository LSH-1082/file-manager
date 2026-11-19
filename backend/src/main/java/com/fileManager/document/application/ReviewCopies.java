package com.fileManager.document.application;

import com.fileManager.document.domain.Document;
import com.fileManager.document.domain.DocumentRepository;
import com.fileManager.document.domain.DocumentVersion;
import com.fileManager.file.application.StoredFileService;
import com.fileManager.file.domain.FileUpload;
import com.fileManager.file.domain.StorageLayout;
import com.fileManager.file.domain.StorageLayout.VersionFileKind;
import com.fileManager.file.domain.StoredFile;
import com.fileManager.global.util.IsoTimes;
import java.util.Collection;
import org.springframework.stereotype.Component;

/** 거절 검토본의 저장과 삭제. 대상이 수정본 n이면 그 edit 폴더에, 원본이면 버전 폴더에 둔다(이름 규칙은 StorageLayout). */
@Component
class ReviewCopies {
    private final StoredFileService files;
    private final DocumentRepository documents;

    ReviewCopies(StoredFileService files, DocumentRepository documents) {
        this.files = files;
        this.documents = documents;
    }

    /** 검사를 모두 통과한 검토본을 저장하고 행을 돌려준다. */
    StoredFile store(Document doc, DocumentVersion v, FileUpload file, String reviewer) {
        var target = v.current();
        String dir;
        if (target.getSeq() >= 1) {
            String key = target.getFile().getStorageKey();
            dir = key.substring(0, key.lastIndexOf('/'));
        } else {
            dir = StorageLayout.join(doc.getDirName(), v.getDirName());
        }
        var now = IsoTimes.now();
        var n = StorageLayout.versionFileName(doc.getTitle(), v.getLabel(), VersionFileKind.REVIEW_COPY,
                StorageLayout.extension(StoredFileService.baseName(file.originalName())), now, reviewer);
        return files.insert(file, reviewer, dir, n.display(), n.stored(), now);
    }

    /** 회차 삭제·링크 비우기를 먼저 DB에 반영한 뒤 검토본 행을 지우고 커밋 뒤 본문도 지운다. */
    void delete(Collection<StoredFile> copies) {
        if (copies.isEmpty()) return;
        documents.flush();
        files.delete(copies);
    }
}
