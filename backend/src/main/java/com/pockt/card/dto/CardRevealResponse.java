package com.pockt.card.dto;

import java.util.UUID;

public record CardRevealResponse(
    UUID id,
    String cardNumberFull,
    String expiryFormatted,
    String cvv,
    String cardHolderName,
    String cardType,
    String cardNetwork
) {}
