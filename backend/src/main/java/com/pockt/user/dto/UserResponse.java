package com.pockt.user.dto;

import com.pockt.user.domain.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String phone,
    String fullName,
    String kycStatus,
    String role,
    boolean isActive,
    Instant createdAt
) {
    public UserResponse(UUID id, String phone, String fullName, String kycStatus, Instant createdAt) {
        this(id, phone, fullName, kycStatus, "USER", true, createdAt);
    }
    public static UserResponse fromDomain(User user) {
        return new UserResponse(
                user.id(),
                user.phone(),
                user.fullName(),
                user.kycStatus(),
                user.role() != null ? user.role() : "USER",
                user.isActive(),
                user.createdAt()
        );
    }
}
