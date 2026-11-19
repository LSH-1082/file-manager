package com.fileManager.document.domain;

import java.util.UUID;

public interface VersionFileRepository {
    /** 이 stored_files 행을 파일이나 수정대조표로 쓰는 버전 파일의 수. 0이면 아무도 쓰지 않는다. */
    long countReferences(UUID storedFileId);
}
