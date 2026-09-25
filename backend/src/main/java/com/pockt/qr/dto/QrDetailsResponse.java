package com.pockt.qr.dto;

public record QrDetailsResponse(
    String qrPayload,
    String vpa,
    String name,
    Long amount,
    String formattedAmount,
    String note
) {}
