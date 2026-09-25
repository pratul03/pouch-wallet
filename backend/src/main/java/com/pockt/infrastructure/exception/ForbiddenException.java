package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends PocktException {
    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, message);
    }

    public ForbiddenException() {
        super(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Access to the requested resource is forbidden.");
    }
}
