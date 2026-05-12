-- Shared user-uploaded maps (Battalion server).
-- Run once against database `battalion` (e.g. Render Postgres SQL shell or: psql -d battalion -f shared_maps.sql).
-- Spring Boot also runs the same DDL from classpath schema.sql on startup when spring.sql.init.mode=always.

CREATE TABLE IF NOT EXISTS shared_map (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(64) NOT NULL UNIQUE,
    owner_username VARCHAR(64) NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    map_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_shared_map_created_at ON shared_map (created_at DESC);
