package com.pockt.bank.domain;

import java.time.Instant;
import java.util.UUID;

public record BankAccount(
    UUID id,
    UUID userId,
    String bankName,
    String accountNumber,
    String ifscCode,
    String accountHolderName,
    long simulatedBalance,
    boolean isPrimary,
    String status,
    Instant createdAt,
    Instant updatedAt
) {}
