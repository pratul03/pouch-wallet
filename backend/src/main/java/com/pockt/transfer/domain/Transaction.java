package com.pockt.transfer.domain;

import java.time.Instant;
import java.util.UUID;

public record Transaction(
    UUID id,
    String idempotencyKey,
    UUID senderWalletId,
    UUID receiverWalletId,
    long amount,
    String currency,
    TransactionStatus status,
    String description,
    String failureReason,
    Instant createdAt,
    Instant updatedAt
) {}
