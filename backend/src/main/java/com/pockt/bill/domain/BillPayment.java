package com.pockt.bill.domain;

import java.time.Instant;
import java.util.UUID;

public record BillPayment(
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
    Instant createdAt,
    Instant updatedAt
) {}
