package com.pockt.card.repository;

import com.pockt.card.domain.CreditAccount;
import com.pockt.infrastructure.persistence.BaseRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CreditAccountRepository extends BaseRepository {

    private final RowMapper<CreditAccount> mapper = (rs, rowNum) -> new CreditAccount(
            getUuid(rs, "id"),
            getUuid(rs, "user_id"),
            getUuid(rs, "card_id"),
            rs.getLong("total_credit_limit"),
            rs.getLong("available_credit_limit"),
            rs.getLong("current_bill_amount"),
            rs.getString("status"),
            getInstant(rs, "created_at"),
            getInstant(rs, "updated_at")
    );

    public CreditAccountRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    public void create(CreditAccount account) {
        String sql = """
            INSERT INTO credit_accounts (id, user_id, card_id, total_credit_limit, available_credit_limit,
                                         current_bill_amount, status, created_at, updated_at)
            VALUES (:id, :userId, :cardId, :totalCreditLimit, :availableCreditLimit,
                    :currentBillAmount, :status, :createdAt, :updatedAt)
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", account.id())
                .addValue("userId", account.userId())
                .addValue("cardId", account.cardId())
                .addValue("totalCreditLimit", account.totalCreditLimit())
                .addValue("availableCreditLimit", account.availableCreditLimit())
                .addValue("currentBillAmount", account.currentBillAmount())
                .addValue("status", account.status())
                .addValue("createdAt", Timestamp.from(account.createdAt()))
                .addValue("updatedAt", Timestamp.from(account.updatedAt()));

        jdbc.update(sql, params);
    }

    public Optional<CreditAccount> findByUserId(UUID userId) {
        String sql = "SELECT * FROM credit_accounts WHERE user_id = :userId";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, Map.of("userId", userId), mapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<CreditAccount> findByUserIdForUpdate(UUID userId) {
        String sql = "SELECT * FROM credit_accounts WHERE user_id = :userId FOR UPDATE";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, Map.of("userId", userId), mapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<CreditAccount> findById(UUID id) {
        String sql = "SELECT * FROM credit_accounts WHERE id = :id";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, Map.of("id", id), mapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void updateBalances(UUID id, long availableCreditLimit, long currentBillAmount) {
        String sql = """
            UPDATE credit_accounts
            SET available_credit_limit = :avail, current_bill_amount = :bill, updated_at = NOW()
            WHERE id = :id
            """;
        jdbc.update(sql, Map.of("id", id, "avail", availableCreditLimit, "bill", currentBillAmount));
    }
}
