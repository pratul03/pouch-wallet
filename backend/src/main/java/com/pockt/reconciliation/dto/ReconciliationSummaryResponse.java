package com.pockt.reconciliation.dto;

import java.time.Instant;

public record ReconciliationSummaryResponse(
    int scannedCount,
    int autoReconciledCount,
    int refundedCount,
    Instant timestamp
) {}
