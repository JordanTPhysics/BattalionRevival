package com.game.network.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CsWarmachineBuild(
    int protocolVersion,
    String matchId,
    String warmachineUnitId,
    String unitType
) implements NetEnvelope {
}
