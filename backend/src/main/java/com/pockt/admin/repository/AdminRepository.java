package com.pockt.admin.repository;

import com.pockt.infrastructure.persistence.BaseRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Repository
public class AdminRepository extends BaseRepository {

    public AdminRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    public record VolumeMetric(long amount, int count) {}

    public VolumeMetric getOutgoingTransferMetric(UUID userId, Instant from, Instant to) {
        String sql = """
            SELECT COALESCE(SUM(t.amount), 0) AS total_amount, COUNT(t.id) AS tx_count
            FROM transactions t
            JOIN wallets w ON t.sender_wallet_id = w.id
            WHERE w.user_id = :userId
              AND t.status = 'COMPLETED'
              AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR t.created_at >= CAST(:from AS TIMESTAMPTZ))
              AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR t.created_at <= CAST(:to AS TIMESTAMPTZ))
            """;
        MapSqlParameterSource params = buildDateParams(from, to)
                .addValue("userId", userId);

        return jdbc.queryForObject(sql, params, (rs, rowNum) ->
                new VolumeMetric(rs.getLong("total_amount"), rs.getInt("tx_count")));
    }

    public VolumeMetric getIncomingTransferMetric(UUID userId, Instant from, Instant to) {
        String sql = """
            SELECT COALESCE(SUM(t.amount), 0) AS total_amount, COUNT(t.id) AS tx_count
            FROM transactions t
            JOIN wallets w ON t.receiver_wallet_id = w.id
            WHERE w.user_id = :userId
              AND t.status = 'COMPLETED'
              AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR t.created_at >= CAST(:from AS TIMESTAMPTZ))
              AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR t.created_at <= CAST(:to AS TIMESTAMPTZ))
            """;
        MapSqlParameterSource params = buildDateParams(from, to)
                .addValue("userId", userId);

        return jdbc.queryForObject(sql, params, (rs, rowNum) ->
                new VolumeMetric(rs.getLong("total_amount"), rs.getInt("tx_count")));
    }

    public VolumeMetric getBankMetric(UUID userId, String type, Instant from, Instant to) {
        String sql = """
            SELECT COALESCE(SUM(bt.amount), 0) AS total_amount, COUNT(bt.id) AS tx_count
            FROM bank_transactions bt
            JOIN bank_accounts ba ON bt.bank_account_id = ba.id
            WHERE ba.user_id = :userId
              AND bt.type = :type
              AND bt.status = 'COMPLETED'
              AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR bt.created_at >= CAST(:from AS TIMESTAMPTZ))
              AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR bt.created_at <= CAST(:to AS TIMESTAMPTZ))
            """;
        MapSqlParameterSource params = buildDateParams(from, to)
                .addValue("userId", userId)
                .addValue("type", type);

        return jdbc.queryForObject(sql, params, (rs, rowNum) ->
                new VolumeMetric(rs.getLong("total_amount"), rs.getInt("tx_count")));
    }

    public long getTotalUsers() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM users", Map.of(), Long.class);
        return count != null ? count : 0L;
    }

    public long getNewUsersInPeriod(Instant from, Instant to) {
        String sql = """
            SELECT COUNT(*) FROM users
            WHERE (CAST(:from AS TIMESTAMPTZ) IS NULL OR created_at >= CAST(:from AS TIMESTAMPTZ))
              AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR created_at <= CAST(:to AS TIMESTAMPTZ))
            """;
        MapSqlParameterSource params = buildDateParams(from, to);
        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count != null ? count : 0L;
    }

    public record WalletAgg(long count, long liquidity) {}

    public WalletAgg getWalletMetrics() {
        String sql = "SELECT COUNT(*) AS total_count, COALESCE(SUM(balance), 0) AS total_liquidity FROM wallets WHERE is_active = TRUE";
        return jdbc.queryForObject(sql, Map.of(), (rs, rowNum) ->
                new WalletAgg(rs.getLong("total_count"), rs.getLong("total_liquidity")));
    }

    public VolumeMetric getTransfersInPeriod(Instant from, Instant to) {
        String sql = """
            SELECT COALESCE(SUM(amount), 0) AS total_amount, COUNT(*) AS tx_count
            FROM transactions
            WHERE status = 'COMPLETED'
              AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR created_at >= CAST(:from AS TIMESTAMPTZ))
              AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR created_at <= CAST(:to AS TIMESTAMPTZ))
            """;
        MapSqlParameterSource params = buildDateParams(from, to);
        return jdbc.queryForObject(sql, params, (rs, rowNum) ->
                new VolumeMetric(rs.getLong("total_amount"), rs.getInt("tx_count")));
    }

    public VolumeMetric getBankTransactionsInPeriod(String type, Instant from, Instant to) {
        String sql = """
            SELECT COALESCE(SUM(amount), 0) AS total_amount, COUNT(*) AS tx_count
            FROM bank_transactions
            WHERE type = :type
              AND status = 'COMPLETED'
              AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR created_at >= CAST(:from AS TIMESTAMPTZ))
              AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR created_at <= CAST(:to AS TIMESTAMPTZ))
            """;
        MapSqlParameterSource params = buildDateParams(from, to)
                .addValue("type", type);
        return jdbc.queryForObject(sql, params, (rs, rowNum) ->
                new VolumeMetric(rs.getLong("total_amount"), rs.getInt("tx_count")));
    }

    public record CreditAgg(long count, long totalOutstanding) {}

    public CreditAgg getCreditMetrics() {
        String sql = "SELECT COUNT(*) AS total_count, COALESCE(SUM(current_bill_amount), 0) AS total_bill FROM credit_accounts";
        return jdbc.queryForObject(sql, Map.of(), (rs, rowNum) ->
                new CreditAgg(rs.getLong("total_count"), rs.getLong("total_bill")));
    }

    public void updateWalletActive(UUID walletId, boolean isActive) {
        String sql = "UPDATE wallets SET is_active = :isActive, updated_at = NOW() WHERE id = :id";
        jdbc.update(sql, Map.of("id", walletId, "isActive", isActive));
    }

    private MapSqlParameterSource buildDateParams(Instant from, Instant to) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (from != null) {
            params.addValue("from", Timestamp.from(from), Types.TIMESTAMP);
        } else {
            params.addValue("from", null, Types.TIMESTAMP);
        }

        if (to != null) {
            params.addValue("to", Timestamp.from(to), Types.TIMESTAMP);
        } else {
            params.addValue("to", null, Types.TIMESTAMP);
        }
        return params;
    }
}
