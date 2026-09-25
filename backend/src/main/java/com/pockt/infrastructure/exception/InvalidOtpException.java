package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

public class InvalidOtpException extends PocktException {
    public InvalidOtpException() {
        super(ErrorCode.INVALID_OTP, HttpStatus.BAD_REQUEST, "Invalid OTP provided.");
    }
}
