package com.pockt.user.repository;

import com.pockt.infrastructure.persistence.BaseRepository;
import com.pockt.user.domain.OtpPurpose;
import com.pockt.user.domain.OtpVerification;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class OtpRepository extends BaseRepository {

    private final RowMapper<OtpVerification> otpMapper = (rs, rowNum) -> new OtpVerification(
            getUuid(rs, "id"),
            rs.getString("phone"),
            rs.getString("otp_hash"),
            OtpPurpose.valueOf(rs.getString("purpose")),
            getInstant(rs, "expires_at"),
            rs.getBoolean("used"),
            getInstant(rs, "created_at")
    );

    public OtpRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    public void save(OtpVerification otp) {
        String sql = """
            INSERT INTO otp_verifications (id, phone, otp_hash, purpose, expires_at, used, created_at)
            VALUES (:id, :phone, :otpHash, :purpose, :expiresAt, :used, :createdAt)
            """;
        Map<String, Object> params = Map.of(
                "id", otp.id(),
                "phone", otp.phone(),
                "otpHash", otp.otpHash(),
                "purpose", otp.purpose().name(),
                "expiresAt", Timestamp.from(otp.expiresAt()),
                "used", otp.used(),
                "createdAt", Timestamp.from(otp.createdAt())
        );
        jdbc.update(sql, params);
    }

    public Optional<OtpVerification> findLatestUnused(String phone, OtpPurpose purpose) {
        String sql = """
            SELECT id, phone, otp_hash, purpose, expires_at, used, created_at
            FROM otp_verifications
            WHERE phone = :phone AND purpose = :purpose AND used = FALSE
            ORDER BY created_at DESC
            LIMIT 1
            """;
        try {
            OtpVerification otp = jdbc.queryForObject(sql, Map.of("phone", phone, "purpose", purpose.name()), otpMapper);
            return Optional.ofNullable(otp);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void markUsed(UUID id) {
        String sql = "UPDATE otp_verifications SET used = TRUE WHERE id = :id";
        jdbc.update(sql, Map.of("id", id));
    }
}
