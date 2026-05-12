package com.game.server.maps;

import com.game.model.map.GameMap;
import com.game.persistence.MapJsonPersistence;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Persists uploaded maps in PostgreSQL (see {@code schema.sql} / {@code sql/shared_maps.sql}).
 */
@Component
public class SharedMapStore {

    private final JdbcTemplate jdbc;

    public SharedMapStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean slugExists(String slug) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM shared_map WHERE slug = ?",
            Integer.class,
            slug
        );
        return count != null && count > 0;
    }

    public void saveUpload(String slug, String ownerUsername, String mapJson, int schemaVersion) {
        String owner = ownerUsername == null || ownerUsername.isBlank() ? "anonymous" : ownerUsername.trim();
        try {
            jdbc.update(
                """
                INSERT INTO shared_map (slug, owner_username, schema_version, map_json, created_at)
                VALUES (?,?,?,?,?)
                """,
                slug,
                owner,
                schemaVersion,
                mapJson,
                Timestamp.from(Instant.now())
            );
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("slug already exists");
        }
    }

    public String readMapJson(String slug) throws IOException {
        List<String> rows = jdbc.query(
            "SELECT map_json FROM shared_map WHERE slug = ?",
            (rs, rowNum) -> rs.getString(1),
            slug
        );
        if (rows.isEmpty()) {
            throw new IOException("map not found");
        }
        return rows.get(0);
    }

    public GameMap loadMap(String slug) {
        try {
            return MapJsonPersistence.parse(readMapJson(slug));
        } catch (IOException e) {
            throw new IllegalArgumentException("map not found: " + slug, e);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    public List<SharedMapContracts.MapSummary> listSummaries() {
        return jdbc.query(
            """
            SELECT id, slug, owner_username, schema_version, created_at
            FROM shared_map
            ORDER BY LOWER(slug)
            """,
            (rs, rowNum) -> new SharedMapContracts.MapSummary(
                rs.getLong("id"),
                rs.getString("slug"),
                rs.getString("owner_username"),
                rs.getInt("schema_version"),
                rs.getTimestamp("created_at").toInstant().toString()
            )
        );
    }
}
