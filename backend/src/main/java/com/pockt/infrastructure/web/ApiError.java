package com.pockt.infrastructure.web;

import com.pockt.infrastructure.exception.ErrorCode;

public record ApiError(
    ErrorCode code,
    String message,
    String field
) {
    public static ApiError of(ErrorCode code, String message) {
        return new ApiError(code, message, null);
    }

    public static ApiError of(ErrorCode code, String message, String field) {
        return new ApiError(code, message, field);
    }
}
