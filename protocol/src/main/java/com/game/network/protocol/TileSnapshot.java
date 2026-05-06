package com.game.network.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TileSnapshot(
    String terrain,
    String structure,
    Integer structureTeam,
    String unitSprite,
    Integer unitTeam,
    String unitFacing,
    Boolean oreDeposit
) {
}
