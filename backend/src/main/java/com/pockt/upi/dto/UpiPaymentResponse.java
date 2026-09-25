package com.pockt.upi.dto;

import java.time.Instant;
import java.util.UUID;

public record UpiPaymentResponse(
    UUID transactionId,
    String senderVpa,
    String receiverVpa,
    String receiverName,
    long amount,
    String formattedAmount,
    String currency,
    String status,
    String note,
    Instant timestamp
) {}
