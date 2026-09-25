package com.pockt.bank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LinkBankAccountRequest(
    @NotBlank(message = "Bank name is required")
    String bankName,

    @NotBlank(message = "Account number is required")
    @Pattern(regexp = "^\\d{9,18}$", message = "Account number must be between 9 and 18 digits")
    String accountNumber,

    @NotBlank(message = "IFSC code is required")
    @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "Invalid IFSC format (e.g. HDFC0001234)")
    String ifscCode,

    @NotBlank(message = "Account holder name is required")
    String accountHolderName
) {}
