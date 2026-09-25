package com.pockt.card.api;

import com.pockt.card.dto.ApplyCreditCardRequest;
import com.pockt.card.dto.CardChargeRequest;
import com.pockt.card.dto.CardChargeResponse;
import com.pockt.card.dto.CardResponse;
import com.pockt.card.dto.CardRevealResponse;
import com.pockt.card.dto.CreditAccountResponse;
import com.pockt.card.dto.IssueDebitCardRequest;
import com.pockt.card.dto.RepayCreditRequest;
import com.pockt.card.dto.RevealCardRequest;
import com.pockt.card.dto.UpdateCardSettingsRequest;
import com.pockt.card.service.CardService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cards")
@Tag(name = "Cards & Credit", description = "Virtual Debit Cards, Credit Cards, PIN-secured CVV reveal, and spending simulator")
@SecurityRequirement(name = "BearerAuth")
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @PostMapping("/debit")
    @Operation(summary = "Issue a virtual debit card linked to user wallet")
    public ResponseEntity<ApiResponse<CardResponse>> issueDebitCard(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) IssueDebitCardRequest request
    ) {
        IssueDebitCardRequest req = request != null ? request : new IssueDebitCardRequest(null, null);
        CardResponse response = cardService.issueDebitCard(principal.id(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/credit/apply")
    @Operation(summary = "Apply for digital credit card / Pockt Postpaid with instant credit limit")
    public ResponseEntity<ApiResponse<CreditAccountResponse>> applyCreditCard(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) ApplyCreditCardRequest request
    ) {
        ApplyCreditCardRequest req = request != null ? request : new ApplyCreditCardRequest(null);
        CreditAccountResponse response = cardService.applyCreditCard(principal.id(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "List all cards for current user")
    public ResponseEntity<ApiResponse<List<CardResponse>>> getUserCards(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<CardResponse> cards = cardService.getUserCards(principal.id());
        return ResponseEntity.ok(ApiResponse.success(cards));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get masked details for a specific card")
    public ResponseEntity<ApiResponse<CardResponse>> getCard(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        CardResponse response = cardService.getCard(principal.id(), id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/reveal")
    @Operation(summary = "Reveal full 16-digit card number and CVV using wallet PIN")
    public ResponseEntity<ApiResponse<CardRevealResponse>> revealCard(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody RevealCardRequest request
    ) {
        CardRevealResponse response = cardService.revealCard(principal.id(), id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{id}/settings")
    @Operation(summary = "Update card online payments toggle and daily limits")
    public ResponseEntity<ApiResponse<CardResponse>> updateSettings(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCardSettingsRequest request
    ) {
        CardResponse response = cardService.updateSettings(principal.id(), id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/freeze")
    @Operation(summary = "Toggle freeze/unfreeze status of card")
    public ResponseEntity<ApiResponse<CardResponse>> toggleFreeze(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        CardResponse response = cardService.toggleFreeze(principal.id(), id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/charge")
    @Operation(summary = "Simulate merchant payment / online card charge")
    public ResponseEntity<ApiResponse<CardChargeResponse>> chargeCard(
            @PathVariable UUID id,
            @Valid @RequestBody CardChargeRequest request
    ) {
        CardChargeResponse response = cardService.simulateCardCharge(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/credit/account")
    @Operation(summary = "Get current user's credit account and billing status")
    public ResponseEntity<ApiResponse<CreditAccountResponse>> getCreditAccount(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        CreditAccountResponse response = cardService.getCreditAccount(principal.id());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/credit/repay")
    @Operation(summary = "Repay outstanding credit card bill from wallet balance")
    public ResponseEntity<ApiResponse<CreditAccountResponse>> repayCredit(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody RepayCreditRequest request
    ) {
        CreditAccountResponse response = cardService.repayCredit(principal.id(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
