package com.pockt.upi.repository;

import com.pockt.infrastructure.persistence.BaseRepository;
import com.pockt.upi.domain.UpiHandle;
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
public class UpiRepository extends BaseRepository {

    private final RowMapper<UpiHandle> mapper = (rs, rowNum) -> new UpiHandle(
            getUuid(rs, "id"),
            getUuid(rs, "user_id"),
            rs.getString("vpa"),
            getUuid(rs, "linked_wallet_id"),
            rs.getBoolean("is_default"),
            getInstant(rs, "created_at")
    );

    public UpiRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    public void create(UpiHandle handle) {
        String sql = """
            INSERT INTO upi_handles (id, user_id, vpa, linked_wallet_id, is_default, created_at)
            VALUES (:id, :userId, :vpa, :linkedWalletId, :isDefault, :createdAt)
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", handle.id())
                .addValue("userId", handle.userId())
                .addValue("vpa", handle.vpa().toLowerCase())
                .addValue("linkedWalletId", handle.linkedWalletId())
                .addValue("isDefault", handle.isDefault())
                .addValue("createdAt", Timestamp.from(handle.createdAt()));

        jdbc.update(sql, params);
    }

    public Optional<UpiHandle> findByVpa(String vpa) {
        String sql = "SELECT * FROM upi_handles WHERE LOWER(vpa) = LOWER(:vpa)";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, Map.of("vpa", vpa.toLowerCase()), mapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<UpiHandle> findDefaultByUserId(UUID userId) {
        String sql = "SELECT * FROM upi_handles WHERE user_id = :userId AND is_default = TRUE LIMIT 1";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, Map.of("userId", userId), mapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<UpiHandle> findByUserId(UUID userId) {
        String sql = "SELECT * FROM upi_handles WHERE user_id = :userId ORDER BY is_default DESC, created_at ASC";
        return jdbc.query(sql, Map.of("userId", userId), mapper);
    }

    public boolean existsByVpa(String vpa) {
        String sql = "SELECT COUNT(*) FROM upi_handles WHERE LOWER(vpa) = LOWER(:vpa)";
        Integer count = jdbc.queryForObject(sql, Map.of("vpa", vpa.toLowerCase()), Integer.class);
        return count != null && count > 0;
    }

    public void setDefault(UUID id, UUID userId) {
        jdbc.update("UPDATE upi_handles SET is_default = FALSE WHERE user_id = :userId", Map.of("userId", userId));
        jdbc.update("UPDATE upi_handles SET is_default = TRUE WHERE id = :id AND user_id = :userId",
                Map.of("id", id, "userId", userId));
    }
}
