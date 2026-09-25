package com.pockt.beneficiary.dto;

import com.pockt.beneficiary.domain.Beneficiary;

import java.time.Instant;
import java.util.UUID;

public record BeneficiaryResponse(
    UUID id,
    UUID userId,
    String name,
    String nickname,
    String phone,
    String vpa,
    String accountNumber,
    String ifscCode,
    boolean isFavorite,
    Instant createdAt
) {
    public static BeneficiaryResponse from(Beneficiary b) {
        return new BeneficiaryResponse(
            b.id(),
            b.userId(),
            b.name(),
            b.nickname(),
            b.phone(),
            b.vpa(),
            b.accountNumber(),
            b.ifscCode(),
            b.isFavorite(),
            b.createdAt()
        );
    }
}
