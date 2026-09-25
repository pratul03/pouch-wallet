package com.pockt.card.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record RepayCreditRequest(
    @NotNull(message = "Amount is required")
    @Min(value = 100, message = "Minimum repayment amount is 1.00 (100 cents)")
    Long amount,

    UUID walletId,

    @NotBlank(message = "PIN is required to authorize repayment")
    @Pattern(regexp = "^\\d{4,6}$", message = "PIN must be 4 to 6 digits")
    String pin
) {}
