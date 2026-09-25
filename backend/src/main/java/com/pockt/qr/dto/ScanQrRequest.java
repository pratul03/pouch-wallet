package com.pockt.qr.dto;

import jakarta.validation.constraints.NotBlank;

public record ScanQrRequest(
    @NotBlank(message = "QR raw payload or string is required")
    String qrData
) {}
