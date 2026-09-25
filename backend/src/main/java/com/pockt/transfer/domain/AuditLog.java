package com.pockt.transfer.domain;

import java.time.Instant;
import java.util.UUID;

public record AuditLog(
    UUID id,
    String entityType,
    UUID entityId,
    String action,
    UUID actorId,
    String oldValue,
    String newValue,
    String ipAddress,
    Instant createdAt
) {}
