package com.pockt.card.repository;

import com.pockt.card.domain.Card;
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
public class CardRepository extends BaseRepository {

    private final RowMapper<Card> mapper = (rs, rowNum) -> new Card(
            getUuid(rs, "id"),
            getUuid(rs, "user_id"),
            getUuid(rs, "wallet_id"),
            rs.getString("card_type"),
            rs.getString("card_network"),
            rs.getString("card_number_masked"),
            rs.getString("card_number_full"),
            rs.getInt("expiry_month"),
            rs.getInt("expiry_year"),
            rs.getString("cvv_plain"),
            rs.getString("card_holder_name"),
            rs.getLong("daily_limit_cents"),
            rs.getBoolean("online_enabled"),
            rs.getString("status"),
            getInstant(rs, "created_at"),
            getInstant(rs, "updated_at")
    );

    public CardRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    public void create(Card card) {
        String sql = """
            INSERT INTO cards (id, user_id, wallet_id, card_type, card_network, card_number_masked,
                               card_number_full, expiry_month, expiry_year, cvv_plain, card_holder_name,
                               daily_limit_cents, online_enabled, status, created_at, updated_at)
            VALUES (:id, :userId, :walletId, :cardType, :cardNetwork, :cardNumberMasked,
                    :cardNumberFull, :expiryMonth, :expiryYear, :cvvPlain, :cardHolderName,
                    :dailyLimitCents, :onlineEnabled, :status, :createdAt, :updatedAt)
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", card.id())
                .addValue("userId", card.userId())
                .addValue("walletId", card.walletId())
                .addValue("cardType", card.cardType())
                .addValue("cardNetwork", card.cardNetwork())
                .addValue("cardNumberMasked", card.cardNumberMasked())
                .addValue("cardNumberFull", card.cardNumberFull())
                .addValue("expiryMonth", card.expiryMonth())
                .addValue("expiryYear", card.expiryYear())
                .addValue("cvvPlain", card.cvvPlain())
                .addValue("cardHolderName", card.cardHolderName())
                .addValue("dailyLimitCents", card.dailyLimitCents())
                .addValue("onlineEnabled", card.onlineEnabled())
                .addValue("status", card.status())
                .addValue("createdAt", Timestamp.from(card.createdAt()))
                .addValue("updatedAt", Timestamp.from(card.updatedAt()));

        jdbc.update(sql, params);
    }

    public Optional<Card> findById(UUID id) {
        String sql = "SELECT * FROM cards WHERE id = :id";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, Map.of("id", id), mapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<Card> findByIdForUpdate(UUID id) {
        String sql = "SELECT * FROM cards WHERE id = :id FOR UPDATE";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, Map.of("id", id), mapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<Card> findByUserId(UUID userId) {
        String sql = "SELECT * FROM cards WHERE user_id = :userId ORDER BY created_at DESC";
        return jdbc.query(sql, Map.of("userId", userId), mapper);
    }

    public void updateSettings(UUID id, boolean onlineEnabled, long dailyLimitCents) {
        String sql = "UPDATE cards SET online_enabled = :onlineEnabled, daily_limit_cents = :dailyLimit, updated_at = NOW() WHERE id = :id";
        jdbc.update(sql, Map.of("id", id, "onlineEnabled", onlineEnabled, "dailyLimit", dailyLimitCents));
    }

    public void updateStatus(UUID id, String status) {
        String sql = "UPDATE cards SET status = :status, updated_at = NOW() WHERE id = :id";
        jdbc.update(sql, Map.of("id", id, "status", status));
    }
}
