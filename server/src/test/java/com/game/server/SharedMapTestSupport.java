package com.game.server;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test helpers for {@code shared_map} rows (integration tests).
 */
public final class SharedMapTestSupport {

    private SharedMapTestSupport() {
    }

    public static void clearAllMaps(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM shared_map");
    }

    public static void insertMap(JdbcTemplate jdbc, String slug, String mapJson) {
        jdbc.update(
            """
            INSERT INTO shared_map (slug, owner_username, schema_version, map_json, created_at)
            VALUES (?,?,?,?, CURRENT_TIMESTAMP)
            """,
            slug,
            "test",
            1,
            mapJson
        );
    }
}
