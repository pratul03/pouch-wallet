package com.pockt.queue.service;

import com.pockt.queue.dto.DeadLetterItemResponse;
import com.pockt.queue.dto.QueueMetricsResponse;

import java.util.List;
import java.util.UUID;

public interface QueueService {
    QueueMetricsResponse getMetrics();
    List<DeadLetterItemResponse> getDeadLetters(int limit, int offset);
    boolean retryDeadLetter(UUID id);
    int retryAllDeadLetters();
}
