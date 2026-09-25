package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class BankAccountNotFoundException extends PocktException {
    public BankAccountNotFoundException(UUID id) {
        super(ErrorCode.BANK_ACCOUNT_NOT_FOUND, HttpStatus.NOT_FOUND, "Bank account not found: " + id);
    }

    public BankAccountNotFoundException(String message) {
        super(ErrorCode.BANK_ACCOUNT_NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }
}
