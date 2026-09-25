package com.pockt.user.dto;

import com.pockt.user.domain.OtpPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record OtpVerifyRequest(
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "Phone must be in E.164 format")
    String phone,

    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
    String otp,

    @NotNull(message = "Purpose is required")
    OtpPurpose purpose
) {}
