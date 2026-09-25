package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

public class DuplicateTransferException extends PocktException {
    public DuplicateTransferException(String idempotencyKey) {
        super(ErrorCode.DUPLICATE_TRANSFER, HttpStatus.CONFLICT, "Transfer with idempotency key already processed: " + idempotencyKey);
    }
}
