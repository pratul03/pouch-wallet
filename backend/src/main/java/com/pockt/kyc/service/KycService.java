package com.pockt.kyc.service;

import com.pockt.kyc.dto.KycStatusResponse;
import com.pockt.kyc.dto.KycVerificationResponse;
import com.pockt.kyc.dto.ReviewKycRequest;
import com.pockt.kyc.dto.SubmitKycRequest;

import java.util.List;
import java.util.UUID;

public interface KycService {
    KycVerificationResponse submitKyc(UUID userId, SubmitKycRequest request);
    KycStatusResponse getKycStatus(UUID userId);
    List<KycVerificationResponse> getUserSubmissions(UUID userId);
    List<KycVerificationResponse> getPendingSubmissions();
    KycVerificationResponse reviewKyc(UUID adminId, UUID kycId, ReviewKycRequest request);
    void validateTransferLimit(UUID userId, long amountCents);
    void validateBalanceLimit(UUID userId, long resultingBalanceCents);
}
