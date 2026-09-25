package com.pockt.analytics.dto;

import java.util.List;

public record SpendingSummaryResponse(
    long totalSpentCents,
    long totalReceivedCents,
    long netFlowCents,
    int month,
    int year,
    List<CategorySpendingItem> categories
) {}
