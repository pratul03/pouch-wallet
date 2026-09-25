package com.pockt.upi.domain;

import java.time.Instant;
import java.util.UUID;

public record UpiHandle(
    UUID id,
    UUID userId,
    String vpa,
    UUID linkedWalletId,
    boolean isDefault,
    Instant createdAt
) {}
