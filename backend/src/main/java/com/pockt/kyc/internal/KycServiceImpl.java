package com.pockt.kyc.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pockt.infrastructure.exception.ErrorCode;
import com.pockt.infrastructure.exception.PocktException;
import com.pockt.kyc.domain.KycVerification;
import com.pockt.kyc.dto.KycStatusResponse;
import com.pockt.kyc.dto.KycVerificationResponse;
import com.pockt.kyc.dto.ReviewKycRequest;
import com.pockt.kyc.dto.SubmitKycRequest;
import com.pockt.kyc.repository.KycRepository;
import com.pockt.kyc.service.KycService;
import com.pockt.transfer.domain.NotificationOutbox;
import com.pockt.transfer.repository.OutboxRepository;
import com.pockt.user.domain.User;
import com.pockt.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KycServiceImpl implements KycService {

    private static final Logger log = LoggerFactory.getLogger(KycServiceImpl.class);

    public static final long TIER_0_MAX_BALANCE = 100_000L;    // $1,000.00
    public static final long TIER_0_MAX_TRANSFER = 25_000L;    // $250.00
    public static final long TIER_1_MAX_BALANCE = 10_000_000L; // $100,000.00
    public static final long TIER_1_MAX_TRANSFER = 2_500_000L; // $25,000.00

    private final KycRepository kycRepository;
    private final UserRepository userRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public KycServiceImpl(
            KycRepository kycRepository,
            UserRepository userRepository,
            OutboxRepository outboxRepository,
            ObjectMapper objectMapper
    ) {
        this.kycRepository = kycRepository;
        this.userRepository = userRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public KycVerificationResponse submitKyc(UUID userId, SubmitKycRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new PocktException(ErrorCode.USER_NOT_FOUND, "User not found", HttpStatus.NOT_FOUND));

        if ("VERIFIED".equalsIgnoreCase(user.kycStatus()) || user.kycTier() >= 1) {
            throw new PocktException(ErrorCode.KYC_ALREADY_VERIFIED, "User is already fully KYC verified", HttpStatus.BAD_REQUEST);
        }

        KycVerification kyc = new KycVerification(
                UUID.randomUUID(),
                userId,
                request.idType().trim().toUpperCase(),
                request.documentNumber().trim(),
                request.documentUrl() != null ? request.documentUrl().trim() : null,
                "SUBMITTED",
                null,
                null,
                null,
                Instant.now(),
                Instant.now()
        );

        kycRepository.save(kyc);
        userRepository.updateKycStatusAndTier(userId, "SUBMITTED", 0);

        log.info("KYC document submitted for user {}: type={}, doc={}", userId, kyc.idType(), kyc.documentNumber());
        return KycVerificationResponse.from(kyc);
    }

    @Override
    @Transactional(readOnly = true)
    public KycStatusResponse getKycStatus(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new PocktException(ErrorCode.USER_NOT_FOUND, "User not found", HttpStatus.NOT_FOUND));

        var latest = kycRepository.findLatestByUserId(userId).map(KycVerificationResponse::from).orElse(null);

        long maxBalance = user.kycTier() >= 1 ? TIER_1_MAX_BALANCE : TIER_0_MAX_BALANCE;
        long maxTransfer = user.kycTier() >= 1 ? TIER_1_MAX_TRANSFER : TIER_0_MAX_TRANSFER;

        return new KycStatusResponse(
                user.kycStatus(),
                user.kycTier(),
                maxBalance,
                maxTransfer,
                latest
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<KycVerificationResponse> getUserSubmissions(UUID userId) {
        return kycRepository.findByUserId(userId).stream()
                .map(KycVerificationResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<KycVerificationResponse> getPendingSubmissions() {
        return kycRepository.findByStatus("SUBMITTED").stream()
                .map(KycVerificationResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public KycVerificationResponse reviewKyc(UUID adminId, UUID kycId, ReviewKycRequest request) {
        KycVerification kyc = kycRepository.findById(kycId)
                .orElseThrow(() -> new PocktException(ErrorCode.KYC_NOT_FOUND, "KYC verification submission not found", HttpStatus.NOT_FOUND));

        String newStatus = request.status().toUpperCase();
        boolean isApproved = "APPROVED".equalsIgnoreCase(newStatus);
        int newTier = isApproved ? 1 : 0;
        String userKycStatus = isApproved ? "VERIFIED" : "REJECTED";

        kycRepository.updateReview(kycId, newStatus, request.rejectionReason(), adminId);
        userRepository.updateKycStatusAndTier(kyc.userId(), userKycStatus, newTier);

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "status", newStatus,
                    "tier", newTier,
                    "rejectionReason", request.rejectionReason() != null ? request.rejectionReason() : ""
            ));
            outboxRepository.save(new NotificationOutbox(
                    UUID.randomUUID(),
                    kyc.userId(),
                    isApproved ? "KYC_APPROVED" : "KYC_REJECTED",
                    payload,
                    "PENDING",
                    0,
                    null,
                    Instant.now()
            ));
        } catch (JsonProcessingException e) {
            log.error("Failed to build KYC review notification payload", e);
        }

        log.info("KYC submission {} reviewed by admin {}: status={}", kycId, adminId, newStatus);
        return KycVerificationResponse.from(kycRepository.findById(kycId).orElse(kyc));
    }

    @Override
    public void validateTransferLimit(UUID userId, long amountCents) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new PocktException(ErrorCode.USER_NOT_FOUND, "User not found", HttpStatus.NOT_FOUND));

        long maxTransfer = user.kycTier() >= 1 ? TIER_1_MAX_TRANSFER : TIER_0_MAX_TRANSFER;
        if (amountCents > maxTransfer) {
            throw new PocktException(
                    ErrorCode.KYC_TIER_LIMIT_EXCEEDED,
                    String.format("Transfer amount of $%.2f exceeds your KYC Tier %d single transfer limit of $%.2f. Upgrade KYC to increase limits.",
                            amountCents / 100.0, user.kycTier(), maxTransfer / 100.0),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    @Override
    public void validateBalanceLimit(UUID userId, long resultingBalanceCents) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new PocktException(ErrorCode.USER_NOT_FOUND, "User not found", HttpStatus.NOT_FOUND));

        long maxBalance = user.kycTier() >= 1 ? TIER_1_MAX_BALANCE : TIER_0_MAX_BALANCE;
        if (resultingBalanceCents > maxBalance) {
            throw new PocktException(
                    ErrorCode.KYC_TIER_LIMIT_EXCEEDED,
                    String.format("Wallet balance would exceed your KYC Tier %d maximum balance limit of $%.2f. Upgrade KYC to increase limits.",
                            user.kycTier(), maxBalance / 100.0),
                    HttpStatus.BAD_REQUEST
            );
        }
    }
}
