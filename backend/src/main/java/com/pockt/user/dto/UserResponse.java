package com.pockt.user.dto;

import com.pockt.user.domain.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String phone,
    String fullName,
    String kycStatus,
    Instant createdAt
) {
    public static UserResponse fromDomain(User user) {
        return new UserResponse(
                user.id(),
                user.phone(),
                user.fullName(),
                user.kycStatus(),
                user.createdAt()
        );
    }
}
