package com.pockt.qr.dto;

public record ScanQrResultResponse(
    String vpa,
    String name,
    Long amount,
    String formattedAmount,
    String note,
    boolean isValid,
    String message
) {}
