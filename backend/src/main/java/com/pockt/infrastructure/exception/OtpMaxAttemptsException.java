package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

public class OtpMaxAttemptsException extends PocktException {
    public OtpMaxAttemptsException() {
        super(ErrorCode.OTP_MAX_ATTEMPTS, HttpStatus.TOO_MANY_REQUESTS, "Maximum OTP verification attempts exceeded. Please wait before trying again.");
    }
}
