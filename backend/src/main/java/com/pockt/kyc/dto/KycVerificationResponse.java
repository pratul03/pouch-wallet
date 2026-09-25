package com.pockt.kyc.dto;

import com.pockt.kyc.domain.KycVerification;

import java.time.Instant;
import java.util.UUID;

public record KycVerificationResponse(
    UUID id,
    UUID userId,
    String idType,
    String documentNumber,
    String documentUrl,
    String status,
    String rejectionReason,
    UUID reviewedBy,
    Instant reviewedAt,
    Instant createdAt
) {
    public static KycVerificationResponse from(KycVerification k) {
        return new KycVerificationResponse(
            k.id(),
            k.userId(),
            k.idType(),
            k.documentNumber(),
            k.documentUrl(),
            k.status(),
            k.rejectionReason(),
            k.reviewedBy(),
            k.reviewedAt(),
            k.createdAt()
        );
    }
}
