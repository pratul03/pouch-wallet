package com.pockt.card.dto;

import java.util.UUID;

public record IssueDebitCardRequest(
    String network, // RUPAY, VISA, MASTERCARD
    UUID walletId
) {}
