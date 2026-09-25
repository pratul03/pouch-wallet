package com.pockt.bank.api;

import com.pockt.bank.dto.AddMoneyFromBankRequest;
import com.pockt.bank.dto.BankAccountResponse;
import com.pockt.bank.dto.BankTransactionResponse;
import com.pockt.bank.dto.LinkBankAccountRequest;
import com.pockt.bank.dto.WithdrawToBankRequest;
import com.pockt.bank.service.BankService;
import com.pockt.infrastructure.security.UserPrincipal;
import com.pockt.infrastructure.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/v1/banks")
@Tag(name = "Bank Module", description = "Simulated bank integration, link accounts, add money, withdraw to bank")
@SecurityRequirement(name = "BearerAuth")
public class BankController {

    private final BankService bankService;

    public BankController(BankService bankService) {
        this.bankService = bankService;
    }

    @PostMapping("/link")
    @Operation(summary = "Link a new bank account")
    public ResponseEntity<ApiResponse<BankAccountResponse>> linkBankAccount(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody LinkBankAccountRequest request
    ) {
        BankAccountResponse response = bankService.linkBankAccount(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "Get all linked bank accounts for current user")
    public ResponseEntity<ApiResponse<List<BankAccountResponse>>> getBankAccounts(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<BankAccountResponse> list = bankService.getBankAccounts(principal.id());
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get bank account by ID")
    public ResponseEntity<ApiResponse<BankAccountResponse>> getBankAccount(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        BankAccountResponse response = bankService.getBankAccount(principal.id(), id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{id}/primary")
    @Operation(summary = "Set bank account as primary")
    public ResponseEntity<ApiResponse<String>> setPrimaryAccount(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        bankService.setPrimaryAccount(principal.id(), id);
        return ResponseEntity.ok(ApiResponse.success("Primary account updated successfully"));
    }

    @PostMapping("/add-money")
    @Operation(summary = "Add money to wallet from linked bank account")
    public ResponseEntity<ApiResponse<BankTransactionResponse>> addMoney(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AddMoneyFromBankRequest request
    ) {
        BankTransactionResponse response = bankService.addMoneyFromBank(principal.id(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/withdraw")
    @Operation(summary = "Withdraw money from wallet back to bank account")
    public ResponseEntity<ApiResponse<BankTransactionResponse>> withdraw(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody WithdrawToBankRequest request
    ) {
        BankTransactionResponse response = bankService.withdrawToBank(principal.id(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}/transactions")
    @Operation(summary = "Get transactions for a specific bank account")
    public ResponseEntity<ApiResponse<List<BankTransactionResponse>>> getTransactions(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "20") int limit
    ) {
        List<BankTransactionResponse> list = bankService.getBankTransactions(principal.id(), id, limit);
        return ResponseEntity.ok(ApiResponse.success(list));
    }
}
