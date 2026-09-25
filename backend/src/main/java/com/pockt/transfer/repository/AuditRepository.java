package com.pockt.transfer.repository;

import com.pockt.infrastructure.persistence.BaseRepository;
import com.pockt.transfer.domain.AuditLog;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;

@Repository
public class AuditRepository extends BaseRepository {

    public AuditRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    public void save(AuditLog log) {
        String sql = """
            INSERT INTO audit_log (id, entity_type, entity_id, action, actor_id, old_value, new_value, ip_address, created_at)
            VALUES (:id, :entityType, :entityId, :action, :actorId, :oldValue::jsonb, :newValue::jsonb, :ipAddress, :createdAt)
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", log.id())
                .addValue("entityType", log.entityType())
                .addValue("entityId", log.entityId())
                .addValue("action", log.action())
                .addValue("actorId", log.actorId())
                .addValue("oldValue", log.oldValue() != null ? log.oldValue() : "{}")
                .addValue("newValue", log.newValue() != null ? log.newValue() : "{}")
                .addValue("ipAddress", log.ipAddress(), Types.OTHER)
                .addValue("createdAt", Timestamp.from(log.createdAt()));

        jdbc.update(sql, params);
    }
}
