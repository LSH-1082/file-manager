package com.fileManager.schedule.presentation;

import com.fileManager.global.error.ApiException;
import tools.jackson.databind.JsonNode;

/**
 * 발송 문서·일정·보기 설정 요청 본문의 형식 검사(docs/api 2.2). 값의 타입을 직접 검사한다.
 * Jackson의 문자열 변환(숫자 → 문자열)이나 LocalDate 역직렬화에 맡기면 2.2의 형식 오류와 8.23~8.30의 문구가 섞이기 때문이다.
 */
final class ScheduleBodies {
    private ScheduleBodies() {}

    static void requireObject(JsonNode body) {
        if (body == null || !body.isObject()) throw ApiException.format();
    }

    /** 키가 없거나 null이면 null, 문자열이면 그 값, 다른 타입(숫자·불리언·객체·배열)이면 형식 오류. */
    static String optionalString(JsonNode body, String key) {
        JsonNode v = body.get(key);
        if (v == null || v.isNull()) return null;
        if (!v.isString()) throw ApiException.format();
        return v.asString();
    }
}
