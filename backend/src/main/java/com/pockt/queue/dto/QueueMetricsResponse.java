package com.pockt.queue.dto;

public record QueueMetricsResponse(
    long pendingCount,
    long deliveredCount,
    long deadLetterCount
) {}
