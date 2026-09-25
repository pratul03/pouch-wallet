package com.pockt.wallet.domain;

import java.time.Instant;
import java.util.UUID;

public record Wallet(
    UUID id,
    UUID userId,
    String currency,
    long balance,
    boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {}
