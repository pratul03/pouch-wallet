package com.pockt.kyc.api;

import com.pockt.infrastructure.security.UserPrincipal;
import com.pockt.infrastructure.web.ApiResponse;
import com.pockt.kyc.dto.KycVerificationResponse;
import com.pockt.kyc.dto.ReviewKycRequest;
import com.pockt.kyc.service.KycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/kyc")
@Tag(name = "Admin - KYC Operations", description = "Back-office endpoints to review, approve, or reject user KYC submissions")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class KycAdminController {

    private final KycService kycService;

    public KycAdminController(KycService kycService) {
        this.kycService = kycService;
    }

    @GetMapping("/pending")
    @Operation(summary = "List all pending submitted KYC documents awaiting review")
    public ResponseEntity<ApiResponse<List<KycVerificationResponse>>> getPendingSubmissions() {
        List<KycVerificationResponse> list = kycService.getPendingSubmissions();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "Approve or reject a KYC submission and promote user to Tier 1")
    public ResponseEntity<ApiResponse<KycVerificationResponse>> reviewKyc(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody ReviewKycRequest request) {
        KycVerificationResponse response = kycService.reviewKyc(principal.id(), id, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
