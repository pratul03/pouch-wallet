package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

public class InvalidTokenException extends PocktException {
    public InvalidTokenException(String message) {
        super(ErrorCode.TOKEN_INVALID, HttpStatus.UNAUTHORIZED, message);
    }

    public InvalidTokenException() {
        super(ErrorCode.TOKEN_INVALID, HttpStatus.UNAUTHORIZED, "Invalid authentication token.");
    }
}
