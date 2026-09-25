package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

public abstract class PocktException extends RuntimeException {
    private final ErrorCode code;
    private final HttpStatus status;

    public PocktException(ErrorCode code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public ErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
