package com.pockt.upi.dto;

public record VerifyVpaResponse(
    String vpa,
    String accountHolderName,
    boolean isValid
) {}
