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
    List<GridPoint> lastMovePathIncludingStart,
    /** When set, field repair is pending until resolved at turn start or interrupted by damage. */
    Integer fieldRepairStartedRound,
    /** When set, this unit is embarked in the given transport (not placed on its own map tile). */
    String embarkedInTransportUnitId,
    /**
     * When {@code unitType} is a converted {@code Albatross} / {@code Leviathan}, the land unit type
     * before morph (used for revert rules and UI). Omitted when not applicable.
     */
    String originalLandUnitType
) {
}
