package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

public class BankAccountAlreadyExistsException extends PocktException {
    public BankAccountAlreadyExistsException(String message) {
        super(ErrorCode.BANK_ACCOUNT_EXISTS, HttpStatus.CONFLICT, message);
    }
}
