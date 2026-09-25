package com.pockt.kyc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitKycRequest(
    @NotBlank(message = "ID type is required (e.g. PASSPORT, NATIONAL_ID, DRIVING_LICENSE, PAN)")
    String idType,

    @NotBlank(message = "Document number is required")
    @Size(max = 80, message = "Document number cannot exceed 80 characters")
    String documentNumber,

    @Size(max = 500, message = "Document URL cannot exceed 500 characters")
    String documentUrl
) {}
