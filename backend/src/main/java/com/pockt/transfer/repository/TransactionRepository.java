package com.pockt.transfer.repository;

import com.pockt.infrastructure.persistence.BaseRepository;
import com.pockt.transfer.domain.Transaction;
import com.pockt.transfer.domain.TransactionStatus;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TransactionRepository extends BaseRepository {

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

    public TransactionRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    public void save(Transaction tx) {
        String sql = """
            INSERT INTO transactions (
                id, idempotency_key, sender_wallet_id, receiver_wallet_id,
                amount, currency, status, description, failure_reason, category, created_at, updated_at
            )
            VALUES (
                :id, :idempotencyKey, :senderWalletId, :receiverWalletId,
                :amount, :currency, :status, :description, :failureReason, :category, :createdAt, :updatedAt
            )
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", tx.id())
                .addValue("idempotencyKey", tx.idempotencyKey())
                .addValue("senderWalletId", tx.senderWalletId())
                .addValue("receiverWalletId", tx.receiverWalletId())
                .addValue("amount", tx.amount())
                .addValue("currency", tx.currency())
                .addValue("status", tx.status().name())
                .addValue("description", tx.description())
                .addValue("failureReason", tx.failureReason())
                .addValue("category", tx.category() != null ? tx.category() : "TRANSFER")
                .addValue("createdAt", Timestamp.from(tx.createdAt()))
                .addValue("updatedAt", Timestamp.from(tx.updatedAt()));
        jdbc.update(sql, params);
    }

    public Optional<Transaction> findById(UUID id) {
        String sql = """
            SELECT id, idempotency_key, sender_wallet_id, receiver_wallet_id,
                   amount, currency, status, description, failure_reason, category, created_at, updated_at
            FROM transactions
            WHERE id = :id
            """;
        try {
            Transaction tx = jdbc.queryForObject(sql, Map.of("id", id), txMapper);
            return Optional.ofNullable(tx);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<Transaction> findByIdempotencyKey(String idempotencyKey) {
        String sql = """
            SELECT id, idempotency_key, sender_wallet_id, receiver_wallet_id,
                   amount, currency, status, description, failure_reason, category, created_at, updated_at
            FROM transactions
            WHERE idempotency_key = :idempotencyKey
            """;
        try {
            Transaction tx = jdbc.queryForObject(sql, Map.of("idempotencyKey", idempotencyKey), txMapper);
            return Optional.ofNullable(tx);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<Transaction> findByWalletIdCursor(UUID walletId, Instant cursorTime, UUID cursorId, int limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT id, idempotency_key, sender_wallet_id, receiver_wallet_id,
                   amount, currency, status, description, failure_reason, category, created_at, updated_at
            FROM transactions
            WHERE (sender_wallet_id = :walletId OR receiver_wallet_id = :walletId)
            """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("walletId", walletId)
                .addValue("limit", limit);

        if (cursorTime != null && cursorId != null) {
            sql.append(" AND (created_at < :cursorTime OR (created_at = :cursorTime AND id < :cursorId))");
            params.addValue("cursorTime", Timestamp.from(cursorTime));
            params.addValue("cursorId", cursorId);
        }

        sql.append(" ORDER BY created_at DESC, id DESC LIMIT :limit");

        return jdbc.query(sql.toString(), params, txMapper);
    }
}
