package com.fileManager.file.presentation;

import com.fileManager.file.domain.FileUpload;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** 웹 업로드 파트를 도메인의 FileUpload로 바꾼다. 검사는 하지 않는다. */
public final class MultipartUploads {
    private MultipartUploads() {}

    public static FileUpload of(MultipartFile f) {
        if (f == null) return null;
        return new FileUpload(f.getOriginalFilename(), f.getContentType(), f.getSize(), f::getInputStream);
    }

    public static List<FileUpload> of(List<MultipartFile> parts) {
        if (parts == null) return null;
        return parts.stream().map(MultipartUploads::of).toList();
    }
}
