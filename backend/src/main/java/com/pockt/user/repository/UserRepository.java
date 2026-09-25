package com.pockt.user.repository;

import com.pockt.infrastructure.persistence.BaseRepository;
import com.pockt.user.domain.User;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository extends BaseRepository {

    private final RowMapper<User> userMapper = (rs, rowNum) -> new User(
            getUuid(rs, "id"),
            rs.getString("phone"),
            rs.getString("full_name"),
            rs.getString("pin_hash"),
            rs.getString("fcm_token"),
            rs.getString("kyc_status"),
            rs.getBoolean("is_active"),
            getInstant(rs, "created_at"),
            getInstant(rs, "updated_at")
    );

    public UserRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    public void create(User user) {
        String sql = """
            INSERT INTO users (id, phone, full_name, pin_hash, fcm_token, kyc_status, is_active, created_at, updated_at)
            VALUES (:id, :phone, :fullName, :pinHash, :fcmToken, :kycStatus, :isActive, :createdAt, :updatedAt)
            """;
        org.springframework.jdbc.core.namedparam.MapSqlParameterSource params =
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("id", user.id())
                .addValue("phone", user.phone())
                .addValue("fullName", user.fullName())
                .addValue("pinHash", user.pinHash())
                .addValue("fcmToken", user.fcmToken())
                .addValue("kycStatus", user.kycStatus())
                .addValue("isActive", user.isActive())
                .addValue("createdAt", java.sql.Timestamp.from(user.createdAt()))
                .addValue("updatedAt", java.sql.Timestamp.from(user.updatedAt()));
        jdbc.update(sql, params);
    }

    public Optional<User> findById(UUID id) {
        String sql = """
            SELECT id, phone, full_name, pin_hash, fcm_token, kyc_status, is_active, created_at, updated_at
            FROM users
            WHERE id = :id AND is_active = TRUE
            """;
        try {
            User user = jdbc.queryForObject(sql, Map.of("id", id), userMapper);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<User> findByPhone(String phone) {
        String sql = """
            SELECT id, phone, full_name, pin_hash, fcm_token, kyc_status, is_active, created_at, updated_at
            FROM users
            WHERE phone = :phone AND is_active = TRUE
            """;
        try {
            User user = jdbc.queryForObject(sql, Map.of("phone", phone), userMapper);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean existsByPhone(String phone) {
        String sql = "SELECT COUNT(*) FROM users WHERE phone = :phone";
        Integer count = jdbc.queryForObject(sql, Map.of("phone", phone), Integer.class);
        return count != null && count > 0;
    }

    public void updateFullName(UUID id, String fullName) {
        String sql = """
            UPDATE users
            SET full_name = :fullName, updated_at = NOW()
            WHERE id = :id
            """;
        jdbc.update(sql, Map.of("id", id, "fullName", fullName));
    }

    public void updateFcmToken(UUID id, String fcmToken) {
        String sql = """
            UPDATE users
            SET fcm_token = :fcmToken, updated_at = NOW()
            WHERE id = :id
            """;
        jdbc.update(sql, Map.of("id", id, "fcmToken", fcmToken));
    }
}
