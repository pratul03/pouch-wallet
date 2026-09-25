package com.pockt.bill.dto;

public record BillerCategoryResponse(
    String category,
    String displayName,
    String description,
    String icon
) {}
