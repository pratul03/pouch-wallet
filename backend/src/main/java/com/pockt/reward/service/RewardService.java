package com.pockt.reward.service;

import com.pockt.reward.dto.RewardsSummaryResponse;
import com.pockt.reward.dto.ScratchCardResponse;

import java.util.List;
import java.util.UUID;

public interface RewardService {
    ScratchCardResponse issueReward(UUID userId, String title, String description, long amountCents, UUID txId);
    List<ScratchCardResponse> getUserCards(UUID userId, Boolean unscratchedOnly);
    RewardsSummaryResponse getRewardsSummary(UUID userId);
    ScratchCardResponse scratchCard(UUID userId, UUID cardId);
}
