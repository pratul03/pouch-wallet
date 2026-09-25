package com.pockt.bank.domain;

import java.time.Instant;
import java.util.UUID;

public record BankTransaction(
    UUID id,
    UUID bankAccountId,
    UUID walletId,
    String type, // DEPOSIT_TO_WALLET | WITHDRAW_TO_BANK
    long amount,
    String currency,
    String status,
    String referenceNumber,
    Instant createdAt
) {}
