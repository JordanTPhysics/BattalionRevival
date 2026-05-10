package com.game.server.lobby;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LobbyRegistry {

    private final ConcurrentHashMap<String, Lobby> lobbies = new ConcurrentHashMap<>();

    public CreateResult create(String hostDisplayLabel) {
        String lobbyId = UUID.randomUUID().toString();
        String hostPlayerId = UUID.randomUUID().toString();
        Lobby lobby = new Lobby(lobbyId, hostPlayerId, hostDisplayLabel);
        lobbies.put(lobbyId, lobby);
        return new CreateResult(lobbyId, hostPlayerId, 0);
    }

    public List<Lobby.LobbyListItem> listJoinable() {
        return lobbies.values().stream()
            .filter(Lobby::isOpenForJoin)
            .sorted(Comparator.comparing(Lobby::createdAt))
            .map(Lobby::toListItem)
            .toList();
    }

    public Optional<Lobby> getLobby(String lobbyId) {
        return Optional.ofNullable(lobbies.get(lobbyId));
    }

    public Optional<JoinResult> join(String lobbyId, String displayLabel) {
        Lobby lobby = lobbies.get(lobbyId);
        if (lobby == null) {
            return Optional.empty();
        }
        return lobby.join(displayLabel).map(j -> new JoinResult(lobbyId, j.playerId(), j.seatIndex()));
    }

    public record CreateResult(String lobbyId, String playerId, int seatIndex) {
    }

    public record JoinResult(String lobbyId, String playerId, int seatIndex) {
    }
}
