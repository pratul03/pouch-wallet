package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

public class ReceiverNotFoundException extends PocktException {
    public ReceiverNotFoundException(String phone) {
        super(ErrorCode.RECEIVER_NOT_FOUND, HttpStatus.NOT_FOUND, "Receiver phone not registered: " + phone);
    }
}
