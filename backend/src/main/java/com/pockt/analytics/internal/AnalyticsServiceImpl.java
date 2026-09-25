package com.pockt.analytics.internal;

import com.pockt.analytics.dto.CategorySpendingItem;
import com.pockt.analytics.dto.SpendingSummaryResponse;
import com.pockt.analytics.repository.AnalyticsRepository;
import com.pockt.analytics.service.AnalyticsService;
import com.pockt.wallet.domain.Wallet;
import com.pockt.wallet.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private final AnalyticsRepository analyticsRepository;
    private final WalletService walletService;

    public AnalyticsServiceImpl(AnalyticsRepository analyticsRepository, WalletService walletService) {
        this.analyticsRepository = analyticsRepository;
        this.walletService = walletService;
    }

    @Override
    @Transactional(readOnly = true)
    public SpendingSummaryResponse getMonthlySpending(UUID userId, Integer month, Integer year) {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        int targetYear = (year != null && year >= 2020 && year <= 2100) ? year : now.getYear();
        int targetMonth = (month != null && month >= 1 && month <= 12) ? month : now.getMonthValue();

        LocalDate startMonthDate = LocalDate.of(targetYear, targetMonth, 1);
        LocalDate endMonthDate = startMonthDate.plusMonths(1);

        Instant start = startMonthDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = endMonthDate.atStartOfDay().toInstant(ZoneOffset.UTC);

        Wallet wallet = walletService.getOrCreateWallet(userId, "USD");

        long totalSpent = analyticsRepository.getTotalSpent(wallet.id(), start, end);
        long totalReceived = analyticsRepository.getTotalReceived(wallet.id(), start, end);
        long netFlow = totalReceived - totalSpent;

        List<CategorySpendingItem> categories = analyticsRepository.getCategoryBreakdown(wallet.id(), start, end, totalSpent);

        return new SpendingSummaryResponse(
                totalSpent,
                totalReceived,
                netFlow,
                targetMonth,
                targetYear,
                categories
        );
    }
}
