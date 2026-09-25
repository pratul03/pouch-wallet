package com.pockt.reconciliation.repository;

import com.pockt.infrastructure.persistence.BaseRepository;
import com.pockt.transfer.domain.Transaction;
import com.pockt.transfer.domain.TransactionStatus;
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
public class ReconciliationRepository extends BaseRepository {

    private final RowMapper<Transaction> txMapper = (rs, rowNum) -> new Transaction(
            getUuid(rs, "id"),
            rs.getString("idempotency_key"),
            getUuid(rs, "sender_wallet_id"),
            getUuid(rs, "receiver_wallet_id"),
            rs.getLong("amount"),
            rs.getString("currency").trim(),
            TransactionStatus.valueOf(rs.getString("status")),
            rs.getString("description"),
            rs.getString("failure_reason"),
            rs.getString("category") != null ? rs.getString("category") : "TRANSFER",
            getInstant(rs, "created_at"),
            getInstant(rs, "updated_at")
    );

    public ReconciliationRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    public List<Transaction> findStuckPendingTransactions(Instant olderThan, int limit) {
        String sql = """
            SELECT id, idempotency_key, sender_wallet_id, receiver_wallet_id,
                   amount, currency, status, description, failure_reason, category, created_at, updated_at
            FROM transactions
            WHERE status = 'PENDING' AND created_at <= :olderThan
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("olderThan", Timestamp.from(olderThan))
                .addValue("limit", limit);
        return jdbc.query(sql, params, txMapper);
    }

    public List<Transaction> findPendingTransactions(int limit, int offset) {
        String sql = """
            SELECT id, idempotency_key, sender_wallet_id, receiver_wallet_id,
                   amount, currency, status, description, failure_reason, category, created_at, updated_at
            FROM transactions
            WHERE status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT :limit OFFSET :offset
            """;
        return jdbc.query(sql, Map.of("limit", limit, "offset", offset), txMapper);
    }

    public void markReversed(UUID txId, String reason) {
        String sql = """
            UPDATE transactions
            SET status = 'REVERSED', failure_reason = :reason, reversed_at = NOW(), updated_at = NOW()
            WHERE id = :id
            """;
        jdbc.update(sql, Map.of("id", txId, "reason", reason != null ? reason : "Transaction reversed by administrator"));
    }

    public void flagDispute(UUID txId, String reason) {
        String sql = """
            UPDATE transactions
            SET disputed = TRUE, dispute_reason = :reason, updated_at = NOW()
            WHERE id = :id
            """;
        jdbc.update(sql, Map.of("id", txId, "reason", reason));
    }
}
