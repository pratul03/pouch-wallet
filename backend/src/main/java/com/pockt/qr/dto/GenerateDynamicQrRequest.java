package com.pockt.qr.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GenerateDynamicQrRequest(
    @NotNull(message = "Amount is required for dynamic QR")
    @Min(value = 100, message = "Minimum amount is 1.00 (100 cents)")
    Long amount,

    @Size(max = 100, message = "Note cannot exceed 100 characters")
    String note
) {}
