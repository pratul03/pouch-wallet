package com.pockt.card.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CardChargeRequest(
    @NotNull(message = "Amount is required")
    @Min(value = 100, message = "Minimum charge amount is 1.00 (100 cents)")
    Long amount,

    @NotBlank(message = "Merchant name is required")
    String merchantName,

    String cvv
) {}
