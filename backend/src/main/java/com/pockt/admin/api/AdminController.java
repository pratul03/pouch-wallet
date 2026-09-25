package com.pockt.admin.api;

import com.pockt.admin.dto.AdminOverviewReportResponse;
import com.pockt.admin.dto.AdminUserDossierResponse;
import com.pockt.admin.dto.AdminUserFinancialsResponse;
import com.pockt.admin.dto.FreezeWalletRequest;
import com.pockt.admin.dto.UpdateUserStatusRequest;
import com.pockt.admin.service.AdminService;
import com.pockt.infrastructure.web.ApiResponse;
import com.pockt.infrastructure.web.PageResponse;
import com.pockt.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin Back-Office", description = "Back-office administration, user dossier, financial activity, wallet controls, and aggregate reporting")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    @Operation(summary = "Search and list all users with KYC, activity and pagination filters")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String kycStatus,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<UserResponse> response = adminService.listUsers(search, kycStatus, isActive, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get complete user dossier (wallets, bank accounts, UPI handles, cards, credit)")
    public ResponseEntity<ApiResponse<AdminUserDossierResponse>> getUserDossier(
            @PathVariable UUID userId
    ) {
        AdminUserDossierResponse response = adminService.getUserDossier(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/users/{userId}/financials")
    @Operation(summary = "Get user financial metrics (spent, received, net flow, deposits, withdrawals) with time filter")
    public ResponseEntity<ApiResponse<AdminUserFinancialsResponse>> getUserFinancials(
            @PathVariable UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        AdminUserFinancialsResponse response = adminService.getUserFinancials(userId, from, to);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/users/{userId}/status")
    @Operation(summary = "Activate or freeze a user account")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        UserResponse response = adminService.updateUserStatus(userId, request.isActive());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/wallets/{walletId}/freeze")
    @Operation(summary = "Freeze or unfreeze a specific wallet")
    public ResponseEntity<ApiResponse<String>> setWalletStatus(
            @PathVariable UUID walletId,
            @Valid @RequestBody FreezeWalletRequest request
    ) {
        adminService.setWalletStatus(walletId, request.isActive());
        String msg = request.isActive() ? "Wallet unfrozen successfully" : "Wallet frozen successfully";
        return ResponseEntity.ok(ApiResponse.success(msg));
    }

    @GetMapping("/reports/overview")
    @Operation(summary = "Get platform-wide executive report (users, liquidity, transfers, bank flows, credit) with time filter")
    public ResponseEntity<ApiResponse<AdminOverviewReportResponse>> getOverviewReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        AdminOverviewReportResponse response = adminService.getOverviewReport(from, to);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
