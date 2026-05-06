package com.game.network.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CsAttackUnit(
    int protocolVersion,
    String matchId,
    String attackerUnitId,
    String defenderUnitId
) implements NetEnvelope {
}
