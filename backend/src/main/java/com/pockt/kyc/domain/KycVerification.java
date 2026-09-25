package com.pockt.kyc.domain;

import java.time.Instant;
import java.util.UUID;

public record KycVerification(
    UUID id,
    UUID userId,
    String idType,
    String documentNumber,
    String documentUrl,
    String status,
    String rejectionReason,
    UUID reviewedBy,
    Instant reviewedAt,
    Instant createdAt,
    Instant updatedAt
) {}
