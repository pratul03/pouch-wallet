package com.pockt.card.dto;

import com.pockt.card.domain.Card;
import com.pockt.infrastructure.util.MoneyUtils;

import java.time.Instant;
import java.util.UUID;

public record CardResponse(
    UUID id,
    UUID userId,
    UUID walletId,
    String cardType,
    String cardNetwork,
    String cardNumberMasked,
    int expiryMonth,
    int expiryYear,
    String expiryFormatted,
    String cardHolderName,
    long dailyLimitCents,
    String formattedDailyLimit,
    boolean onlineEnabled,
    String status,
    Instant createdAt
) {
    public static CardResponse fromDomain(Card card) {
        String expiry = String.format("%02d/%d", card.expiryMonth(), card.expiryYear() % 100);
        return new CardResponse(
                card.id(),
                card.userId(),
                card.walletId(),
                card.cardType(),
                card.cardNetwork(),
                card.cardNumberMasked(),
                card.expiryMonth(),
                card.expiryYear(),
                expiry,
                card.cardHolderName(),
                card.dailyLimitCents(),
                MoneyUtils.format(card.dailyLimitCents(), "USD"),
                card.onlineEnabled(),
                card.status(),
                card.createdAt()
        );
    }
}
