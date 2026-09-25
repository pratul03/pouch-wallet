package com.pockt.bill.api;

import com.pockt.bill.dto.BillDetailsResponse;
import com.pockt.bill.dto.BillPaymentResponse;
import com.pockt.bill.dto.BillerCategoryResponse;
import com.pockt.bill.dto.BillerResponse;
import com.pockt.bill.dto.FetchBillRequest;
import com.pockt.bill.dto.PayBillRequest;
import com.pockt.bill.service.BillPaymentService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bills")
@Tag(name = "Utility Bills & Mobile Recharge", description = "Fetch and pay electricity, mobile recharge, broadband, DTH, and water bills")
@SecurityRequirement(name = "BearerAuth")
public class BillController {

    private final BillPaymentService billPaymentService;

    public BillController(BillPaymentService billPaymentService) {
        this.billPaymentService = billPaymentService;
    }

    @GetMapping("/categories")
    @Operation(summary = "Get list of supported bill categories")
    public ResponseEntity<ApiResponse<List<BillerCategoryResponse>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.ok(billPaymentService.getCategories()));
    }

    @GetMapping("/billers")
    @Operation(summary = "Get list of billers, optionally filtered by category")
    public ResponseEntity<ApiResponse<List<BillerResponse>>> getBillers(
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(ApiResponse.ok(billPaymentService.getBillers(category)));
    }

    @PostMapping("/fetch")
    @Operation(summary = "Fetch bill details for consumer number before payment")
    public ResponseEntity<ApiResponse<BillDetailsResponse>> fetchBill(
            @Valid @RequestBody FetchBillRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(billPaymentService.fetchBill(request)));
    }

    @PostMapping("/pay")
    @Operation(summary = "Pay bill using wallet balance")
    public ResponseEntity<ApiResponse<BillPaymentResponse>> payBill(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PayBillRequest request) {
        BillPaymentResponse response = billPaymentService.payBill(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping("/history")
    @Operation(summary = "Get current user's bill payment history")
    public ResponseEntity<ApiResponse<List<BillPaymentResponse>>> getHistory(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(billPaymentService.getUserBillPayments(principal.id())));
    }
}
