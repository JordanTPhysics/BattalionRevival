package com.game.server.lobby;

import com.game.engine.PlayableGameSession;
import com.game.model.map.GameMap;
import com.game.server.MatchRoomRegistry;
import com.game.server.maps.SharedMapFileStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory pre-game room. When {@link #start} runs, an authoritative match is registered
 * and clients connect to {@code /ws/match} using the issued {@code matchId} and seat index.
 */
public final class Lobby {

    public static final int MAX_PLAYERS = 4;
    public static final int MIN_PLAYERS_TO_START = 1;

    private final String id;
    private final Instant createdAt;
    private final String hostPlayerId;
    private final String[] playerIds = new String[MAX_PLAYERS];
    private final String[] labels = new String[MAX_PLAYERS];

    private volatile String selectedMapSlug;
    private volatile boolean started;
    private volatile String matchId;

    Lobby(String id, String hostPlayerId, String hostLabel) {
        this.id = id;
        this.createdAt = Instant.now();
        this.hostPlayerId = hostPlayerId;
        this.playerIds[0] = hostPlayerId;
        this.labels[0] = hostLabel == null || hostLabel.isBlank() ? "Host" : hostLabel.trim();
    }

    public String id() {
        return id;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean started() {
        return started;
    }

    public boolean isOpenForJoin() {
        return !started && occupiedCount() < MAX_PLAYERS;
    }

    public int occupiedCount() {
        int n = 0;
        for (String pid : playerIds) {
            if (pid != null) {
                n++;
            }
        }
        return n;
    }

    public Optional<JoinResult> join(String displayLabel) {
        synchronized (this) {
            if (started) {
                return Optional.empty();
            }
            for (int i = 0; i < MAX_PLAYERS; i++) {
                if (playerIds[i] == null) {
                    String pid = UUID.randomUUID().toString();
                    playerIds[i] = pid;
                    labels[i] =
                        displayLabel == null || displayLabel.isBlank()
                            ? "Player " + (i + 1)
                            : displayLabel.trim();
                    return Optional.of(new JoinResult(pid, i));
                }
            }
            return Optional.empty();
        }
    }

    public boolean isMember(String playerId) {
        if (playerId == null) {
            return false;
        }
        for (String pid : playerIds) {
            if (playerId.equals(pid)) {
                return true;
            }
        }
        return false;
    }

    public Integer seatFor(String playerId) {
        for (int i = 0; i < MAX_PLAYERS; i++) {
            if (playerId.equals(playerIds[i])) {
                return i;
            }
        }
        return null;
    }

    /** Host-only; only while waiting. */
    public String setMap(String playerId, String mapSlug) {
        synchronized (this) {
            if (started) {
                return "already started";
            }
            if (!hostPlayerId.equals(playerId)) {
                return "only host can set map";
            }
            if (mapSlug == null || mapSlug.isBlank()) {
                return "mapSlug required";
            }
            this.selectedMapSlug = mapSlug.trim().toLowerCase(java.util.Locale.ROOT);
            return null;
        }
    }

    /**
     * Host-only; creates authoritative room. Returns error code string or null on success.
     */
    public String start(String playerId, MatchRoomRegistry registry, SharedMapFileStore maps) {
        synchronized (this) {
            if (started) {
                return null;
            }
            if (!hostPlayerId.equals(playerId)) {
                return "only host can start";
            }
            if (selectedMapSlug == null || selectedMapSlug.isBlank()) {
                return "pick a map first";
            }
            if (occupiedCount() < MIN_PLAYERS_TO_START) {
                return "need at least " + MIN_PLAYERS_TO_START + " players";
            }
            GameMap gameMap;
            try {
                gameMap = maps.loadMap(selectedMapSlug);
            } catch (IllegalArgumentException ex) {
                return "map not found";
            }
            String mid = LobbyIds.matchIdForLobby(id);
            int teamCount = Math.max(GameMap.MIN_TEAMS, gameMap.getTeamCount());
            Set<Integer> aiSeats = new LinkedHashSet<>();
            for (int seat = 0; seat < teamCount; seat++) {
                boolean hasHuman = seat < MAX_PLAYERS && playerIds[seat] != null;
                if (!hasHuman) {
                    aiSeats.add(seat);
                }
            }
            registry.ensureRoom(mid, new PlayableGameSession(gameMap), aiSeats);
            this.matchId = mid;
            this.started = true;
            return null;
        }
    }

    public LobbyListItem toListItem() {
        return new LobbyListItem(
            id,
            occupiedCount(),
            MAX_PLAYERS,
            started,
            selectedMapSlug
        );
    }

    public LobbyPublicView toPublicView() {
        return new LobbyPublicView(
            id,
            started,
            occupiedCount(),
            MAX_PLAYERS,
            selectedMapSlug,
            matchId
        );
    }

    public LobbyMemberView toMemberView(String playerId) {
        List<LobbyPlayerRow> rows = new ArrayList<>();
        for (int i = 0; i < MAX_PLAYERS; i++) {
            if (playerIds[i] != null) {
                rows.add(new LobbyPlayerRow(i, labels[i], hostPlayerId.equals(playerIds[i])));
            }
        }
        Integer seat = seatFor(playerId);
        return new LobbyMemberView(
            id,
            started,
            occupiedCount(),
            MAX_PLAYERS,
            selectedMapSlug,
            matchId,
            hostPlayerId,
            seat,
            rows
        );
    }

    public record JoinResult(String playerId, int seatIndex) {
    }

    public record LobbyListItem(
        String lobbyId,
        int playerCount,
        int maxPlayers,
        boolean started,
        String selectedMapSlug
    ) {
    }

    public record LobbyPublicView(
        String lobbyId,
        boolean started,
        int playerCount,
        int maxPlayers,
        String selectedMapSlug,
        String matchId
    ) {
    }

    public record LobbyPlayerRow(int seatIndex, String displayLabel, boolean host) {
    }

    public record LobbyMemberView(
        String lobbyId,
        boolean started,
        int playerCount,
        int maxPlayers,
        String selectedMapSlug,
        String matchId,
        String hostPlayerId,
        Integer yourSeatIndex,
        List<LobbyPlayerRow> players
    ) {
    }
}
