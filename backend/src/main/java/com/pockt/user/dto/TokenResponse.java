package com.pockt.user.dto;

import java.util.UUID;

public record TokenResponse(
    UUID userId,
    String accessToken,
    String refreshToken,
    long expiresIn
) {}
