package com.pockt.receipt.dto;

import java.time.Instant;
import java.util.UUID;

public record ReceiptResponse(
    String receiptId,
    String referenceNumber,
    UUID transactionId,
    String receiptType, // P2P_TRANSFER, BILL_PAYMENT, BANK_DEPOSIT, CARD_CHARGE
    Instant timestamp,
    long amountCents,
    String formattedAmount,
    String currency,
    String status,
    String senderName,
    String senderIdentifier,
    String receiverName,
    String receiverIdentifier,
    String category,
    String notes,
    String verificationHash,
    String htmlReceipt
) {}
