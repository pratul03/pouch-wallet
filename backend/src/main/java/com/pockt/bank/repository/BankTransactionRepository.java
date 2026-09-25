package com.pockt.bank.repository;

import com.pockt.bank.domain.BankTransaction;
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
public class BankTransactionRepository extends BaseRepository {

    private final RowMapper<BankTransaction> mapper = (rs, rowNum) -> new BankTransaction(
            getUuid(rs, "id"),
            getUuid(rs, "bank_account_id"),
            getUuid(rs, "wallet_id"),
            rs.getString("type"),
            rs.getLong("amount"),
            rs.getString("currency").trim(),
            rs.getString("status"),
            rs.getString("reference_number"),
            getInstant(rs, "created_at")
    );

    public BankTransactionRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    public void create(BankTransaction tx) {
        String sql = """
            INSERT INTO bank_transactions (id, bank_account_id, wallet_id, type, amount, currency, status, reference_number, created_at)
            VALUES (:id, :bankAccountId, :walletId, :type, :amount, :currency, :status, :referenceNumber, :createdAt)
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", tx.id())
                .addValue("bankAccountId", tx.bankAccountId())
                .addValue("walletId", tx.walletId())
                .addValue("type", tx.type())
                .addValue("amount", tx.amount())
                .addValue("currency", tx.currency())
                .addValue("status", tx.status())
                .addValue("referenceNumber", tx.referenceNumber())
                .addValue("createdAt", Timestamp.from(tx.createdAt()));

        jdbc.update(sql, params);
    }

    public Optional<BankTransaction> findById(UUID id) {
        String sql = "SELECT * FROM bank_transactions WHERE id = :id";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, Map.of("id", id), mapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<BankTransaction> findByBankAccountId(UUID bankAccountId, int limit) {
        String sql = "SELECT * FROM bank_transactions WHERE bank_account_id = :bankAccountId ORDER BY created_at DESC LIMIT :limit";
        return jdbc.query(sql, Map.of("bankAccountId", bankAccountId, "limit", limit), mapper);
    }

    public List<BankTransaction> findByWalletId(UUID walletId, int limit) {
        String sql = "SELECT * FROM bank_transactions WHERE wallet_id = :walletId ORDER BY created_at DESC LIMIT :limit";
        return jdbc.query(sql, Map.of("walletId", walletId, "limit", limit), mapper);
    }
}
