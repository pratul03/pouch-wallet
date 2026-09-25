package com.pockt.infrastructure.scheduler;

import com.pockt.notification.service.NotificationService;
import com.pockt.transfer.domain.NotificationOutbox;
import com.pockt.transfer.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int MAX_ATTEMPTS = 5;

    private final OutboxRepository outboxRepository;
    private final NotificationService notificationService;

    public OutboxPoller(OutboxRepository outboxRepository, NotificationService notificationService) {
        this.outboxRepository = outboxRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelay = 5000)
    public void pollOutbox() {
        List<NotificationOutbox> pending = outboxRepository.findPendingForProcessing(20);
        if (pending.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox notifications to process", pending.size());

        for (NotificationOutbox entry : pending) {
            try {
                notificationService.processOutboxEntry(
                        entry.id(),
                        entry.userId(),
                        entry.type(),
                        entry.payload()
                );
                outboxRepository.markDelivered(entry.id());
                log.debug("Successfully dispatched outbox entry {}", entry.id());
            } catch (Exception e) {
                int nextAttempt = entry.attempts() + 1;
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                if (nextAttempt >= MAX_ATTEMPTS) {
                    outboxRepository.markDeadLetter(entry.id(), errorMsg);
                    log.error("Outbox entry {} reached max attempts ({}). Moved to DEAD_LETTER queue: {}",
                            entry.id(), MAX_ATTEMPTS, errorMsg);
                } else {
                    long backoffSeconds = (long) Math.min(3600, Math.pow(2, nextAttempt) * 5);
                    Instant nextRetry = Instant.now().plusSeconds(backoffSeconds);
                    outboxRepository.recordFailure(entry.id(), nextAttempt, nextRetry, errorMsg);
                    log.warn("Failed to dispatch outbox entry {}, will retry at {}: {}",
                            entry.id(), nextRetry, errorMsg);
                }
            }
        }
    }
}
