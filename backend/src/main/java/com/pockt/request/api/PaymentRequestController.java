package com.pockt.request.api;

import com.pockt.infrastructure.security.UserPrincipal;
import com.pockt.infrastructure.web.ApiResponse;
import com.pockt.request.dto.CreatePaymentRequest;
import com.pockt.request.dto.PaymentRequestResponse;
import com.pockt.request.dto.SplitBillRequest;
import com.pockt.request.service.PaymentRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/payment-requests")
@Tag(name = "Payment Requests & Split Bill", description = "Request money from contacts and split group bills")
@SecurityRequirement(name = "BearerAuth")
public class PaymentRequestController {

    private final PaymentRequestService paymentRequestService;

    public PaymentRequestController(PaymentRequestService paymentRequestService) {
        this.paymentRequestService = paymentRequestService;
    }

    @PostMapping
    @Operation(summary = "Create an individual payment request to a phone or VPA")
    public ResponseEntity<ApiResponse<PaymentRequestResponse>> createRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreatePaymentRequest request) {
        PaymentRequestResponse response = paymentRequestService.createRequest(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PostMapping("/split")
    @Operation(summary = "Create a multi-participant split bill request")
    public ResponseEntity<ApiResponse<List<PaymentRequestResponse>>> createSplitBill(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SplitBillRequest request) {
        List<PaymentRequestResponse> responses = paymentRequestService.createSplitBill(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(responses));
    }

    @GetMapping("/incoming")
    @Operation(summary = "List incoming pending payment requests for the authenticated user")
    public ResponseEntity<ApiResponse<List<PaymentRequestResponse>>> getIncomingRequests(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<PaymentRequestResponse> list = paymentRequestService.getIncomingRequests(principal.id());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/outgoing")
    @Operation(summary = "List outgoing payment requests created by the authenticated user")
    public ResponseEntity<ApiResponse<List<PaymentRequestResponse>>> getOutgoingRequests(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<PaymentRequestResponse> list = paymentRequestService.getOutgoingRequests(principal.id());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment request details by ID")
    public ResponseEntity<ApiResponse<PaymentRequestResponse>> getRequestById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        PaymentRequestResponse response = paymentRequestService.getRequestById(principal.id(), id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Accept incoming payment request and instantly settle via atomic wallet transfer")
    public ResponseEntity<ApiResponse<PaymentRequestResponse>> acceptRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        PaymentRequestResponse response = paymentRequestService.acceptRequest(principal.id(), id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/decline")
    @Operation(summary = "Decline an incoming payment request")
    public ResponseEntity<ApiResponse<PaymentRequestResponse>> declineRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        PaymentRequestResponse response = paymentRequestService.declineRequest(principal.id(), id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a pending outgoing payment request created by the user")
    public ResponseEntity<ApiResponse<PaymentRequestResponse>> cancelRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        PaymentRequestResponse response = paymentRequestService.cancelRequest(principal.id(), id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
