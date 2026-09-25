package com.pockt.queue.api;

import com.pockt.infrastructure.web.ApiResponse;
import com.pockt.queue.dto.DeadLetterItemResponse;
import com.pockt.queue.dto.QueueMetricsResponse;
import com.pockt.queue.service.QueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/queue")
@Tag(name = "Admin - Queue & DLQ Management", description = "Monitor async job queues, inspect dead letters, and trigger replay retries")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class QueueAdminController {

    private final QueueService queueService;

    public QueueAdminController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping("/metrics")
    @Operation(summary = "Get queue throughput and status metrics (Pending, Delivered, Dead-Letter)")
    public ResponseEntity<ApiResponse<QueueMetricsResponse>> getMetrics() {
        return ResponseEntity.ok(ApiResponse.ok(queueService.getMetrics()));
    }

    @GetMapping("/dead-letter")
    @Operation(summary = "List all messages in the Dead Letter Queue (DLQ)")
    public ResponseEntity<ApiResponse<List<DeadLetterItemResponse>>> getDeadLetters(
            @RequestParam(required = false, defaultValue = "20") int limit,
            @RequestParam(required = false, defaultValue = "0") int offset) {
        return ResponseEntity.ok(ApiResponse.ok(queueService.getDeadLetters(limit, offset)));
    }

    @PostMapping("/dead-letter/{id}/retry")
    @Operation(summary = "Retry a specific dead-letter message")
    public ResponseEntity<ApiResponse<Map<String, Object>>> retryDeadLetter(@PathVariable UUID id) {
        boolean retried = queueService.retryDeadLetter(id);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", id, "retried", retried)));
    }

    @PostMapping("/dead-letter/retry-all")
    @Operation(summary = "Bulk retry all messages in the Dead Letter Queue")
    public ResponseEntity<ApiResponse<Map<String, Object>>> retryAllDeadLetters() {
        int count = queueService.retryAllDeadLetters();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("requeuedCount", count)));
    }
}
