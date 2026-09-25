package com.pockt.request.dto;

import com.pockt.request.domain.PaymentRequest;

import java.time.Instant;
import java.util.UUID;

public record PaymentRequestResponse(
    UUID id,
    UUID requesterUserId,
    UUID payerUserId,
    String payerPhone,
    String payerVpa,
    long amountCents,
    String note,
    UUID splitGroupId,
    String status,
    UUID transactionId,
    Instant expiresAt,
    Instant createdAt
) {
    public static PaymentRequestResponse from(PaymentRequest r) {
        return new PaymentRequestResponse(
            r.id(),
            r.requesterUserId(),
            r.payerUserId(),
            r.payerPhone(),
            r.payerVpa(),
            r.amountCents(),
            r.note(),
            r.splitGroupId(),
            r.status(),
            r.transactionId(),
            r.expiresAt(),
            r.createdAt()
        );
    }
}
