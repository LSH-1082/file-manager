package com.fileManager.file.domain;

import java.io.IOException;
import java.io.InputStream;

/** 업로드된 파일 한 건(웹 타입과 무관). content.open()은 본문을 처음부터 읽는 새 스트림을 돌려준다. */
public record FileUpload(String originalName, String contentType, long size, Content content) {
    @FunctionalInterface
    public interface Content {
        InputStream open() throws IOException;
    }
}
