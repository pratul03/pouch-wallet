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
    int kycTier,
    String role,
    boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {
    public User(UUID id, String phone, String fullName, String pinHash, String fcmToken, String kycStatus, String role, boolean isActive, Instant createdAt, Instant updatedAt) {
        this(id, phone, fullName, pinHash, fcmToken, kycStatus, 0, role, isActive, createdAt, updatedAt);
    }

    public User(UUID id, String phone, String fullName, String pinHash, String fcmToken, String kycStatus, boolean isActive, Instant createdAt, Instant updatedAt) {
        this(id, phone, fullName, pinHash, fcmToken, kycStatus, 0, "USER", isActive, createdAt, updatedAt);
    }
}
