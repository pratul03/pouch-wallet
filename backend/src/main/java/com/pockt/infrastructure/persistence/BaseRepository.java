package com.pockt.infrastructure.persistence;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

public abstract class BaseRepository {

    protected final NamedParameterJdbcTemplate jdbc;

    protected BaseRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    protected UUID getUuid(ResultSet rs, String column) throws SQLException {
        String val = rs.getString(column);
        return val != null ? UUID.fromString(val) : null;
    }

    protected Instant getInstant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts != null ? ts.toInstant() : null;
    }
}
