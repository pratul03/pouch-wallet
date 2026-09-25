package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

public class BadCredentialsException extends PocktException {
    public BadCredentialsException() {
        super(ErrorCode.BAD_CREDENTIALS, HttpStatus.UNAUTHORIZED, "Invalid phone number or PIN.");
    }
}
