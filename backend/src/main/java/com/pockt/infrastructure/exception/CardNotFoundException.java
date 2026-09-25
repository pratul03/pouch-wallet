package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class CardNotFoundException extends PocktException {
    public CardNotFoundException(UUID cardId) {
        super(ErrorCode.CARD_NOT_FOUND, HttpStatus.NOT_FOUND, "Card not found: " + cardId);
    }

    public CardNotFoundException(String message) {
        super(ErrorCode.CARD_NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }
}
