package com.pockt.reward.dto;

import java.util.List;

public record RewardsSummaryResponse(
    long totalCashbackEarnedCents,
    int totalCardsCount,
    int unscratchedCount,
    List<ScratchCardResponse> cards
) {}
