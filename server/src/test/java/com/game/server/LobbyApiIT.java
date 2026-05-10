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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LobbyApiIT {

    @TempDir
    static Path sharedMapsDirectory;

    @DynamicPropertySource
    static void registerSharedMapsPath(DynamicPropertyRegistry registry) {
        registry.add(
            "battalion.shared-maps.directory",
            () -> sharedMapsDirectory.toAbsolutePath().toString()
        );
    }

    @Autowired
    private TestRestTemplate rest;

    @BeforeEach
    void clearSharedMaps() {
        if (!Files.isDirectory(sharedMapsDirectory)) {
            return;
        }
        try (Stream<Path> stream = Files.list(sharedMapsDirectory)) {
            stream.forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String mapJson10() {
        GameMap map = new GameMap(10, 10);
        map.setTeamCount(2);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                map.setTile(x, y, new Tile(TerrainType.PLAINS_1));
            }
        }
        return MapJsonPersistence.serialize(map);
    }

    private void writeMap(String slug) {
        try {
            Files.writeString(sharedMapsDirectory.resolve(slug + ".json"), mapJson10());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void lobby_flow_createJoinMapStart() {
        writeMap("lobby-test-map");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> create =
            rest.postForEntity("/api/lobbies", new HttpEntity<>("{}", headers), String.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(create.getBody()).contains("lobbyId");
        assertThat(create.getBody()).contains("playerId");
        assertThat(create.getBody()).contains("\"seatIndex\":0");

        String lobbyId =
            create.getBody() == null ? "" : extractJsonStringField(create.getBody(), "lobbyId");
        String hostPlayerId =
            create.getBody() == null ? "" : extractJsonStringField(create.getBody(), "playerId");
        assertThat(lobbyId).isNotBlank();
        assertThat(hostPlayerId).isNotBlank();

        ResponseEntity<String> join = rest.postForEntity(
            "/api/lobbies/" + lobbyId + "/join",
            new HttpEntity<>("{}", headers),
            String.class
        );
        assertThat(join.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(join.getBody()).contains("\"seatIndex\":1");
        String guestPlayerId = extractJsonStringField(join.getBody(), "playerId");

        ResponseEntity<String> setMap = rest.postForEntity(
            "/api/lobbies/" + lobbyId + "/map",
            new HttpEntity<>(
                "{\"playerId\":\"" + hostPlayerId + "\",\"mapSlug\":\"lobby-test-map\"}",
                headers
            ),
            String.class
        );
        assertThat(setMap.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> startTooSoon = rest.postForEntity(
            "/api/lobbies/" + lobbyId + "/start",
            new HttpEntity<>("{\"playerId\":\"" + guestPlayerId + "\"}", headers),
            String.class
        );
        assertThat(startTooSoon.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> start = rest.postForEntity(
            "/api/lobbies/" + lobbyId + "/start",
            new HttpEntity<>("{\"playerId\":\"" + hostPlayerId + "\"}", headers),
            String.class
        );
        assertThat(start.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(start.getBody()).startsWith("lob-");

        ResponseEntity<String> guestView = rest.getForEntity(
            "/api/lobbies/" + lobbyId + "?playerId=" + guestPlayerId,
            String.class
        );
        assertThat(guestView.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(guestView.getBody()).contains("\"started\":true");
        assertThat(guestView.getBody()).contains("\"matchId\":\"lob-");
    }

    @Test
    void lobby_supports_four_players_and_start_with_host_only() {
        writeMap("lobby-four-player-map");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> create =
            rest.postForEntity("/api/lobbies", new HttpEntity<>("{}", headers), String.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String lobbyId =
            create.getBody() == null ? "" : extractJsonStringField(create.getBody(), "lobbyId");
        String hostPlayerId =
            create.getBody() == null ? "" : extractJsonStringField(create.getBody(), "playerId");
        assertThat(lobbyId).isNotBlank();
        assertThat(hostPlayerId).isNotBlank();

        ResponseEntity<String> setMap = rest.postForEntity(
            "/api/lobbies/" + lobbyId + "/map",
            new HttpEntity<>(
                "{\"playerId\":\"" + hostPlayerId + "\",\"mapSlug\":\"lobby-four-player-map\"}",
                headers
            ),
            String.class
        );
        assertThat(setMap.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> hostOnlyStart = rest.postForEntity(
            "/api/lobbies/" + lobbyId + "/start",
            new HttpEntity<>("{\"playerId\":\"" + hostPlayerId + "\"}", headers),
            String.class
        );
        assertThat(hostOnlyStart.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(hostOnlyStart.getBody()).startsWith("lob-");

        ResponseEntity<String> createFull =
            rest.postForEntity("/api/lobbies", new HttpEntity<>("{}", headers), String.class);
        String fullLobbyId =
            createFull.getBody() == null ? "" : extractJsonStringField(createFull.getBody(), "lobbyId");

        ResponseEntity<String> join1 = rest.postForEntity(
            "/api/lobbies/" + fullLobbyId + "/join",
            new HttpEntity<>("{}", headers),
            String.class
        );
        ResponseEntity<String> join2 = rest.postForEntity(
            "/api/lobbies/" + fullLobbyId + "/join",
            new HttpEntity<>("{}", headers),
            String.class
        );
        ResponseEntity<String> join3 = rest.postForEntity(
            "/api/lobbies/" + fullLobbyId + "/join",
            new HttpEntity<>("{}", headers),
            String.class
        );
        ResponseEntity<String> join4ShouldFail = rest.postForEntity(
            "/api/lobbies/" + fullLobbyId + "/join",
            new HttpEntity<>("{}", headers),
            String.class
        );

        assertThat(join1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(join1.getBody()).contains("\"seatIndex\":1");
        assertThat(join2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(join2.getBody()).contains("\"seatIndex\":2");
        assertThat(join3.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(join3.getBody()).contains("\"seatIndex\":3");
        assertThat(join4ShouldFail.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private static String extractJsonStringField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int i = json.indexOf(key);
        if (i < 0) {
            return "";
        }
        int from = i + key.length();
        int end = json.indexOf('"', from);
        if (end < 0) {
            return "";
        }
        return json.substring(from, end);
    }
}
