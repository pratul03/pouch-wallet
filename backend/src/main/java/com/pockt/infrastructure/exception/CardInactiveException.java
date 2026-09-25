package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

public class CardInactiveException extends PocktException {
    public CardInactiveException(String message) {
        super(ErrorCode.CARD_INACTIVE, HttpStatus.BAD_REQUEST, message);
    }
}
