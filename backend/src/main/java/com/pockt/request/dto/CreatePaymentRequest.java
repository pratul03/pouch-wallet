package com.pockt.request.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreatePaymentRequest(
    String payerPhone,

    String payerVpa,

    @Positive(message = "Amount must be strictly positive")
    long amountCents,

    @Size(max = 255, message = "Note cannot exceed 255 characters")
    String note,

    Integer expiryHours
) {
    public boolean hasTarget() {
        return (payerPhone != null && !payerPhone.isBlank()) || (payerVpa != null && !payerVpa.isBlank());
    }
}
