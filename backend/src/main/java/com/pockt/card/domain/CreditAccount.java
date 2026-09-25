package com.pockt.card.domain;

import java.time.Instant;
import java.util.UUID;

public record CreditAccount(
    UUID id,
    UUID userId,
    UUID cardId,
    long totalCreditLimit,
    long availableCreditLimit,
    long currentBillAmount,
    String status,
    Instant createdAt,
    Instant updatedAt
) {}
