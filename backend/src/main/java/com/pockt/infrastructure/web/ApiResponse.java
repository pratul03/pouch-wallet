package com.pockt.infrastructure.web;

import org.slf4j.MDC;

import java.time.Instant;
import java.util.UUID;

public record ApiResponse<T>(
    boolean success,
    T data,
    ApiError error,
    String requestId,
    Instant timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, currentRequestId(), Instant.now());
    }

    public static <T> ApiResponse<T> error(ApiError error) {
        return new ApiResponse<>(false, null, error, currentRequestId(), Instant.now());
    }

    public static <T> ApiResponse<T> error(ApiError error, String requestId) {
        return new ApiResponse<>(false, null, error, requestId != null ? requestId : currentRequestId(), Instant.now());
    }

    private static String currentRequestId() {
        String reqId = MDC.get("requestId");
        return reqId != null ? reqId : UUID.randomUUID().toString();
    }
}
