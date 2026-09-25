package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class UserNotFoundException extends PocktException {
    public UserNotFoundException(UUID userId) {
        super(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found: " + userId);
    }

    public UserNotFoundException(String message) {
        super(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }
}
