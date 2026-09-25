package com.pockt.user.dto;

import com.pockt.user.domain.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String phone,
    String fullName,
    String kycStatus,
    int kycTier,
    String role,
    boolean isActive,
    Instant createdAt
) {
    public UserResponse(UUID id, String phone, String fullName, String kycStatus, String role, boolean isActive, Instant createdAt) {
        this(id, phone, fullName, kycStatus, 0, role, isActive, createdAt);
    }

    public UserResponse(UUID id, String phone, String fullName, String kycStatus, Instant createdAt) {
        this(id, phone, fullName, kycStatus, 0, "USER", true, createdAt);
    }

    public static UserResponse fromDomain(User user) {
        return new UserResponse(
                user.id(),
                user.phone(),
                user.fullName(),
                user.kycStatus(),
                user.kycTier(),
                user.role() != null ? user.role() : "USER",
                user.isActive(),
                user.createdAt()
        );
    }
}
