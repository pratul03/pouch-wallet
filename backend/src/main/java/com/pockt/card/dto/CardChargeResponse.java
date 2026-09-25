package com.pockt.card.dto;

import java.time.Instant;
import java.util.UUID;

public record CardChargeResponse(
    UUID transactionId,
    UUID cardId,
    String cardType,
    String cardNumberMasked,
    long amount,
    String formattedAmount,
    String merchantName,
    String status,
    String message,
    Instant timestamp
) {}
