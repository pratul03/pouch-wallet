package com.pockt.upi.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpiPaymentRequest(
    @NotBlank(message = "Receiver UPI ID is required")
    String receiverVpa,

    @NotNull(message = "Amount is required")
    @Min(value = 100, message = "Minimum transfer amount is 1.00 (100 cents)")
    Long amount,

    @NotBlank(message = "UPI PIN is required")
    @Pattern(regexp = "^\\d{4,6}$", message = "PIN must be 4 to 6 digits")
    String pin,

    String note,

    String idempotencyKey
) {}
