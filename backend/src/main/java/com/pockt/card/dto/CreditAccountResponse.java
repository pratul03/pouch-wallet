package com.pockt.card.dto;

import com.pockt.card.domain.CreditAccount;
import com.pockt.infrastructure.util.MoneyUtils;

import java.time.Instant;
import java.util.UUID;

public record CreditAccountResponse(
    UUID id,
    UUID userId,
    UUID cardId,
    long totalCreditLimit,
    String formattedTotalCreditLimit,
    long availableCreditLimit,
    String formattedAvailableCreditLimit,
    long currentBillAmount,
    String formattedCurrentBillAmount,
    String status,
    Instant createdAt
) {
    public static CreditAccountResponse fromDomain(CreditAccount acc) {
        return new CreditAccountResponse(
                acc.id(),
                acc.userId(),
                acc.cardId(),
                acc.totalCreditLimit(),
                MoneyUtils.format(acc.totalCreditLimit(), "USD"),
                acc.availableCreditLimit(),
                MoneyUtils.format(acc.availableCreditLimit(), "USD"),
                acc.currentBillAmount(),
                MoneyUtils.format(acc.currentBillAmount(), "USD"),
                acc.status(),
                acc.createdAt()
        );
    }
}
