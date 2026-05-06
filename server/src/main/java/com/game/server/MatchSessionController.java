package com.game.server;

import com.game.engine.PlayableGameSession;
import com.game.model.map.GameMap;
import com.game.server.maps.SharedMapContracts;
import com.game.server.maps.SharedMapFileStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Creates authoritative match rooms backed by a shared map file (idempotent for the same {@code matchId}).
 */
@RestController
@RequestMapping("/api/matches")
public class MatchSessionController {

    private static final Pattern MATCH_ID_SAFE = Pattern.compile("^[a-z0-9][a-z0-9_-]{1,62}$");

    public record EnsureMatchRequest(String matchId, String mapSlug) {
    }

    private final MatchRoomRegistry registry;
    private final SharedMapFileStore sharedMaps;

    public MatchSessionController(MatchRoomRegistry registry, SharedMapFileStore sharedMaps) {
        this.registry = registry;
        this.sharedMaps = sharedMaps;
    }

    /**
     * Ensures a room exists for {@code matchId} using the map {@code mapSlug}. If the room already exists, it is left unchanged
     * (so every player can call this before connecting). The match id {@code demo} is reserved for the bootstrapped default room.
     */
    @PostMapping(consumes = "application/json")
    public ResponseEntity<String> ensureMatch(@RequestBody EnsureMatchRequest body) {
        if (body == null || body.matchId() == null || body.mapSlug() == null) {
            return ResponseEntity.badRequest().body("matchId and mapSlug required");
        }
        String matchId = body.matchId().trim().toLowerCase(Locale.ROOT);
        if (!MATCH_ID_SAFE.matcher(matchId).matches()) {
            return ResponseEntity.badRequest().body("invalid matchId");
        }
        if ("demo".equals(matchId)) {
            return ResponseEntity.badRequest().body(
                "matchId 'demo' is reserved for the server's default skirmish; choose another id (e.g. room1)"
            );
        }
        String slug = body.mapSlug().trim().toLowerCase(Locale.ROOT);
        if (!SharedMapContracts.isSlugSafe(slug)) {
            return ResponseEntity.badRequest().body("invalid mapSlug");
        }

        boolean existed = registry.get(matchId) != null;
        GameMap map;
        try {
            map = sharedMaps.loadMap(slug);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("unknown map slug: " + slug);
        }
        registry.ensureRoom(matchId, new PlayableGameSession(map));
        return existed
            ? ResponseEntity.ok("match already existed")
            : ResponseEntity.status(HttpStatus.CREATED).body("match created");
    }
}
