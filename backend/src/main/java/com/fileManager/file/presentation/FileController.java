package com.fileManager.file.presentation;

import com.fileManager.file.application.StoredFileService;
import com.fileManager.file.domain.StoredFile;
import com.fileManager.global.error.ApiException;
import com.fileManager.global.util.Ids;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.util.Map;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FileController {
    private static final String NOT_FOUND = "저장된 파일을 찾을 수 없습니다.";

    private final StoredFileService files;

    FileController(StoredFileService files) {
        this.files = files;
    }

    @GetMapping("/api/files/{id}")
    ResponseEntity<InputStreamResource> download(@PathVariable String id) throws IOException {
        StoredFile f = files.find(Ids.parse(id, NOT_FOUND))
                .orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        InputStreamResource body;
        try {
            body = new InputStreamResource(files.open(f));
        } catch (NoSuchFileException e) {
            throw ApiException.notFound(NOT_FOUND);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, f.getMimeType())
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(f.getSizeBytes()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition(f.downloadName()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    /** 저장 루트의 절대 경로(설정 fileManager.storage-root를 해석한 값). */
    @GetMapping("/api/storage-root")
    Map<String, String> storageRoot() {
        return Map.of("path", files.storageRoot());
    }

    /** RFC 6266: ASCII 대체 이름 + RFC 5987 filename*. */
    public static String disposition(String name) {
        StringBuilder ascii = new StringBuilder();
        for (char c : name.toCharArray()) ascii.append(c > 126 || c < 32 || c == '"' || c == '\\' ? '_' : c);
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }
}
