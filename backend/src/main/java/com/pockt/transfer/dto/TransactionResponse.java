package com.pockt.transfer.dto;

import com.pockt.infrastructure.util.MoneyUtils;

import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
    UUID id,
    String type,
    long amount,
    String currency,
    String formattedAmount,
    String counterpartyName,
    String description,
    String status,
    Instant createdAt
) {
    public static TransactionResponse of(UUID id,
                                         String type,
                                         long amount,
                                         String currency,
                                         String counterpartyName,
                                         String description,
                                         String status,
                                         Instant createdAt) {
        String sign = "DEBIT".equals(type) ? "-" : "+";
        String formatted = sign + MoneyUtils.format(amount, currency);
        return new TransactionResponse(
                id,
                type,
                amount,
                currency,
                formatted,
                counterpartyName,
                description,
                status,
                createdAt
        );
    }
}
