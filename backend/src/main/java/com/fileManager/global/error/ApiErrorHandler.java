package com.fileManager.global.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** 모든 오류를 {code, message} 본문으로 바꾼다(docs/api 2절). */
@RestControllerAdvice
public class ApiErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    public record Body(String code, String message) {}

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Body> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(new Body(e.code(), e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Body> tooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new Body("PAYLOAD_TOO_LARGE", ApiException.TOO_LARGE));
    }

    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class})
    ResponseEntity<Body> race(RuntimeException e) {
        log.warn("DB 제약 위반 또는 잠금 충돌", e);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new Body("CONFLICT", ApiException.RACE));
    }

    @ExceptionHandler({org.springframework.web.multipart.MultipartException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    ResponseEntity<Body> format() {
        return ResponseEntity.badRequest().body(new Body("INVALID_INPUT", ApiException.FORMAT));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Body> internal(Exception e) {
        // 없는 경로, 허용 안 된 메서드, 파트·파라미터 누락 등 Spring이 상태를 아는 오류
        if (e instanceof ErrorResponse er) {
            HttpStatusCode s = er.getStatusCode();
            if (s.value() == 404) return ResponseEntity.status(s).body(new Body("NOT_FOUND", "요청한 경로를 찾을 수 없습니다."));
            // 명세 2절: INVALID_INPUT은 400 전용이라 405·415 등도 400으로 통일한다(DECISIONS D-13)
            if (s.is4xxClientError()) return ResponseEntity.badRequest().body(new Body("INVALID_INPUT", ApiException.FORMAT));
        }
        log.error("처리하지 못한 오류", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new Body("INTERNAL_ERROR", ApiException.INTERNAL));
    }
}
