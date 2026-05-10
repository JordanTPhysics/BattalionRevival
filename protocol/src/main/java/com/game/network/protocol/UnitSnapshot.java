package com.game.network.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnitSnapshot(
    String id,
    String unitType,
    int ownerSeatIndex,
    int x,
    int y,
    int health,
    boolean hasMoved,
    boolean cloaked,
    String facing,
    Integer warmachineFunds,
    /** Authoritative orthogonal path for the last move animation; omitted when absent. */
    List<GridPoint> lastMovePathIncludingStart
) {
}
