package com.fileManager.global.util;

import com.fileManager.global.error.ApiException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/** yyyy-MM-dd'T'HH:mm:ss.SSS'Z' (UTC, 밀리초 3자리 고정) — docs/api 1절. */
public final class IsoTimes {
    private static final DateTimeFormatter F =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private IsoTimes() {}

    /** DB에 넣는 현재 시각. DATETIME(6) 정밀도에 맞춰 마이크로초로 자른다. */
    public static Instant now() { return Instant.now().truncatedTo(ChronoUnit.MICROS); }

    public static String format(Instant t) { return t == null ? null : F.format(t); }

    public static Instant parse(String s) {
        try {
            return Instant.parse(s).truncatedTo(ChronoUnit.MICROS);
        } catch (DateTimeParseException | NullPointerException e) {
            throw ApiException.format();
        }
    }
}
