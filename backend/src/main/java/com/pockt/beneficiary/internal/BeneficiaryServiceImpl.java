package com.pockt.beneficiary.internal;

import com.pockt.beneficiary.domain.Beneficiary;
import com.pockt.beneficiary.dto.AddBeneficiaryRequest;
import com.pockt.beneficiary.dto.BeneficiaryResponse;
import com.pockt.beneficiary.repository.BeneficiaryRepository;
import com.pockt.beneficiary.service.BeneficiaryService;
import com.pockt.infrastructure.exception.ErrorCode;
import com.pockt.infrastructure.exception.PocktException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BeneficiaryServiceImpl implements BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;

    public BeneficiaryServiceImpl(BeneficiaryRepository beneficiaryRepository) {
        this.beneficiaryRepository = beneficiaryRepository;
    }

    @Override
    @Transactional
    public BeneficiaryResponse addBeneficiary(UUID userId, AddBeneficiaryRequest request) {
        if (!request.isValidTarget()) {
            throw new PocktException(ErrorCode.INVALID_BENEFICIARY,
                    "Beneficiary must have at least a valid phone, UPI VPA, or bank account with IFSC",
                    HttpStatus.BAD_REQUEST);
        }

        Beneficiary beneficiary = new Beneficiary(
                UUID.randomUUID(),
                userId,
                request.name().trim(),
                request.nickname() != null ? request.nickname().trim() : null,
                request.phone() != null ? request.phone().trim() : null,
                request.vpa() != null ? request.vpa().trim().toLowerCase() : null,
                request.accountNumber() != null ? request.accountNumber().trim() : null,
                request.ifscCode() != null ? request.ifscCode().trim().toUpperCase() : null,
                Boolean.TRUE.equals(request.isFavorite()),
                Instant.now(),
                Instant.now()
        );

        beneficiaryRepository.save(beneficiary);
        return BeneficiaryResponse.from(beneficiary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BeneficiaryResponse> getBeneficiaries(UUID userId, boolean favoritesOnly) {
        List<Beneficiary> list = favoritesOnly
                ? beneficiaryRepository.findFavoritesByUserId(userId)
                : beneficiaryRepository.findByUserId(userId);
        return list.stream().map(BeneficiaryResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BeneficiaryResponse getBeneficiaryById(UUID userId, UUID beneficiaryId) {
        return beneficiaryRepository.findById(beneficiaryId)
                .filter(b -> b.userId().equals(userId))
                .map(BeneficiaryResponse::from)
                .orElseThrow(() -> new PocktException(ErrorCode.BENEFICIARY_NOT_FOUND,
                        "Beneficiary not found", HttpStatus.NOT_FOUND));
    }

    @Override
    @Transactional
    public void deleteBeneficiary(UUID userId, UUID beneficiaryId) {
        boolean deleted = beneficiaryRepository.deleteById(beneficiaryId, userId);
        if (!deleted) {
            throw new PocktException(ErrorCode.BENEFICIARY_NOT_FOUND,
                    "Beneficiary not found or does not belong to user", HttpStatus.NOT_FOUND);
        }
    }

    @Override
    @Transactional
    public BeneficiaryResponse toggleFavorite(UUID userId, UUID beneficiaryId, boolean isFavorite) {
        boolean updated = beneficiaryRepository.updateFavorite(beneficiaryId, userId, isFavorite);
        if (!updated) {
            throw new PocktException(ErrorCode.BENEFICIARY_NOT_FOUND,
                    "Beneficiary not found or does not belong to user", HttpStatus.NOT_FOUND);
        }
        return getBeneficiaryById(userId, beneficiaryId);
    }
}
