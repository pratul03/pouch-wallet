package com.pockt.qr.api;

import com.pockt.infrastructure.security.UserPrincipal;
import com.pockt.infrastructure.web.ApiResponse;
import com.pockt.qr.dto.GenerateDynamicQrRequest;
import com.pockt.qr.dto.QrDetailsResponse;
import com.pockt.qr.dto.ScanQrRequest;
import com.pockt.qr.dto.ScanQrResultResponse;
import com.pockt.qr.service.QrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/qr")
@Tag(name = "QR Ecosystem", description = "Generate static/dynamic payment QR codes and scan & resolve QR payloads")
@SecurityRequirement(name = "BearerAuth")
public class QrController {

    private final QrService qrService;

    public QrController(QrService qrService) {
        this.qrService = qrService;
    }

    @GetMapping("/my-qr")
    @Operation(summary = "Get user's personal static UPI QR code payload")
    public ResponseEntity<ApiResponse<QrDetailsResponse>> getMyQr(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        QrDetailsResponse response = qrService.getMyQr(principal.id());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate a dynamic QR code for a specific amount and note")
    public ResponseEntity<ApiResponse<QrDetailsResponse>> generateDynamicQr(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody GenerateDynamicQrRequest request
    ) {
        QrDetailsResponse response = qrService.generateDynamicQr(principal.id(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/scan")
    @Operation(summary = "Scan and resolve raw QR code data (UPI URI or VPA)")
    public ResponseEntity<ApiResponse<ScanQrResultResponse>> scanQr(
            @Valid @RequestBody ScanQrRequest request
    ) {
        ScanQrResultResponse response = qrService.scanAndResolve(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
