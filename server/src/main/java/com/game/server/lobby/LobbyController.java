package com.game.server.lobby;

import com.game.server.MatchRoomRegistry;
import com.game.server.maps.SharedMapStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Browse open lobbies, join in one request, then choose a map and start (host).
 */
@RestController
@RequestMapping("/api/lobbies")
public class LobbyController {

    public record CreateLobbyBody(String displayLabel) {
    }

    public record JoinLobbyBody(String displayLabel) {
    }

    public record SetMapBody(String playerId, String mapSlug) {
    }

    public record StartBody(String playerId) {
    }

    private final LobbyRegistry lobbies;
    private final MatchRoomRegistry matchRooms;
    private final SharedMapStore sharedMaps;

    public LobbyController(LobbyRegistry lobbies, MatchRoomRegistry matchRooms, SharedMapStore sharedMaps) {
        this.lobbies = lobbies;
        this.matchRooms = matchRooms;
        this.sharedMaps = sharedMaps;
    }

    @GetMapping
    public List<Lobby.LobbyListItem> listOpen() {
        return lobbies.listJoinable();
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<LobbyRegistry.CreateResult> create(@RequestBody(required = false) CreateLobbyBody body) {
        String label = body == null ? null : body.displayLabel();
        LobbyRegistry.CreateResult created = lobbies.create(label);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{lobbyId}")
    public ResponseEntity<?> getLobby(
        @PathVariable("lobbyId") String lobbyId,
        @RequestParam(value = "playerId", required = false) String playerId
    ) {
        return lobbies.getLobby(lobbyId)
            .map(lobby -> {
                if (playerId == null || playerId.isBlank()) {
                    return ResponseEntity.ok(lobby.toPublicView());
                }
                if (!lobby.isMember(playerId.trim())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("unknown playerId for this lobby");
                }
                return ResponseEntity.ok(lobby.toMemberView(playerId.trim()));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/{lobbyId}/join", consumes = "application/json")
    public ResponseEntity<?> join(
        @PathVariable("lobbyId") String lobbyId,
        @RequestBody(required = false) JoinLobbyBody body
    ) {
        String label = body == null ? null : body.displayLabel();
        Optional<LobbyRegistry.JoinResult> joined = lobbies.join(lobbyId, label);
        if (joined.isPresent()) {
            return ResponseEntity.ok(joined.get());
        }
        if (lobbies.getLobby(lobbyId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body("lobby full or already started");
    }

    @PostMapping(value = "/{lobbyId}/map", consumes = "application/json")
    public ResponseEntity<String> setMap(
        @PathVariable("lobbyId") String lobbyId,
        @RequestBody SetMapBody body
    ) {
        if (body == null || body.playerId() == null || body.mapSlug() == null) {
            return ResponseEntity.badRequest().body("playerId and mapSlug required");
        }
        String slug = body.mapSlug().trim().toLowerCase(Locale.ROOT);
        try {
            sharedMaps.loadMap(slug);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("unknown map slug");
        }
        return lobbies.getLobby(lobbyId)
            .map(lobby -> {
                String err = lobby.setMap(body.playerId().trim(), slug);
                if (err != null) {
                    HttpStatus st =
                        err.contains("only host")
                            ? HttpStatus.FORBIDDEN
                            : err.contains("already started") ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
                    return ResponseEntity.status(st).body(err);
                }
                return ResponseEntity.ok("ok");
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/{lobbyId}/start", consumes = "application/json")
    public ResponseEntity<String> start(
        @PathVariable("lobbyId") String lobbyId,
        @RequestBody StartBody body
    ) {
        if (body == null || body.playerId() == null || body.playerId().isBlank()) {
            return ResponseEntity.badRequest().body("playerId required");
        }
        return lobbies.getLobby(lobbyId)
            .map(lobby -> {
                String err = lobby.start(body.playerId().trim(), matchRooms, sharedMaps);
                if (err != null) {
                    HttpStatus st =
                        err.contains("only host")
                            ? HttpStatus.FORBIDDEN
                            : err.contains("pick a map")
                                ? HttpStatus.BAD_REQUEST
                                : err.contains("need at least")
                                    ? HttpStatus.BAD_REQUEST
                                    : err.contains("map not found")
                                        ? HttpStatus.NOT_FOUND
                                        : HttpStatus.BAD_REQUEST;
                    return ResponseEntity.status(st).body(err);
                }
                return ResponseEntity.ok(LobbyIds.matchIdForLobby(lobbyId));
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
