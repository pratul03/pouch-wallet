package com.pockt.user.domain;

import java.time.Instant;
import java.util.UUID;

public record User(
    UUID id,
    String phone,
    String fullName,
    String pinHash,
    String fcmToken,
    String kycStatus,
    boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {}
