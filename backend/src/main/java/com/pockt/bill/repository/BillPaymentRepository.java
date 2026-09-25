package com.pockt.bill.repository;

import com.pockt.bill.domain.BillPayment;
import com.pockt.infrastructure.persistence.BaseRepository;
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
public class BillPaymentRepository extends BaseRepository {

    private final RowMapper<BillPayment> mapper = (rs, rowNum) -> new BillPayment(
            getUuid(rs, "id"),
            getUuid(rs, "user_id"),
            getUuid(rs, "wallet_id"),
            rs.getString("category"),
            rs.getString("biller_id"),
            rs.getString("biller_name"),
            rs.getString("consumer_number"),
            rs.getLong("amount_cents"),
            rs.getString("status"),
            rs.getString("reference_number"),
            getUuid(rs, "transaction_id"),
            getInstant(rs, "created_at"),
            getInstant(rs, "updated_at")
    );

    public BillPaymentRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    public void save(BillPayment bp) {
        String sql = """
            INSERT INTO bill_payments (
                id, user_id, wallet_id, category, biller_id, biller_name,
                consumer_number, amount_cents, status, reference_number,
                transaction_id, created_at, updated_at
            )
            VALUES (
                :id, :userId, :walletId, :category, :billerId, :billerName,
                :consumerNumber, :amountCents, :status, :referenceNumber,
                :transactionId, :createdAt, :updatedAt
            )
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", bp.id())
                .addValue("userId", bp.userId())
                .addValue("walletId", bp.walletId())
                .addValue("category", bp.category())
                .addValue("billerId", bp.billerId())
                .addValue("billerName", bp.billerName())
                .addValue("consumerNumber", bp.consumerNumber())
                .addValue("amountCents", bp.amountCents())
                .addValue("status", bp.status())
                .addValue("referenceNumber", bp.referenceNumber())
                .addValue("transactionId", bp.transactionId())
                .addValue("createdAt", Timestamp.from(bp.createdAt()))
                .addValue("updatedAt", Timestamp.from(bp.updatedAt()));
        jdbc.update(sql, params);
    }

    public Optional<BillPayment> findById(UUID id) {
        String sql = "SELECT * FROM bill_payments WHERE id = :id";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, Map.of("id", id), mapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<BillPayment> findByUserId(UUID userId) {
        String sql = """
            SELECT * FROM bill_payments
            WHERE user_id = :userId
            ORDER BY created_at DESC
            """;
        return jdbc.query(sql, Map.of("userId", userId), mapper);
    }
}
