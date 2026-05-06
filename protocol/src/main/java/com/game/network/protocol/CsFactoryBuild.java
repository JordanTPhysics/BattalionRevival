package com.game.network.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CsFactoryBuild(
    int protocolVersion,
    String matchId,
    int factoryX,
    int factoryY,
    String unitType
) implements NetEnvelope {
}
