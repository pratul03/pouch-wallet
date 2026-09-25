package com.pockt.card.dto;

import jakarta.validation.constraints.Min;

public record UpdateCardSettingsRequest(
    Boolean onlineEnabled,

    @Min(value = 1000, message = "Daily limit cannot be less than 10.00 (1000 cents)")
    Long dailyLimitCents
) {}
