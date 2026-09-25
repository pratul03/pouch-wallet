package com.pockt.bill.dto;

public record BillerResponse(
    String id,
    String name,
    String category,
    String consumerIdLabel,
    String sampleFormat
) {}
