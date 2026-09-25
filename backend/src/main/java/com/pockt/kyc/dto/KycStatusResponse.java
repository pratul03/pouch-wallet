package com.pockt.kyc.dto;

public record KycStatusResponse(
    String kycStatus,
    int kycTier,
    long maxBalanceCents,
    long maxSingleTransferCents,
    KycVerificationResponse latestSubmission
) {}
