package com.fileManager.schedule.application;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

/** 발송 문서와 일정의 날짜 입력 해석(docs/api 1절 날짜 전용 형식). 앞뒤 공백을 지우지 않는다. */
final class ScheduleDates {
    static final String FORMAT_MESSAGE = "날짜 형식이 올바르지 않습니다.";

    private static final Pattern DATE_SHAPE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private ScheduleDates() {}

    /** YYYY-MM-DD(ASCII 숫자 4-2-2자리)이고 실제 있는 날짜면 그 날짜, 아니면 null. */
    static LocalDate parse(String raw) {
        if (!DATE_SHAPE.matcher(raw).matches()) return null;
        try {
            return LocalDate.parse(raw, DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
