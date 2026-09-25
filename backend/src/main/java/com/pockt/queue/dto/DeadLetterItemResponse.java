package com.pockt.queue.dto;

import com.pockt.transfer.domain.NotificationOutbox;

import java.time.Instant;
import java.util.UUID;

public record DeadLetterItemResponse(
    UUID id,
    UUID userId,
    String type,
    String payload,
    int attempts,
    String lastError,
    Instant deadLetteredAt,
    Instant createdAt
) {
    public static DeadLetterItemResponse from(NotificationOutbox entry) {
        return new DeadLetterItemResponse(
            entry.id(),
            entry.userId(),
            entry.type(),
            entry.payload(),
            entry.attempts(),
            entry.lastError(),
            entry.deadLetteredAt(),
            entry.createdAt()
        );
    }
}
