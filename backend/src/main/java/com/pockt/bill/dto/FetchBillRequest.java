package com.pockt.bill.dto;

import jakarta.validation.constraints.NotBlank;

public record FetchBillRequest(
    @NotBlank(message = "Biller ID is required")
    String billerId,

    @NotBlank(message = "Consumer number/account is required")
    String consumerNumber
) {}
