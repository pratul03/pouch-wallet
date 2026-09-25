package com.pockt.reconciliation.api;

import com.pockt.infrastructure.security.UserPrincipal;
import com.pockt.infrastructure.web.ApiResponse;
import com.pockt.reconciliation.dto.DisputeRequest;
import com.pockt.reconciliation.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Disputes & Support", description = "Flag transactions for review, dispute erroneous transfers, and report issues")
@SecurityRequirement(name = "BearerAuth")
public class DisputeController {

    private final ReconciliationService reconciliationService;

    public DisputeController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/{id}/dispute")
    @Operation(summary = "Flag a transaction as disputed by sender or receiver")
    public ResponseEntity<ApiResponse<Map<String, Object>>> disputeTransfer(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody DisputeRequest request) {
        reconciliationService.disputeTransaction(principal.id(), id, request.reason());
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "transactionId", id,
                "status", "DISPUTED",
                "message", "Dispute registered. Our fraud & compliance desk will review this transfer."
        )));
    }
}
