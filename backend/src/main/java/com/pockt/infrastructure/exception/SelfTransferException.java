package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

public class SelfTransferException extends PocktException {
    public SelfTransferException() {
        super(ErrorCode.SELF_TRANSFER, HttpStatus.UNPROCESSABLE_ENTITY, "Sender and receiver cannot be the same.");
    }
}
