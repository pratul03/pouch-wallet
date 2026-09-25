package com.pockt.bill.dto;

import java.time.LocalDate;

public record BillDetailsResponse(
    String billerId,
    String billerName,
    String category,
    String consumerNumber,
    String customerName,
    long amountCents,
    String currency,
    LocalDate billDate,
    LocalDate dueDate
) {}
