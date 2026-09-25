package com.pockt.wallet.dto;

import com.pockt.infrastructure.util.MoneyUtils;
import com.pockt.wallet.domain.Wallet;

import java.time.Instant;
import java.util.UUID;

public record WalletResponse(
    UUID id,
    String currency,
    long balance,
    String formattedBalance,
    boolean isActive,
    Instant createdAt
) {
    public static WalletResponse fromDomain(Wallet wallet) {
        return new WalletResponse(
                wallet.id(),
                wallet.currency(),
                wallet.balance(),
                MoneyUtils.format(wallet.balance(), wallet.currency()),
                wallet.isActive(),
                wallet.createdAt()
        );
    }
}
