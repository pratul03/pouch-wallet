package com.pockt.beneficiary.repository;

import com.pockt.beneficiary.domain.Beneficiary;
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
public class BeneficiaryRepository extends BaseRepository {

    private final RowMapper<Beneficiary> mapper = (rs, rowNum) -> new Beneficiary(
            getUuid(rs, "id"),
            getUuid(rs, "user_id"),
            rs.getString("name"),
            rs.getString("nickname"),
            rs.getString("phone"),
            rs.getString("vpa"),
            rs.getString("account_number"),
            rs.getString("ifsc_code"),
            rs.getBoolean("is_favorite"),
            getInstant(rs, "created_at"),
            getInstant(rs, "updated_at")
    );

    public BeneficiaryRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    public void save(Beneficiary b) {
        String sql = """
            INSERT INTO beneficiaries (
                id, user_id, name, nickname, phone, vpa,
                account_number, ifsc_code, is_favorite, created_at, updated_at
            )
            VALUES (
                :id, :userId, :name, :nickname, :phone, :vpa,
                :accountNumber, :ifscCode, :isFavorite, :createdAt, :updatedAt
            )
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", b.id())
                .addValue("userId", b.userId())
                .addValue("name", b.name())
                .addValue("nickname", b.nickname())
                .addValue("phone", b.phone())
                .addValue("vpa", b.vpa())
                .addValue("accountNumber", b.accountNumber())
                .addValue("ifscCode", b.ifscCode())
                .addValue("isFavorite", b.isFavorite())
                .addValue("createdAt", Timestamp.from(b.createdAt()))
                .addValue("updatedAt", Timestamp.from(b.updatedAt()));
        jdbc.update(sql, params);
    }

    public Optional<Beneficiary> findById(UUID id) {
        String sql = "SELECT * FROM beneficiaries WHERE id = :id";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, Map.of("id", id), mapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<Beneficiary> findByUserId(UUID userId) {
        String sql = """
            SELECT * FROM beneficiaries
            WHERE user_id = :userId
            ORDER BY is_favorite DESC, created_at DESC
            """;
        return jdbc.query(sql, Map.of("userId", userId), mapper);
    }

    public List<Beneficiary> findFavoritesByUserId(UUID userId) {
        String sql = """
            SELECT * FROM beneficiaries
            WHERE user_id = :userId AND is_favorite = TRUE
            ORDER BY created_at DESC
            """;
        return jdbc.query(sql, Map.of("userId", userId), mapper);
    }

    public boolean deleteById(UUID id, UUID userId) {
        String sql = "DELETE FROM beneficiaries WHERE id = :id AND user_id = :userId";
        int rows = jdbc.update(sql, Map.of("id", id, "userId", userId));
        return rows > 0;
    }

    public boolean updateFavorite(UUID id, UUID userId, boolean isFavorite) {
        String sql = """
            UPDATE beneficiaries
            SET is_favorite = :isFavorite, updated_at = NOW()
            WHERE id = :id AND user_id = :userId
            """;
        int rows = jdbc.update(sql, Map.of("id", id, "userId", userId, "isFavorite", isFavorite));
        return rows > 0;
    }
}
