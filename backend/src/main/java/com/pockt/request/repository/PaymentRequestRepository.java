package com.pockt.request.repository;

import com.pockt.infrastructure.persistence.BaseRepository;
import com.pockt.request.domain.PaymentRequest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PaymentRequestRepository extends BaseRepository {

    private final RowMapper<PaymentRequest> mapper = (rs, rowNum) -> new PaymentRequest(
            getUuid(rs, "id"),
            getUuid(rs, "requester_user_id"),
            getUuid(rs, "payer_user_id"),
            rs.getString("payer_phone"),
            rs.getString("payer_vpa"),
            rs.getLong("amount_cents"),
            rs.getString("note"),
            getUuid(rs, "split_group_id"),
            rs.getString("status"),
            getUuid(rs, "transaction_id"),
            getInstant(rs, "expires_at"),
            getInstant(rs, "created_at"),
            getInstant(rs, "updated_at")
    );

    public PaymentRequestRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    public void save(PaymentRequest req) {
        String sql = """
            INSERT INTO payment_requests (
                id, requester_user_id, payer_user_id, payer_phone, payer_vpa,
                amount_cents, note, split_group_id, status, transaction_id,
                expires_at, created_at, updated_at
            )
            VALUES (
                :id, :requesterUserId, :payerUserId, :payerPhone, :payerVpa,
                :amountCents, :note, :splitGroupId, :status, :transactionId,
                :expiresAt, :createdAt, :updatedAt
            )
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", req.id())
                .addValue("requesterUserId", req.requesterUserId())
                .addValue("payerUserId", req.payerUserId())
                .addValue("payerPhone", req.payerPhone())
                .addValue("payerVpa", req.payerVpa())
                .addValue("amountCents", req.amountCents())
                .addValue("note", req.note())
                .addValue("splitGroupId", req.splitGroupId())
                .addValue("status", req.status())
                .addValue("transactionId", req.transactionId())
                .addValue("expiresAt", Timestamp.from(req.expiresAt()))
                .addValue("createdAt", Timestamp.from(req.createdAt()))
                .addValue("updatedAt", Timestamp.from(req.updatedAt()));
        jdbc.update(sql, params);
    }

    public Optional<PaymentRequest> findById(UUID id) {
        String sql = "SELECT * FROM payment_requests WHERE id = :id";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, Map.of("id", id), mapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<PaymentRequest> findByIdForUpdate(UUID id) {
        String sql = "SELECT * FROM payment_requests WHERE id = :id FOR UPDATE";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, Map.of("id", id), mapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<PaymentRequest> findIncomingRequests(UUID userId, String phone, String vpa) {
        String sql = """
            SELECT * FROM payment_requests
            WHERE (payer_user_id = :userId OR (payer_phone IS NOT NULL AND payer_phone = :phone) OR (payer_vpa IS NOT NULL AND payer_vpa = :vpa))
              AND status = 'PENDING'
              AND expires_at > NOW()
            ORDER BY created_at DESC
            """;
        return jdbc.query(sql, Map.of(
                "userId", userId,
                "phone", phone != null ? phone : "",
                "vpa", vpa != null ? vpa : ""
        ), mapper);
    }

    public List<PaymentRequest> findOutgoingRequests(UUID requesterUserId) {
        String sql = """
            SELECT * FROM payment_requests
            WHERE requester_user_id = :requesterUserId
            ORDER BY created_at DESC
            """;
        return jdbc.query(sql, Map.of("requesterUserId", requesterUserId), mapper);
    }

    public List<PaymentRequest> findBySplitGroupId(UUID splitGroupId) {
        String sql = """
            SELECT * FROM payment_requests
            WHERE split_group_id = :splitGroupId
            ORDER BY created_at ASC
            """;
        return jdbc.query(sql, Map.of("splitGroupId", splitGroupId), mapper);
    }

    public void updateStatus(UUID id, String status, UUID transactionId) {
        String sql = """
            UPDATE payment_requests
            SET status = :status, transaction_id = :transactionId, updated_at = NOW()
            WHERE id = :id
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", status)
                .addValue("transactionId", transactionId);
        jdbc.update(sql, params);
    }
}
