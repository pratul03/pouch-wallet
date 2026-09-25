package com.pockt.receipt.api;

import com.pockt.infrastructure.security.UserPrincipal;
import com.pockt.infrastructure.web.ApiResponse;
import com.pockt.notification.service.NotificationService;
import com.pockt.receipt.dto.ReceiptResponse;
import com.pockt.receipt.service.ReceiptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Receipt Generator & Emailing", description = "Official digital payment receipts, HTML invoice rendering, verification, and email dispatch with attachments")
public class ReceiptController {

    private final ReceiptService receiptService;
    private final NotificationService notificationService;

    public ReceiptController(ReceiptService receiptService, NotificationService notificationService) {
        this.receiptService = receiptService;
        this.notificationService = notificationService;
    }

    @GetMapping("/transfers/{id}/receipt")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Get official transaction receipt JSON with embedded print-ready HTML")
    public ResponseEntity<ApiResponse<ReceiptResponse>> getTransferReceipt(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        ReceiptResponse response = receiptService.generateTransferReceipt(principal.id(), id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping(value = "/transfers/{id}/receipt/html", produces = MediaType.TEXT_HTML_VALUE)
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Render print-ready HTML receipt directly in browser")
    public ResponseEntity<String> renderTransferReceiptHtml(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        ReceiptResponse response = receiptService.generateTransferReceipt(principal.id(), id);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(response.htmlReceipt());
    }

    @PostMapping("/transfers/{id}/send-receipt-email")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Send receipt email with HTML invoice attachment to specified email address")
    public ResponseEntity<ApiResponse<Map<String, String>>> sendTransferReceiptEmail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam String email) {
        ReceiptResponse receipt = receiptService.generateTransferReceipt(principal.id(), id);
        byte[] attachmentBytes = receipt.htmlReceipt().getBytes(StandardCharsets.UTF_8);
        String subject = "Payment Receipt: " + receipt.receiptId() + " (" + receipt.formattedAmount() + ")";
        String filename = "receipt-" + receipt.receiptId() + ".html";

        notificationService.sendEmailReceipt(
                principal.id(),
                email.trim(),
                subject,
                receipt.htmlReceipt(),
                filename,
                attachmentBytes
        );

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "status", "DISPATCHED",
                "recipient", email.trim(),
                "receiptId", receipt.receiptId()
        )));
    }

    @GetMapping("/bills/{id}/receipt")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Get official utility bill payment receipt")
    public ResponseEntity<ApiResponse<ReceiptResponse>> getBillReceipt(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        ReceiptResponse response = receiptService.generateBillReceipt(principal.id(), id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/bills/{id}/send-receipt-email")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Send utility bill receipt email with HTML invoice attachment")
    public ResponseEntity<ApiResponse<Map<String, String>>> sendBillReceiptEmail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam String email) {
        ReceiptResponse receipt = receiptService.generateBillReceipt(principal.id(), id);
        byte[] attachmentBytes = receipt.htmlReceipt().getBytes(StandardCharsets.UTF_8);
        String subject = "Utility Bill Payment Receipt: " + receipt.receiptId();
        String filename = "bill-receipt-" + receipt.receiptId() + ".html";

        notificationService.sendEmailReceipt(
                principal.id(),
                email.trim(),
                subject,
                receipt.htmlReceipt(),
                filename,
                attachmentBytes
        );

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "status", "DISPATCHED",
                "recipient", email.trim(),
                "receiptId", receipt.receiptId()
        )));
    }

    @GetMapping("/receipts/{receiptId}/verify")
    @Operation(summary = "Verify authenticity of a receipt ID against the immutable ledger")
    public ResponseEntity<ApiResponse<ReceiptResponse>> verifyReceipt(
            @PathVariable String receiptId) {
        ReceiptResponse response = receiptService.verifyReceipt(receiptId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
