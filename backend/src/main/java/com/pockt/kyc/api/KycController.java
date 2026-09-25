package com.pockt.kyc.api;

import com.pockt.infrastructure.security.UserPrincipal;
import com.pockt.infrastructure.web.ApiResponse;
import com.pockt.kyc.dto.KycStatusResponse;
import com.pockt.kyc.dto.KycVerificationResponse;
import com.pockt.kyc.dto.SubmitKycRequest;
import com.pockt.kyc.service.KycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/kyc")
@Tag(name = "KYC & Verification", description = "Submit KYC documents, check tier status, and view account limits")
@SecurityRequirement(name = "BearerAuth")
public class KycController {

    private final KycService kycService;

    public KycController(KycService kycService) {
        this.kycService = kycService;
    }

    @PostMapping("/submit")
    @Operation(summary = "Submit KYC identity documents for verification (Passport, National ID, PAN, Driving License)")
    public ResponseEntity<ApiResponse<KycVerificationResponse>> submitKyc(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SubmitKycRequest request) {
        KycVerificationResponse response = kycService.submitKyc(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping("/status")
    @Operation(summary = "Get current KYC tier status and active transaction/balance limits")
    public ResponseEntity<ApiResponse<KycStatusResponse>> getKycStatus(
            @AuthenticationPrincipal UserPrincipal principal) {
        KycStatusResponse response = kycService.getKycStatus(principal.id());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/history")
    @Operation(summary = "Get historical KYC verification submissions for the user")
    public ResponseEntity<ApiResponse<List<KycVerificationResponse>>> getKycHistory(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<KycVerificationResponse> list = kycService.getUserSubmissions(principal.id());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }
}
