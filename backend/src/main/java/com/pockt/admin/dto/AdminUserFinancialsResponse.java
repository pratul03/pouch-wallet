package com.pockt.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminUserFinancialsResponse(
    UUID userId,
    String phone,
    String fullName,
    Instant filterFrom,
    Instant filterTo,
    long totalSpentCents,
    String formattedTotalSpent,
    int outgoingTransferCount,
    long totalReceivedCents,
    String formattedTotalReceived,
    int incomingTransferCount,
    long netTransferFlowCents,
    String formattedNetTransferFlow,
    long totalBankDepositsCents,
    String formattedTotalBankDeposits,
    int bankDepositCount,
    long totalBankWithdrawalsCents,
    String formattedTotalBankWithdrawals,
    int bankWithdrawalCount
) {}
