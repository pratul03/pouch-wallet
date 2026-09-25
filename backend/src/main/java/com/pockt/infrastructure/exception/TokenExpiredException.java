package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

public class TokenExpiredException extends PocktException {
    public TokenExpiredException() {
        super(ErrorCode.TOKEN_EXPIRED, HttpStatus.UNAUTHORIZED, "Authentication token has expired.");
    }
}
