package com.pockt.analytics.service;

import com.pockt.analytics.dto.SpendingSummaryResponse;

import java.util.UUID;

public interface AnalyticsService {
    SpendingSummaryResponse getMonthlySpending(UUID userId, Integer month, Integer year);
}
