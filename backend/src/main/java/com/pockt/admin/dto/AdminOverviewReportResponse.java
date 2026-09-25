package com.pockt.admin.dto;

import java.time.Instant;

public record AdminOverviewReportResponse(
    Instant periodFrom,
    Instant periodTo,
    long totalUsers,
    long newUsersInPeriod,
    long totalWallets,
    long totalSystemLiquidityCents,
    String formattedTotalSystemLiquidity,
    long totalTransfersCountInPeriod,
    long totalTransfersVolumeCents,
    String formattedTotalTransfersVolume,
    long totalBankDepositsCountInPeriod,
    long totalBankDepositsVolumeCents,
    String formattedTotalBankDepositsVolume,
    long totalBankWithdrawalsCountInPeriod,
    long totalBankWithdrawalsVolumeCents,
    String formattedTotalBankWithdrawalsVolume,
    long totalCreditAccounts,
    long totalCreditOutstandingCents,
    String formattedTotalCreditOutstanding
) {}
