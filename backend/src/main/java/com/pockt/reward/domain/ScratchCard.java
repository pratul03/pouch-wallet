package com.pockt.reward.domain;

import java.time.Instant;
import java.util.UUID;

public record ScratchCard(
    UUID id,
    UUID userId,
    String title,
    String description,
    long rewardAmountCents,
    boolean isScratched,
    Instant scratchedAt,
    UUID transactionId,
    Instant createdAt
) {}
