package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

public class OtpExpiredException extends PocktException {
    public OtpExpiredException() {
        super(ErrorCode.OTP_EXPIRED, HttpStatus.BAD_REQUEST, "OTP has expired. Please request a new one.");
    }
}
