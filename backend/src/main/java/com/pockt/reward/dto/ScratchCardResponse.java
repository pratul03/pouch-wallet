package com.pockt.reward.dto;

import com.pockt.reward.domain.ScratchCard;

import java.time.Instant;
import java.util.UUID;

public record ScratchCardResponse(
    UUID id,
    UUID userId,
    String title,
    String description,
    long rewardAmountCents,
    boolean isScratched,
    Instant scratchedAt,
    Instant createdAt
) {
    public static ScratchCardResponse from(ScratchCard card) {
        return new ScratchCardResponse(
            card.id(),
            card.userId(),
            card.title(),
            card.description(),
            card.isScratched() ? card.rewardAmountCents() : 0L, // Hidden until scratched!
            card.isScratched(),
            card.scratchedAt(),
            card.createdAt()
        );
    }
}
