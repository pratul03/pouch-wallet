package com.pockt.card.domain;

import java.time.Instant;
import java.util.UUID;

public record Card(
    UUID id,
    UUID userId,
    UUID walletId,
    String cardType,      // DEBIT | CREDIT
    String cardNetwork,   // RUPAY | VISA | MASTERCARD
    String cardNumberMasked,
    String cardNumberFull,
    int expiryMonth,
    int expiryYear,
    String cvvPlain,
    String cardHolderName,
    long dailyLimitCents,
    boolean onlineEnabled,
    String status,        // ACTIVE | FROZEN | BLOCKED
    Instant createdAt,
    Instant updatedAt
) {}
