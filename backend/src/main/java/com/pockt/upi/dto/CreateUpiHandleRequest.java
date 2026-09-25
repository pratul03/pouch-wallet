package com.pockt.upi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record CreateUpiHandleRequest(
    @NotBlank(message = "VPA is required")
    @Pattern(regexp = "^[a-zA-Z0-9._-]{3,30}@pockt$", message = "VPA must end with @pockt and contain 3-30 alphanumeric characters before @ (e.g. rahul@pockt)")
    String vpa,

    UUID linkedWalletId
) {}
