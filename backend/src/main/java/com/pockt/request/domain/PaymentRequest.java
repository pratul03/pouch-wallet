package com.pockt.request.domain;

import java.time.Instant;
import java.util.UUID;

public record PaymentRequest(
    UUID id,
    UUID requesterUserId,
    UUID payerUserId,
    String payerPhone,
    String payerVpa,
    long amountCents,
    String note,
    UUID splitGroupId,
    String status, // PENDING, ACCEPTED, DECLINED, EXPIRED, CANCELLED
    UUID transactionId,
    Instant expiresAt,
    Instant createdAt,
    Instant updatedAt
) {}
