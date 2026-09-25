package com.pockt.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
    @NotBlank(message = "Full name cannot be blank")
    @Size(min = 2, max = 120, message = "Full name must be between 2 and 120 characters")
    String fullName
) {}
