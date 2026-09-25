package com.pockt.transfer.api;

import com.pockt.infrastructure.security.UserPrincipal;
import com.pockt.infrastructure.web.ApiResponse;
import com.pockt.infrastructure.web.PageResponse;
import com.pockt.transfer.dto.TransactionResponse;
import com.pockt.transfer.dto.TransferRequest;
import com.pockt.transfer.dto.TransferResponse;
import com.pockt.transfer.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers", description = "Money transfer operations and history")
@SecurityRequirement(name = "bearerAuth")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @Operation(summary = "Initiate peer-to-peer money transfer")
    public ResponseEntity<ApiResponse<TransferResponse>> transfer(
            @Parameter(description = "Client generated idempotency key", required = false)
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String effectiveKey = idempotencyKey != null && !idempotencyKey.isBlank()
                ? idempotencyKey
                : request.idempotencyKey();

        TransferRequest effectiveRequest = request.withIdempotencyKey(effectiveKey);
        TransferResponse response = transferService.send(effectiveRequest, principal.id());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "Get transaction history with cursor pagination")
    public ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> getHistory(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @AuthenticationPrincipal UserPrincipal principal) {
        PageResponse<TransactionResponse> page = transferService.getHistory(principal.id(), cursor, limit);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transaction detail by ID")
    public ResponseEntity<ApiResponse<TransactionResponse>> getById(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        TransactionResponse tx = transferService.getById(id, principal.id());
        return ResponseEntity.ok(ApiResponse.success(tx));
    }
}
