package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

public class CreditLimitExceededException extends PocktException {
    public CreditLimitExceededException(String message) {
        super(ErrorCode.CREDIT_LIMIT_EXCEEDED, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
