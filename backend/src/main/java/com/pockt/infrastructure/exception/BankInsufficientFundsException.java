package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

public class BankInsufficientFundsException extends PocktException {
    public BankInsufficientFundsException(String message) {
        super(ErrorCode.BANK_INSUFFICIENT_FUNDS, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
