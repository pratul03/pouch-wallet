package com.pockt.beneficiary.domain;

import java.time.Instant;
import java.util.UUID;

public record Beneficiary(
    UUID id,
    UUID userId,
    String name,
    String nickname,
    String phone,
    String vpa,
    String accountNumber,
    String ifscCode,
    boolean isFavorite,
    Instant createdAt,
    Instant updatedAt
) {}
