package com.pockt.card.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RevealCardRequest(
    @NotBlank(message = "Wallet PIN is required to reveal card details")
    @Pattern(regexp = "^\\d{4,6}$", message = "PIN must be 4 to 6 digits")
    String pin
) {}
