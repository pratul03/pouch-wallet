package com.pockt.upi.dto;

import com.pockt.upi.domain.UpiHandle;

import java.time.Instant;
import java.util.UUID;

public record UpiHandleResponse(
    UUID id,
    UUID userId,
    String vpa,
    UUID linkedWalletId,
    boolean isDefault,
    Instant createdAt
) {
    public static UpiHandleResponse fromDomain(UpiHandle handle) {
        return new UpiHandleResponse(
                handle.id(),
                handle.userId(),
                handle.vpa(),
                handle.linkedWalletId(),
                handle.isDefault(),
                handle.createdAt()
        );
    }
}
