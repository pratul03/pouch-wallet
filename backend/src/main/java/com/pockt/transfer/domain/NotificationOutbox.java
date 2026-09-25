package com.pockt.transfer.domain;

import java.time.Instant;
import java.util.UUID;

public record NotificationOutbox(
    UUID id,
    UUID userId,
    String type,
    String payload,
    String status,
    int attempts,
    Instant nextRetry,
    Instant createdAt
) {}
