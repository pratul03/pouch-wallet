package com.pockt.analytics.repository;

import com.pockt.analytics.dto.CategorySpendingItem;
import com.pockt.infrastructure.persistence.BaseRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class AnalyticsRepository extends BaseRepository {

    public AnalyticsRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    public long getTotalSpent(UUID walletId, Instant start, Instant end) {
        String sql = """
            SELECT COALESCE(SUM(amount), 0)
            FROM transactions
            WHERE sender_wallet_id = :walletId
              AND status = 'COMPLETED'
              AND created_at >= :start AND created_at < :end
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("walletId", walletId)
                .addValue("start", Timestamp.from(start))
                .addValue("end", Timestamp.from(end));
        Long total = jdbc.queryForObject(sql, params, Long.class);
        return total != null ? total : 0L;
    }

    public long getTotalReceived(UUID walletId, Instant start, Instant end) {
        String sql = """
            SELECT COALESCE(SUM(amount), 0)
            FROM transactions
            WHERE receiver_wallet_id = :walletId
              AND status = 'COMPLETED'
              AND created_at >= :start AND created_at < :end
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("walletId", walletId)
                .addValue("start", Timestamp.from(start))
                .addValue("end", Timestamp.from(end));
        Long total = jdbc.queryForObject(sql, params, Long.class);
        return total != null ? total : 0L;
    }

    public List<CategorySpendingItem> getCategoryBreakdown(UUID walletId, Instant start, Instant end, long totalSpent) {
        String sql = """
            SELECT COALESCE(category, 'OTHER') AS cat,
                   COALESCE(SUM(amount), 0) AS total_amount,
                   COUNT(*) AS tx_count
            FROM transactions
            WHERE sender_wallet_id = :walletId
              AND status = 'COMPLETED'
              AND created_at >= :start AND created_at < :end
            GROUP BY COALESCE(category, 'OTHER')
            ORDER BY total_amount DESC
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("walletId", walletId)
                .addValue("start", Timestamp.from(start))
                .addValue("end", Timestamp.from(end));

        return jdbc.query(sql, params, (rs, rowNum) -> {
            String category = rs.getString("cat");
            long amount = rs.getLong("total_amount");
            int count = rs.getInt("tx_count");
            double pct = totalSpent > 0 ? (amount * 100.0 / totalSpent) : 0.0;
            return new CategorySpendingItem(category, amount, Math.round(pct * 10.0) / 10.0, count);
        });
    }
}
