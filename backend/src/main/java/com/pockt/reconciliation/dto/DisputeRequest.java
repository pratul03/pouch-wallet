package com.pockt.reconciliation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DisputeRequest(
    @NotBlank(message = "Dispute reason is required")
    @Size(max = 255, message = "Reason cannot exceed 255 characters")
    String reason
) {}
