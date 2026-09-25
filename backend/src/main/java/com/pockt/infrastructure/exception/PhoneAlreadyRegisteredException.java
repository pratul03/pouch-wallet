package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

public class PhoneAlreadyRegisteredException extends PocktException {
    public PhoneAlreadyRegisteredException(String phone) {
        super(ErrorCode.PHONE_ALREADY_REGISTERED, HttpStatus.CONFLICT, "Phone number is already registered: " + phone);
    }
}
