package com.pockt.wallet.repository;

import com.pockt.infrastructure.exception.WalletNotFoundException;
import com.pockt.infrastructure.persistence.BaseRepository;
import com.pockt.wallet.domain.Wallet;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class WalletRepository extends BaseRepository {

    private final RowMapper<Wallet> walletMapper = (rs, rowNum) -> new Wallet(
            getUuid(rs, "id"),
            getUuid(rs, "user_id"),
            rs.getString("currency").trim(),
            rs.getLong("balance"),
            rs.getBoolean("is_active"),
            getInstant(rs, "created_at"),
            getInstant(rs, "updated_at")
    );

    public WalletRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    public void create(Wallet wallet) {
        String sql = """
            INSERT INTO wallets (id, user_id, currency, balance, is_active, created_at, updated_at)
            VALUES (:id, :userId, :currency, :balance, :isActive, :createdAt, :updatedAt)
            """;
        Map<String, Object> params = Map.of(
                "id", wallet.id(),
                "userId", wallet.userId(),
                "currency", wallet.currency(),
                "balance", wallet.balance(),
                "isActive", wallet.isActive(),
                "createdAt", Timestamp.from(wallet.createdAt()),
                "updatedAt", Timestamp.from(wallet.updatedAt())
        );
        jdbc.update(sql, params);
    }

    public Optional<Wallet> findById(UUID id) {
        String sql = """
            SELECT id, user_id, currency, balance, is_active, created_at, updated_at
            FROM wallets
            WHERE id = :id AND is_active = TRUE
            """;
        try {
            Wallet wallet = jdbc.queryForObject(sql, Map.of("id", id), walletMapper);
            return Optional.ofNullable(wallet);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<Wallet> findByUserId(UUID userId) {
        String sql = """
            SELECT id, user_id, currency, balance, is_active, created_at, updated_at
            FROM wallets
            WHERE user_id = :userId AND is_active = TRUE
            ORDER BY created_at ASC
            """;
        return jdbc.query(sql, Map.of("userId", userId), walletMapper);
    }

    public Optional<Wallet> findByUserIdAndCurrency(UUID userId, String currency) {
        String sql = """
            SELECT id, user_id, currency, balance, is_active, created_at, updated_at
            FROM wallets
            WHERE user_id = :userId AND currency = :currency AND is_active = TRUE
            """;
        try {
            Wallet wallet = jdbc.queryForObject(sql, Map.of("userId", userId, "currency", currency), walletMapper);
            return Optional.ofNullable(wallet);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Wallet findByIdForUpdate(UUID walletId) {
        String sql = """
            SELECT id, user_id, currency, balance, is_active, created_at, updated_at
            FROM wallets
            WHERE id = :id AND is_active = TRUE
            FOR UPDATE
            """;
        try {
            return jdbc.queryForObject(sql, Map.of("id", walletId), walletMapper);
        } catch (EmptyResultDataAccessException e) {
            throw new WalletNotFoundException(walletId);
        }
    }

    public void debit(UUID walletId, long amountCents) {
        String sql = """
            UPDATE wallets
            SET balance = balance - :amount, updated_at = NOW()
            WHERE id = :walletId
            """;
        int rows = jdbc.update(sql, Map.of("walletId", walletId, "amount", amountCents));
        if (rows == 0) {
            throw new WalletNotFoundException(walletId);
        }
    }

    public void credit(UUID walletId, long amountCents) {
        String sql = """
            UPDATE wallets
            SET balance = balance + :amount, updated_at = NOW()
            WHERE id = :walletId
            """;
        int rows = jdbc.update(sql, Map.of("walletId", walletId, "amount", amountCents));
        if (rows == 0) {
            throw new WalletNotFoundException(walletId);
        }
    }

    public long getBalance(UUID walletId) {
        String sql = "SELECT balance FROM wallets WHERE id = :id";
        Long balance = jdbc.queryForObject(sql, Map.of("id", walletId), Long.class);
        if (balance == null) {
            throw new WalletNotFoundException(walletId);
        }
        return balance;
    }
}
