package com.pockt.bill.dto;

import com.pockt.bill.domain.BillPayment;

import java.time.Instant;
import java.util.UUID;

public record BillPaymentResponse(
    UUID id,
    UUID userId,
    UUID walletId,
    String category,
    String billerId,
    String billerName,
    String consumerNumber,
    long amountCents,
    String status,
    String referenceNumber,
    UUID transactionId,
    Instant createdAt
) {
    public static BillPaymentResponse from(BillPayment bp) {
        return new BillPaymentResponse(
            bp.id(),
            bp.userId(),
            bp.walletId(),
            bp.category(),
            bp.billerId(),
            bp.billerName(),
            bp.consumerNumber(),
            bp.amountCents(),
            bp.status(),
            bp.referenceNumber(),
            bp.transactionId(),
            bp.createdAt()
        );
    }
}
