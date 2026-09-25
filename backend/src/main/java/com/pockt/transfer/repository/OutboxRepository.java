package com.pockt.transfer.repository;

import com.pockt.infrastructure.persistence.BaseRepository;
import com.pockt.transfer.domain.NotificationOutbox;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class OutboxRepository extends BaseRepository {

    private final RowMapper<NotificationOutbox> outboxMapper = (rs, rowNum) -> new NotificationOutbox(
            getUuid(rs, "id"),
            getUuid(rs, "user_id"),
            rs.getString("type"),
            rs.getString("payload"),
            rs.getString("status"),
            rs.getInt("attempts"),
            getInstant(rs, "next_retry"),
            rs.getString("last_error"),
            getInstant(rs, "dead_lettered_at"),
            getInstant(rs, "created_at")
    );

    public OutboxRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    public void save(NotificationOutbox entry) {
        String sql = """
            INSERT INTO notification_outbox (id, user_id, type, payload, status, attempts, next_retry, last_error, dead_lettered_at, created_at)
            VALUES (:id, :userId, :type, :payload::jsonb, :status, :attempts, :nextRetry, :lastError, :deadLetteredAt, :createdAt)
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", entry.id())
                .addValue("userId", entry.userId())
                .addValue("type", entry.type())
                .addValue("payload", entry.payload() != null ? entry.payload() : "{}")
                .addValue("status", entry.status())
                .addValue("attempts", entry.attempts())
                .addValue("nextRetry", entry.nextRetry() != null ? Timestamp.from(entry.nextRetry()) : null)
                .addValue("lastError", entry.lastError())
                .addValue("deadLetteredAt", entry.deadLetteredAt() != null ? Timestamp.from(entry.deadLetteredAt()) : null)
                .addValue("createdAt", Timestamp.from(entry.createdAt()));
        jdbc.update(sql, params);
    }

    public List<NotificationOutbox> findPendingForProcessing(int limit) {
        String sql = """
            SELECT id, user_id, type, payload, status, attempts, next_retry, last_error, dead_lettered_at, created_at
            FROM notification_outbox
            WHERE status = 'PENDING' AND (next_retry IS NULL OR next_retry <= NOW())
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """;
        return jdbc.query(sql, Map.of("limit", limit), outboxMapper);
    }

    public void markDelivered(UUID id) {
        String sql = "UPDATE notification_outbox SET status = 'DELIVERED', next_retry = NULL WHERE id = :id";
        jdbc.update(sql, Map.of("id", id));
    }

    public void recordFailure(UUID id, int attempts, Instant nextRetry, String lastError) {
        String sql = """
            UPDATE notification_outbox
            SET attempts = :attempts, next_retry = :nextRetry, last_error = :lastError
            WHERE id = :id
            """;
        jdbc.update(sql, Map.of(
                "id", id,
                "attempts", attempts,
                "nextRetry", nextRetry != null ? Timestamp.from(nextRetry) : java.sql.Types.NULL,
                "lastError", lastError != null ? lastError : ""
        ));
    }

    public void markDeadLetter(UUID id, String lastError) {
        String sql = """
            UPDATE notification_outbox
            SET status = 'DEAD_LETTER', last_error = :lastError, dead_lettered_at = NOW(), next_retry = NULL
            WHERE id = :id
            """;
        jdbc.update(sql, Map.of("id", id, "lastError", lastError != null ? lastError : "Max retry limit exceeded"));
    }

    public List<NotificationOutbox> findDeadLetters(int limit, int offset) {
        String sql = """
            SELECT id, user_id, type, payload, status, attempts, next_retry, last_error, dead_lettered_at, created_at
            FROM notification_outbox
            WHERE status = 'DEAD_LETTER'
            ORDER BY dead_lettered_at DESC
            LIMIT :limit OFFSET :offset
            """;
        return jdbc.query(sql, Map.of("limit", limit, "offset", offset), outboxMapper);
    }

    public long countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM notification_outbox WHERE status = :status";
        Long count = jdbc.queryForObject(sql, Map.of("status", status), Long.class);
        return count != null ? count : 0L;
    }

    public boolean retryDeadLetter(UUID id) {
        String sql = """
            UPDATE notification_outbox
            SET status = 'PENDING', attempts = 0, next_retry = NOW(), last_error = NULL
            WHERE id = :id AND status = 'DEAD_LETTER'
            """;
        int rows = jdbc.update(sql, Map.of("id", id));
        return rows > 0;
    }

    public int retryAllDeadLetters() {
        String sql = """
            UPDATE notification_outbox
            SET status = 'PENDING', attempts = 0, next_retry = NOW(), last_error = NULL
            WHERE status = 'DEAD_LETTER'
            """;
        return jdbc.update(sql, Map.of());
    }
}
