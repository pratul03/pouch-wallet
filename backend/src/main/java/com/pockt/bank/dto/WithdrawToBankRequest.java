package com.pockt.bank.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record WithdrawToBankRequest(
    @NotNull(message = "Bank account ID is required")
    UUID bankAccountId,

    UUID walletId,

    @NotNull(message = "Amount is required")
    @Min(value = 100, message = "Minimum withdrawal amount is 1.00 (100 cents)")
    Long amount,

    @NotBlank(message = "PIN is required to authorize bank transfers")
    @Pattern(regexp = "^\\d{4,6}$", message = "PIN must be 4 to 6 digits")
    String pin
) {}
