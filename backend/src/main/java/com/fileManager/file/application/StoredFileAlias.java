package com.fileManager.file.application;

import com.fileManager.file.domain.StoredFile;
import java.util.Optional;
import java.util.UUID;

/**
 * 다운로드 id로 쓰이는 다른 도메인의 id(예: 양식·참고자료 id)를 파일로 바꾼다.
 * file 도메인이 다른 도메인을 import하지 않도록, 그쪽이 이 인터페이스를 구현한다.
 */
public interface StoredFileAlias {
    Optional<StoredFile> resolve(UUID id);
}
