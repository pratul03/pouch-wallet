package com.pockt.bank.dto;

import com.pockt.bank.domain.BankTransaction;
import com.pockt.infrastructure.util.MoneyUtils;

import java.time.Instant;
import java.util.UUID;

public record BankTransactionResponse(
    UUID id,
    UUID bankAccountId,
    UUID walletId,
    String type,
    long amount,
    String formattedAmount,
    String currency,
    String status,
    String referenceNumber,
    Instant createdAt
) {
    public static BankTransactionResponse fromDomain(BankTransaction tx) {
        return new BankTransactionResponse(
                tx.id(),
                tx.bankAccountId(),
                tx.walletId(),
                tx.type(),
                tx.amount(),
                MoneyUtils.format(tx.amount(), tx.currency()),
                tx.currency(),
                tx.status(),
                tx.referenceNumber(),
                tx.createdAt()
        );
    }
}
