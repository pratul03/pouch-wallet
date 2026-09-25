package com.pockt.bank.repository;

import com.pockt.bank.domain.BankAccount;
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
public class BankAccountRepository extends BaseRepository {

    private final RowMapper<BankAccount> mapper = (rs, rowNum) -> new BankAccount(
            getUuid(rs, "id"),
            getUuid(rs, "user_id"),
            rs.getString("bank_name"),
            rs.getString("account_number"),
            rs.getString("ifsc_code"),
            rs.getString("account_holder_name"),
            rs.getLong("simulated_balance"),
            rs.getBoolean("is_primary"),
            rs.getString("status"),
            getInstant(rs, "created_at"),
            getInstant(rs, "updated_at")
    );

    public BankAccountRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    public void create(BankAccount account) {
        String sql = """
            INSERT INTO bank_accounts (id, user_id, bank_name, account_number, ifsc_code, account_holder_name,
                                       simulated_balance, is_primary, status, created_at, updated_at)
            VALUES (:id, :userId, :bankName, :accountNumber, :ifscCode, :accountHolderName,
                    :simulatedBalance, :isPrimary, :status, :createdAt, :updatedAt)
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", account.id())
                .addValue("userId", account.userId())
                .addValue("bankName", account.bankName())
                .addValue("accountNumber", account.accountNumber())
                .addValue("ifscCode", account.ifscCode())
                .addValue("accountHolderName", account.accountHolderName())
                .addValue("simulatedBalance", account.simulatedBalance())
                .addValue("isPrimary", account.isPrimary())
                .addValue("status", account.status())
                .addValue("createdAt", Timestamp.from(account.createdAt()))
                .addValue("updatedAt", Timestamp.from(account.updatedAt()));

        jdbc.update(sql, params);
    }

    public Optional<BankAccount> findById(UUID id) {
        String sql = "SELECT * FROM bank_accounts WHERE id = :id AND status != 'DELETED'";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, Map.of("id", id), mapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<BankAccount> findByIdForUpdate(UUID id) {
        String sql = "SELECT * FROM bank_accounts WHERE id = :id AND status != 'DELETED' FOR UPDATE";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, Map.of("id", id), mapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<BankAccount> findByUserId(UUID userId) {
        String sql = "SELECT * FROM bank_accounts WHERE user_id = :userId AND status != 'DELETED' ORDER BY is_primary DESC, created_at ASC";
        return jdbc.query(sql, Map.of("userId", userId), mapper);
    }

    public Optional<BankAccount> findPrimaryByUserId(UUID userId) {
        String sql = "SELECT * FROM bank_accounts WHERE user_id = :userId AND is_primary = TRUE AND status != 'DELETED' LIMIT 1";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, Map.of("userId", userId), mapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void updateBalance(UUID id, long newBalance) {
        String sql = "UPDATE bank_accounts SET simulated_balance = :balance, updated_at = NOW() WHERE id = :id";
        jdbc.update(sql, Map.of("id", id, "balance", newBalance));
    }

    public void resetPrimaryForUser(UUID userId) {
        String sql = "UPDATE bank_accounts SET is_primary = FALSE, updated_at = NOW() WHERE user_id = :userId";
        jdbc.update(sql, Map.of("userId", userId));
    }

    public void setPrimary(UUID id, UUID userId) {
        resetPrimaryForUser(userId);
        String sql = "UPDATE bank_accounts SET is_primary = TRUE, updated_at = NOW() WHERE id = :id AND user_id = :userId";
        jdbc.update(sql, Map.of("id", id, "userId", userId));
    }

    public boolean existsByAccountNumberAndIfsc(UUID userId, String accountNumber, String ifscCode) {
        String sql = "SELECT COUNT(*) FROM bank_accounts WHERE user_id = :userId AND account_number = :accountNumber AND ifsc_code = :ifscCode";
        Integer count = jdbc.queryForObject(sql, Map.of(
                "userId", userId,
                "accountNumber", accountNumber,
                "ifscCode", ifscCode
        ), Integer.class);
        return count != null && count > 0;
    }
}
