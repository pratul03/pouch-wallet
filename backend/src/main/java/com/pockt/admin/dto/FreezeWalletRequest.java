package com.pockt.admin.dto;

import jakarta.validation.constraints.NotNull;

public record FreezeWalletRequest(
    @NotNull(message = "isActive flag is required")
    Boolean isActive
) {}
