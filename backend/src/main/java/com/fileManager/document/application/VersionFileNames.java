package com.fileManager.document.application;

import com.fileManager.document.domain.Document;
import com.fileManager.document.domain.DocumentVersion;
import com.fileManager.document.domain.ReviewRound;
import com.fileManager.document.domain.VersionFile;
import com.fileManager.file.application.StoredFileService;
import com.fileManager.file.domain.StorageLayout;
import com.fileManager.file.domain.StorageLayout.VersionFileKind;
import com.fileManager.file.domain.StoredFile;
import org.springframework.stereotype.Component;

/**
 * 버전 파일 이름을 현재 문서 제목·버전 라벨에 맞춘다(카테고리 이름 변경, 분류 지정, 작업중 버전 이름 변경).
 * 파일 이름은 그 파일을 올린 버전을 따른다: v1은 원본을 포함한 모든 파일을 소유하고, v2부터는 수정본·대조표만
 * 소유한다. v2 이상의 원본은 직전 제출본과 함께 쓰는 행이라 직전 버전의 이름을 유지한다.
 * 검토본은 대상 파일과 무관하게 그 회차의 버전이 소유한다(올린 사람 = 검토자).
 * 이름 규칙은 StorageLayout.versionFileName 한 곳에 있다.
 */
@Component
class VersionFileNames {
    private final StoredFileService files;

    VersionFileNames(StoredFileService files) {
        this.files = files;
    }

    /** 이 버전이 소유한 파일의 이름을 맞춘다. */
    void renameOwned(Document doc, DocumentVersion v) {
        for (VersionFile f : v.getFiles()) {
            if (v.getNumber() == 1 || f.getSeq() >= 1) apply(doc, v, f.getFile(), VersionFileKind.FILE);
            if (f.getRevisionFile() != null) apply(doc, v, f.getRevisionFile(), VersionFileKind.REVISION_SHEET);
        }
        for (ReviewRound r : v.getRounds()) {
            if (r.getReviewCopy() != null) apply(doc, v, r.getReviewCopy(), VersionFileKind.REVIEW_COPY);
        }
    }

    /** 문서의 모든 버전이 소유한 파일의 이름을 맞춘다. */
    void renameAll(Document doc) {
        for (DocumentVersion v : doc.getVersions()) renameOwned(doc, v);
    }

    private void apply(Document doc, DocumentVersion v, StoredFile sf, VersionFileKind kind) {
        var n = StorageLayout.versionFileName(doc.getTitle(), v.getLabel(), kind,
                StorageLayout.extension(sf.getFileName()), sf.getUploadedAt(), sf.getUploadedBy());
        files.rename(sf, n.display(), n.stored());
    }
}
