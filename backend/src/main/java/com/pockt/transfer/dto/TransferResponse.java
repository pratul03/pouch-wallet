package com.pockt.transfer.dto;

import com.pockt.infrastructure.util.MoneyUtils;

import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
    UUID transactionId,
    String status,
    long amount,
    String currency,
    String formattedAmount,
    String receiverName,
    long senderBalanceAfter,
    Instant createdAt
) {
    public static TransferResponse of(UUID transactionId,
                                      String status,
                                      long amount,
                                      String currency,
                                      String receiverName,
                                      long senderBalanceAfter,
                                      Instant createdAt) {
        return new TransferResponse(
                transactionId,
                status,
                amount,
                currency,
                MoneyUtils.format(amount, currency),
                receiverName,
                senderBalanceAfter,
                createdAt
        );
    }
}
