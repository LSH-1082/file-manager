package com.fileManager.global.error;

public class ApiException extends RuntimeException {
    public static final String FORMAT = "요청 형식이 올바르지 않습니다.";
    public static final String RACE = "다른 변경과 겹쳤습니다. 다시 시도하세요.";
    public static final String TOO_LARGE = "파일이 너무 큽니다. 100MB까지 올릴 수 있습니다.";
    public static final String INTERNAL = "서버 오류가 발생했습니다.";

    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }

    public static ApiException badRequest(String message) {
        return new ApiException(400, "INVALID_INPUT", message);
    }
    public static ApiException format() { return badRequest(FORMAT); }
    public static ApiException notFound(String message) {
        return new ApiException(404, "NOT_FOUND", message);
    }
    public static ApiException conflict(String message) {
        return new ApiException(409, "CONFLICT", message);
    }
}
