package com.pockt.upi.api;

import com.pockt.infrastructure.security.UserPrincipal;
import com.pockt.infrastructure.web.ApiResponse;
import com.pockt.upi.dto.CreateUpiHandleRequest;
import com.pockt.upi.dto.UpiHandleResponse;
import com.pockt.upi.dto.UpiPaymentRequest;
import com.pockt.upi.dto.UpiPaymentResponse;
import com.pockt.upi.dto.VerifyVpaResponse;
import com.pockt.upi.service.UpiService;
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
@RequestMapping("/api/v1/upi")
@Tag(name = "UPI Engine", description = "Virtual Private Address (VPA) management, verification, and instant UPI transfers")
@SecurityRequirement(name = "BearerAuth")
public class UpiController {

    private final UpiService upiService;

    public UpiController(UpiService upiService) {
        this.upiService = upiService;
    }

    @PostMapping("/handles")
    @Operation(summary = "Create custom UPI handle (e.g. rahul@pockt)")
    public ResponseEntity<ApiResponse<UpiHandleResponse>> createHandle(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateUpiHandleRequest request
    ) {
        UpiHandleResponse response = upiService.createHandle(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/handles")
    @Operation(summary = "Get user's UPI handles (auto-provisions default if none exists)")
    public ResponseEntity<ApiResponse<List<UpiHandleResponse>>> getHandles(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<UpiHandleResponse> handles = upiService.getUserHandles(principal.id());
        if (handles.isEmpty()) {
            handles = List.of(upiService.getOrCreateDefaultHandle(principal.id()));
        }
        return ResponseEntity.ok(ApiResponse.success(handles));
    }

    @GetMapping("/verify")
    @Operation(summary = "Verify a receiver UPI ID before sending money")
    public ResponseEntity<ApiResponse<VerifyVpaResponse>> verifyVpa(
            @RequestParam String vpa
    ) {
        VerifyVpaResponse response = upiService.verifyVpa(vpa);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/pay")
    @Operation(summary = "Execute instant UPI money transfer using VPA and PIN")
    public ResponseEntity<ApiResponse<UpiPaymentResponse>> payViaUpi(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpiPaymentRequest request
    ) {
        UpiPaymentResponse response = upiService.payViaUpi(principal.id(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
