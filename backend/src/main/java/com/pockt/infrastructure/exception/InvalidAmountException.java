package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

public class InvalidAmountException extends PocktException {
    public InvalidAmountException(String message) {
        super(ErrorCode.INVALID_AMOUNT, HttpStatus.BAD_REQUEST, message);
    }

    public InvalidAmountException() {
        super(ErrorCode.INVALID_AMOUNT, HttpStatus.BAD_REQUEST, "Amount must be greater than zero.");
    }
}
