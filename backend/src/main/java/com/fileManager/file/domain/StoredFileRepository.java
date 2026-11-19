package com.fileManager.file.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoredFileRepository {
    StoredFile save(StoredFile f);

    Optional<StoredFile> findById(UUID id);

    void deleteAll(Iterable<? extends StoredFile> files);

    void flush();

    /** 키가 pattern('{폴더}/%'를 '!'로 이스케이프한 값)에 맞는 행. 키 앞부분은 DB 문자열 함수 없이 자바에서 바꾼다. */
    List<StoredFile> findByKeyPattern(String pattern);
}
