package com.game.network.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Authoritative match view for reconnect and client resync. Rows follow map JSON convention (y-major).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MatchSnapshot(
    int schemaVersion,
    String matchId,
    int roundNumber,
    int activePlayerIndex,
    int teamCount,
    int width,
    int height,
    List<List<TileSnapshot>> tiles,
    List<UnitSnapshot> units,
    List<PlayerSnapshot> players,
    boolean matchFinished
) {
}
