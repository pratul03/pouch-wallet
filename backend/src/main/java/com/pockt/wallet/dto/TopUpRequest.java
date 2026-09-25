package com.pockt.wallet.dto;

import jakarta.validation.constraints.Positive;

public record TopUpRequest(
    @Positive(message = "Top-up amount must be greater than zero")
    long amount
) {}
