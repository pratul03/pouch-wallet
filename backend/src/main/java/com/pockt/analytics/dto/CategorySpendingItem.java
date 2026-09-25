package com.pockt.analytics.dto;

public record CategorySpendingItem(
    String category,
    long amountCents,
    double percentage,
    int transactionCount
) {}
