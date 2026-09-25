package com.pockt.wallet.api;

import com.pockt.infrastructure.security.UserPrincipal;
import com.pockt.infrastructure.web.ApiResponse;
import com.pockt.wallet.dto.TopUpRequest;
import com.pockt.wallet.dto.WalletResponse;
import com.pockt.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/wallets")
@Tag(name = "Wallets", description = "Wallet balance and management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    @Operation(summary = "List all wallets for current user")
    public ResponseEntity<ApiResponse<List<WalletResponse>>> getMyWallets(@AuthenticationPrincipal UserPrincipal principal) {
        List<WalletResponse> wallets = walletService.getUserWallets(principal.id());
        return ResponseEntity.ok(ApiResponse.success(wallets));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get specific wallet detail and balance")
    public ResponseEntity<ApiResponse<WalletResponse>> getWallet(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        WalletResponse wallet = walletService.getWallet(id, principal.id());
        return ResponseEntity.ok(ApiResponse.success(wallet));
    }

    @PostMapping("/{id}/topup")
    @Operation(summary = "Mock top-up wallet balance (MVP)")
    public ResponseEntity<ApiResponse<WalletResponse>> topUp(
            @PathVariable("id") UUID id,
            @Valid @RequestBody TopUpRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        WalletResponse wallet = walletService.topUp(id, request.amount(), principal.id());
        return ResponseEntity.ok(ApiResponse.success(wallet));
    }
}
