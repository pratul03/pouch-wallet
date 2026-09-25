package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends PocktException {
    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
    }
}
