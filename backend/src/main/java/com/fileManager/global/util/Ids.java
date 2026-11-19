package com.fileManager.global.util;

import com.fileManager.global.error.ApiException;
import java.util.UUID;

public final class Ids {
    private Ids() {}

    /** 경로 id 파싱. UUID가 아니면 그 리소스의 404 문구로 응답한다(docs/api 2.2). */
    public static UUID parse(String raw, String notFoundMessage) {
        UUID id = parseOrNull(raw);
        if (id == null) throw ApiException.notFound(notFoundMessage);
        return id;
    }

    /** UUID가 아니면 null. */
    public static UUID parseOrNull(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
