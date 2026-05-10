package com.game.server.lobby;

/**
 * Deterministic match id derived from a lobby UUID so clients can poll until {@code started},
 * then open {@code /ws/match} with this id and their seat.
 */
public final class LobbyIds {

    private LobbyIds() {
    }

    public static String matchIdForLobby(String lobbyUuidWithHyphens) {
        String compact = lobbyUuidWithHyphens.replace("-", "");
        return "lob-" + compact;
    }
}
