package com.pockt.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetPinRequest(
    @NotBlank(message = "New PIN is required")
    @Pattern(regexp = "^\\d{6}$", message = "PIN must be a 6-digit number")
    String newPin
) {}
