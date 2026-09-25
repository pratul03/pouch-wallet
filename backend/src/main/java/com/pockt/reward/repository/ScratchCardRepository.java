package com.pockt.reward.repository;

import com.pockt.infrastructure.persistence.BaseRepository;
import com.pockt.reward.domain.ScratchCard;
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
public class ScratchCardRepository extends BaseRepository {

    private final RowMapper<ScratchCard> mapper = (rs, rowNum) -> new ScratchCard(
            getUuid(rs, "id"),
            getUuid(rs, "user_id"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getLong("reward_amount_cents"),
            rs.getBoolean("is_scratched"),
            getInstant(rs, "scratched_at"),
            getUuid(rs, "transaction_id"),
            getInstant(rs, "created_at")
    );

    public ScratchCardRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    public void save(ScratchCard card) {
        String sql = """
            INSERT INTO scratch_cards (
                id, user_id, title, description, reward_amount_cents,
                is_scratched, scratched_at, transaction_id, created_at
            )
            VALUES (
                :id, :userId, :title, :description, :rewardAmountCents,
                :isScratched, :scratchedAt, :transactionId, :createdAt
            )
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", card.id())
                .addValue("userId", card.userId())
                .addValue("title", card.title())
                .addValue("description", card.description())
                .addValue("rewardAmountCents", card.rewardAmountCents())
                .addValue("isScratched", card.isScratched())
                .addValue("scratchedAt", card.scratchedAt() != null ? Timestamp.from(card.scratchedAt()) : null)
                .addValue("transactionId", card.transactionId())
                .addValue("createdAt", Timestamp.from(card.createdAt()));
        jdbc.update(sql, params);
    }

    public Optional<ScratchCard> findById(UUID id) {
        String sql = "SELECT * FROM scratch_cards WHERE id = :id";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, Map.of("id", id), mapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<ScratchCard> findByIdForUpdate(UUID id) {
        String sql = "SELECT * FROM scratch_cards WHERE id = :id FOR UPDATE";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, Map.of("id", id), mapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<ScratchCard> findByUserId(UUID userId) {
        String sql = """
            SELECT * FROM scratch_cards
            WHERE user_id = :userId
            ORDER BY is_scratched ASC, created_at DESC
            """;
        return jdbc.query(sql, Map.of("userId", userId), mapper);
    }

    public List<ScratchCard> findUnscratchedByUserId(UUID userId) {
        String sql = """
            SELECT * FROM scratch_cards
            WHERE user_id = :userId AND is_scratched = FALSE
            ORDER BY created_at DESC
            """;
        return jdbc.query(sql, Map.of("userId", userId), mapper);
    }

    public long getTotalCashbackByUserId(UUID userId) {
        String sql = """
            SELECT COALESCE(SUM(reward_amount_cents), 0)
            FROM scratch_cards
            WHERE user_id = :userId AND is_scratched = TRUE
            """;
        Long sum = jdbc.queryForObject(sql, Map.of("userId", userId), Long.class);
        return sum != null ? sum : 0L;
    }

    public void markScratched(UUID id) {
        String sql = "UPDATE scratch_cards SET is_scratched = TRUE, scratched_at = NOW() WHERE id = :id";
        jdbc.update(sql, Map.of("id", id));
    }
}
