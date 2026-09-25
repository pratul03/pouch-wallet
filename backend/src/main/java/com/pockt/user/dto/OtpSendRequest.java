package com.pockt.user.dto;

import com.pockt.user.domain.OtpPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record OtpSendRequest(
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "Phone must be in E.164 format (e.g. +2348012345678)")
    String phone,

    @NotNull(message = "Purpose is required")
    OtpPurpose purpose
) {}
