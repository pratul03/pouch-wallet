package com.pockt.beneficiary.service;

import com.pockt.beneficiary.dto.AddBeneficiaryRequest;
import com.pockt.beneficiary.dto.BeneficiaryResponse;

import java.util.List;
import java.util.UUID;

public interface BeneficiaryService {
    BeneficiaryResponse addBeneficiary(UUID userId, AddBeneficiaryRequest request);
    List<BeneficiaryResponse> getBeneficiaries(UUID userId, boolean favoritesOnly);
    BeneficiaryResponse getBeneficiaryById(UUID userId, UUID beneficiaryId);
    void deleteBeneficiary(UUID userId, UUID beneficiaryId);
    BeneficiaryResponse toggleFavorite(UUID userId, UUID beneficiaryId, boolean isFavorite);
}
