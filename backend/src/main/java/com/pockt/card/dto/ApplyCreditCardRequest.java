package com.pockt.card.dto;

public record ApplyCreditCardRequest(
    String network // RUPAY, VISA, MASTERCARD
) {}
