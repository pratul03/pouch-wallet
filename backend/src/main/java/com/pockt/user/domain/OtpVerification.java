package com.pockt.user.domain;

import java.time.Instant;
import java.util.UUID;

public record OtpVerification(
    UUID id,
    String phone,
    String otpHash,
    OtpPurpose purpose,
    Instant expiresAt,
    boolean used,
    Instant createdAt
) {}
