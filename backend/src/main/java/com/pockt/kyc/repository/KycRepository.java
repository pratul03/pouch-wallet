package com.pockt.kyc.repository;

import com.pockt.infrastructure.persistence.BaseRepository;
import com.pockt.kyc.domain.KycVerification;
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
public class KycRepository extends BaseRepository {

    private final RowMapper<KycVerification> mapper = (rs, rowNum) -> new KycVerification(
            getUuid(rs, "id"),
            getUuid(rs, "user_id"),
            rs.getString("id_type"),
            rs.getString("document_number"),
            rs.getString("document_url"),
            rs.getString("status"),
            rs.getString("rejection_reason"),
            getUuid(rs, "reviewed_by"),
            getInstant(rs, "reviewed_at"),
            getInstant(rs, "created_at"),
            getInstant(rs, "updated_at")
    );

    public KycRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    public void save(KycVerification kyc) {
        String sql = """
            INSERT INTO kyc_verifications (
                id, user_id, id_type, document_number, document_url,
                status, rejection_reason, reviewed_by, reviewed_at,
                created_at, updated_at
            )
            VALUES (
                :id, :userId, :idType, :documentNumber, :documentUrl,
                :status, :rejectionReason, :reviewedBy, :reviewedAt,
                :createdAt, :updatedAt
            )
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", kyc.id())
                .addValue("userId", kyc.userId())
                .addValue("idType", kyc.idType())
                .addValue("documentNumber", kyc.documentNumber())
                .addValue("documentUrl", kyc.documentUrl())
                .addValue("status", kyc.status())
                .addValue("rejectionReason", kyc.rejectionReason())
                .addValue("reviewedBy", kyc.reviewedBy())
                .addValue("reviewedAt", kyc.reviewedAt() != null ? Timestamp.from(kyc.reviewedAt()) : null)
                .addValue("createdAt", Timestamp.from(kyc.createdAt()))
                .addValue("updatedAt", Timestamp.from(kyc.updatedAt()));
        jdbc.update(sql, params);
    }

    public Optional<KycVerification> findById(UUID id) {
        String sql = "SELECT * FROM kyc_verifications WHERE id = :id";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, Map.of("id", id), mapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<KycVerification> findByUserId(UUID userId) {
        String sql = "SELECT * FROM kyc_verifications WHERE user_id = :userId ORDER BY created_at DESC";
        return jdbc.query(sql, Map.of("userId", userId), mapper);
    }

    public Optional<KycVerification> findLatestByUserId(UUID userId) {
        String sql = "SELECT * FROM kyc_verifications WHERE user_id = :userId ORDER BY created_at DESC LIMIT 1";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, Map.of("userId", userId), mapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<KycVerification> findByStatus(String status) {
        String sql = "SELECT * FROM kyc_verifications WHERE status = :status ORDER BY created_at ASC";
        return jdbc.query(sql, Map.of("status", status), mapper);
    }

    public void updateReview(UUID id, String status, String rejectionReason, UUID reviewedBy) {
        String sql = """
            UPDATE kyc_verifications
            SET status = :status,
                rejection_reason = :rejectionReason,
                reviewed_by = :reviewedBy,
                reviewed_at = NOW(),
                updated_at = NOW()
            WHERE id = :id
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", status)
                .addValue("rejectionReason", rejectionReason)
                .addValue("reviewedBy", reviewedBy);
        jdbc.update(sql, params);
    }
}
