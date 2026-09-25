package com.pockt.bill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record PayBillRequest(
    @NotBlank(message = "Biller ID is required")
    String billerId,

    @NotBlank(message = "Consumer number is required")
    String consumerNumber,

    @Positive(message = "Amount must be strictly positive")
    long amountCents,

    String note
) {}
