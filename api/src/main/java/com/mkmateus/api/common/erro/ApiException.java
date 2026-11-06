package com.mkmateus.api.common.erro;

import java.util.Map;

public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> extras;

    public ApiException(ErrorCode code) {
        this(code, code.titulo(), Map.of());
    }

    public ApiException(ErrorCode code, String detail) {
        this(code, detail, Map.of());
    }

    public ApiException(ErrorCode code, String detail, Map<String, Object> extras) {
        super(detail);
        this.code = code;
        this.extras = extras;
    }

    public ErrorCode code() { return code; }
    public Map<String, Object> extras() { return extras; }
}
