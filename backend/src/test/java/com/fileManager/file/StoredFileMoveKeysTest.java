package com.fileManager.file;

import com.fileManager.ApiTestSupport;
import com.fileManager.file.application.StoredFileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 폴더 키 이동이 보충 문자(이모지) 폴더 이름에서도 키 뒷부분을 그대로 두는지 확인한다. */
class StoredFileMoveKeysTest extends ApiTestSupport {
    @Autowired StoredFileService service;

    private void insert(String key) {
        jdbc.sql("INSERT INTO stored_files (id, storage_key, file_name, mime_type, size_bytes, uploaded_at) "
                        + "VALUES (:id, :k, 'a.txt', 'text/plain', 1, CURRENT_TIMESTAMP(6))")
                .param("id", newId()).param("k", key).update();
    }

    private String key(String like) {
        return jdbc.sql("SELECT storage_key FROM stored_files WHERE storage_key LIKE :p").param("p", like)
                .query(String.class).single();
    }

    @Test
    void movesKeysUnderFolderWithSupplementaryCharacters() {
        insert("계약😀/v1/a.txt");
        insert("계약😀x/v1/b.txt"); // 이름만 비슷한 다른 폴더는 건드리지 않는다
        service.moveKeys("계약😀", "계약🎉🎉");
        assertEquals("계약🎉🎉/v1/a.txt", key("계약🎉🎉/%"));
        assertEquals("계약😀x/v1/b.txt", key("계약😀x/%"));
    }
}
