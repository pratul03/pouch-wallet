package com.pockt.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TransferRequest(
    @NotBlank(message = "Receiver phone is required")
    @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "Phone must be in E.164 format")
    String receiverPhone,

    @Positive(message = "Amount must be greater than zero")
    long amount,

    @NotBlank(message = "Currency code is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter code")
    String currency,

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    String description,

    String idempotencyKey
) {
    public TransferRequest withIdempotencyKey(String key) {
        return new TransferRequest(receiverPhone, amount, currency, description, key);
    }
}
