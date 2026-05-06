package com.game.network.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

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
    Integer warmachineFunds
) {
}
