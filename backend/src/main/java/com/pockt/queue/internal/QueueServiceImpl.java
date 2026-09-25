package com.pockt.queue.internal;

import com.pockt.queue.dto.DeadLetterItemResponse;
import com.pockt.queue.dto.QueueMetricsResponse;
import com.pockt.queue.service.QueueService;
import com.pockt.transfer.repository.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class QueueServiceImpl implements QueueService {

    private final OutboxRepository outboxRepository;

    public QueueServiceImpl(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public QueueMetricsResponse getMetrics() {
        long pending = outboxRepository.countByStatus("PENDING");
        long delivered = outboxRepository.countByStatus("DELIVERED");
        long deadLetter = outboxRepository.countByStatus("DEAD_LETTER");
        return new QueueMetricsResponse(pending, delivered, deadLetter);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeadLetterItemResponse> getDeadLetters(int limit, int offset) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int safeOffset = Math.max(offset, 0);
        return outboxRepository.findDeadLetters(safeLimit, safeOffset).stream()
                .map(DeadLetterItemResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public boolean retryDeadLetter(UUID id) {
        return outboxRepository.retryDeadLetter(id);
    }

    @Override
    @Transactional
    public int retryAllDeadLetters() {
        return outboxRepository.retryAllDeadLetters();
    }
}
