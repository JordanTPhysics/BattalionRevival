package com.game.server;

import com.game.model.map.GameMap;
import com.game.model.map.TerrainType;
import com.game.model.map.Tile;
import com.game.persistence.MapJsonPersistence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for HTTP endpoints: {@code /api/maps} and {@code /api/matches}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServerRestApiIT {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearSharedMaps() {
        SharedMapTestSupport.clearAllMaps(jdbc);
    }

    private static String newSlug() {
        return "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String minimalPlainsMapJson() {
        GameMap map = new GameMap(10, 10);
        map.setTeamCount(2);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                map.setTile(x, y, new Tile(TerrainType.PLAINS_1));
            }
        }
        return MapJsonPersistence.serialize(map);
    }

    private static HttpEntity<String> jsonBody(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(json, headers);
    }

    @Test
    void maps_list_emptyWhenNoMaps() {
        ResponseEntity<String> res = rest.getForEntity("/api/maps", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isEqualTo("[]");
    }

    @Test
    void maps_download_invalidSlug_returns400() {
        ResponseEntity<String> res = rest.getForEntity("/api/maps/z", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void maps_download_unknown_returns404() {
        ResponseEntity<String> res =
            rest.getForEntity("/api/maps/unknown-map-slug-zz", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void maps_upload_roundTrip_listAndDownload() {
        String slug = newSlug();
        String mapJson = minimalPlainsMapJson();
        String uploadJson = """
            {"slug":"%s","ownerUsername":"tester","mapJson":%s,"schemaVersion":1}
            """.formatted(slug, escapeJsonString(mapJson));

        ResponseEntity<String> post =
            rest.postForEntity("/api/maps", jsonBody(uploadJson), String.class);
        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post.getBody()).isEqualTo("saved");

        ResponseEntity<String> list = rest.getForEntity("/api/maps", String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("\"slug\":\"" + slug + "\"");

        ResponseEntity<String> raw = rest.getForEntity("/api/maps/" + slug, String.class);
        assertThat(raw.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(raw.getBody()).contains("\"width\"");
    }

    @Test
    void maps_upload_missingMapJson_returns400() {
        ResponseEntity<String> res =
            rest.postForEntity("/api/maps", jsonBody("{\"slug\":\"ab\",\"ownerUsername\":\"x\"}"), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void maps_upload_duplicateSlug_returns409() {
        String slug = newSlug();
        String mapJson = minimalPlainsMapJson();
        String uploadJson = """
            {"slug":"%s","ownerUsername":"u","mapJson":%s}
            """.formatted(slug, escapeJsonString(mapJson));
        ResponseEntity<String> first =
            rest.postForEntity("/api/maps", jsonBody(uploadJson), String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> second =
            rest.postForEntity("/api/maps", jsonBody(uploadJson), String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void matches_ensure_missingBodyFields_returns400() {
        ResponseEntity<String> res =
            rest.postForEntity("/api/matches", jsonBody("{}"), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void matches_ensure_invalidMatchId_returns400() {
        String slug = newSlug();
        SharedMapTestSupport.insertMap(jdbc, slug, minimalPlainsMapJson());
        ResponseEntity<String> res = rest.postForEntity(
            "/api/matches",
            jsonBody("{\"matchId\":\"room.one\",\"mapSlug\":\"" + slug + "\"}"),
            String.class
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void matches_ensure_demoMatchId_returns400() {
        String slug = newSlug();
        SharedMapTestSupport.insertMap(jdbc, slug, minimalPlainsMapJson());
        ResponseEntity<String> res = rest.postForEntity(
            "/api/matches",
            jsonBody("{\"matchId\":\"demo\",\"mapSlug\":\"" + slug + "\"}"),
            String.class
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void matches_ensure_unknownSlug_returns404() {
        ResponseEntity<String> res = rest.postForEntity(
            "/api/matches",
            jsonBody("{\"matchId\":\"room-z1\",\"mapSlug\":\"does-not-exist-zz\"}"),
            String.class
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void matches_ensure_createsThenIdempotent() {
        String slug = newSlug();
        SharedMapTestSupport.insertMap(jdbc, slug, minimalPlainsMapJson());
        String matchId = "room-" + newSlug();

        ResponseEntity<String> first = rest.postForEntity(
            "/api/matches",
            jsonBody("{\"matchId\":\"" + matchId + "\",\"mapSlug\":\"" + slug + "\"}"),
            String.class
        );
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody()).isEqualTo("match created");

        ResponseEntity<String> second = rest.postForEntity(
            "/api/matches",
            jsonBody("{\"matchId\":\"" + matchId + "\",\"mapSlug\":\"" + slug + "\"}"),
            String.class
        );
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).isEqualTo("match already existed");
    }

    /** Wrap arbitrary map JSON as a JSON string value (quoted, escaped) for embedding in a request body. */
    private static String escapeJsonString(String raw) {
        String escaped =
            raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
        return "\"" + escaped + "\"";
    }
}
