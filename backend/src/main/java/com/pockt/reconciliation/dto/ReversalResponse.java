package com.pockt.reconciliation.dto;

import java.time.Instant;
import java.util.UUID;

public record ReversalResponse(
    UUID transactionId,
    String originalStatus,
    String newStatus,
    long amountCents,
    String reason,
    Instant reversedAt
) {}
