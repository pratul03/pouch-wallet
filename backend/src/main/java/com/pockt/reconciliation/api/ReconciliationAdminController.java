package com.pockt.reconciliation.api;

import com.pockt.infrastructure.security.UserPrincipal;
import com.pockt.infrastructure.web.ApiResponse;
import com.pockt.reconciliation.dto.ReconciliationSummaryResponse;
import com.pockt.reconciliation.dto.ReversalResponse;
import com.pockt.reconciliation.service.ReconciliationService;
import com.pockt.transfer.domain.Transaction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin - Reconciliation & Reversals", description = "Back-office tools to reverse disputed transactions, recover funds, and run reconciliation jobs")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class ReconciliationAdminController {

    private final ReconciliationService reconciliationService;

    public ReconciliationAdminController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/transfers/{id}/reverse")
    @Operation(summary = "Reverse a completed transaction, debiting the receiver and refunding the sender")
    public ResponseEntity<ApiResponse<ReversalResponse>> reverseTransfer(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "Reversed by administrator") String reason) {
        ReversalResponse response = reconciliationService.reverseTransaction(principal.id(), id, reason);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/reconciliation/run")
    @Operation(summary = "Manually trigger background reconciliation job to resolve stuck PENDING transactions")
    public ResponseEntity<ApiResponse<ReconciliationSummaryResponse>> runReconciliation(
            @RequestParam(required = false, defaultValue = "5") int olderThanMinutes) {
        ReconciliationSummaryResponse response = reconciliationService.runReconciliation(olderThanMinutes);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/transfers/pending")
    @Operation(summary = "List currently in-process or stuck PENDING transactions awaiting reconciliation")
    public ResponseEntity<ApiResponse<List<Transaction>>> getPendingTransactions(
            @RequestParam(required = false, defaultValue = "20") int limit,
            @RequestParam(required = false, defaultValue = "0") int offset) {
        List<Transaction> list = reconciliationService.getPendingTransactions(limit, offset);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }
}
