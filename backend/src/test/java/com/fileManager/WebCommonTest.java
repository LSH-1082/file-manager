package com.fileManager;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import com.fileManager.global.util.Texts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WebCommonTest extends ApiTestSupport {
    private Response preflight(String origin) {
        return call(HttpMethod.OPTIONS, "/api/categories", null, null,
                Map.of("Origin", origin, "Access-Control-Request-Method", "POST"));
    }

    @Test
    void corsAllowsDevServerOrigin() {
        // when
        Response r = preflight("http://localhost:5173");

        // then
        assertEquals(200, r.status());
        assertEquals("http://localhost:5173", r.headers().getFirst("Access-Control-Allow-Origin"));
    }

    @Test
    void corsRejectsOtherOrigin() {
        // when
        Response r = preflight("http://evil.example");

        // then
        assertNull(r.headers().getFirst("Access-Control-Allow-Origin"));
    }

    @Test
    void unknownPathIs404WithCode() {
        // when
        Response r = get("/api/nope");

        // then
        assertEquals(404, r.status());
        assertEquals("NOT_FOUND", r.json().get("code").asString());
    }

    private static final String FORMAT = "요청 형식이 올바르지 않습니다.";

    @Test
    void unsupportedMediaTypeIs400() {
        // when
        Response r = call(HttpMethod.POST, "/api/_probe/json", MediaType.TEXT_PLAIN, "x", Map.of());

        // then
        assertError(r, 400, "INVALID_INPUT", FORMAT);
    }

    @Test
    void methodNotAllowedIs400() {
        // when & then
        assertError(get("/api/_probe/json"), 400, "INVALID_INPUT", FORMAT);
    }

    @Test
    void missingPartIs400() {
        // when & then
        assertError(multipart(HttpMethod.POST, "/api/_probe/part", Map.of("a", "b"), null), 400, "INVALID_INPUT", FORMAT);
    }

    @Test
    void trimMatchesJs() {
        // when & then
        assertEquals("a", Texts.trim("\u00A0\uFEFF a \u3000\u2028"));
        assertEquals("", Texts.trim("\u00A0 \uFEFF"));
        assertEquals("a b", Texts.trim("\ta b\n"));
        assertNull(Texts.trim(null));
    }
}
