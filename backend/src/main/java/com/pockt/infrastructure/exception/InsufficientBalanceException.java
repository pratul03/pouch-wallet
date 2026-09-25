package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

public class InsufficientBalanceException extends PocktException {
    public InsufficientBalanceException() {
        super(ErrorCode.INSUFFICIENT_BALANCE, HttpStatus.UNPROCESSABLE_ENTITY, "Your wallet balance is too low for this transfer.");
    }

    public InsufficientBalanceException(String message) {
        super(ErrorCode.INSUFFICIENT_BALANCE, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
