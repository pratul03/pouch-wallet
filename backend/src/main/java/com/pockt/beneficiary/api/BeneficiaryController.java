package com.pockt.beneficiary.api;

import com.pockt.beneficiary.dto.AddBeneficiaryRequest;
import com.pockt.beneficiary.dto.BeneficiaryResponse;
import com.pockt.beneficiary.service.BeneficiaryService;
import com.pockt.infrastructure.security.UserPrincipal;
import com.pockt.infrastructure.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/beneficiaries")
@Tag(name = "Beneficiaries", description = "Manage saved payees, frequent contacts, and favorites")
@SecurityRequirement(name = "BearerAuth")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    public BeneficiaryController(BeneficiaryService beneficiaryService) {
        this.beneficiaryService = beneficiaryService;
    }

    @PostMapping
    @Operation(summary = "Save a new beneficiary (phone, UPI VPA, or bank account)")
    public ResponseEntity<ApiResponse<BeneficiaryResponse>> addBeneficiary(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AddBeneficiaryRequest request) {
        BeneficiaryResponse res = beneficiaryService.addBeneficiary(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(res));
    }

    @GetMapping
    @Operation(summary = "List all saved beneficiaries for current user")
    public ResponseEntity<ApiResponse<List<BeneficiaryResponse>>> getBeneficiaries(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false, defaultValue = "false") boolean favoritesOnly) {
        List<BeneficiaryResponse> list = beneficiaryService.getBeneficiaries(principal.id(), favoritesOnly);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get beneficiary details by ID")
    public ResponseEntity<ApiResponse<BeneficiaryResponse>> getBeneficiaryById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        BeneficiaryResponse res = beneficiaryService.getBeneficiaryById(principal.id(), id);
        return ResponseEntity.ok(ApiResponse.ok(res));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a saved beneficiary")
    public ResponseEntity<ApiResponse<Void>> deleteBeneficiary(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        beneficiaryService.deleteBeneficiary(principal.id(), id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PatchMapping("/{id}/favorite")
    @Operation(summary = "Toggle favorite status for beneficiary")
    public ResponseEntity<ApiResponse<BeneficiaryResponse>> toggleFavorite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam boolean favorite) {
        BeneficiaryResponse res = beneficiaryService.toggleFavorite(principal.id(), id, favorite);
        return ResponseEntity.ok(ApiResponse.ok(res));
    }
}
