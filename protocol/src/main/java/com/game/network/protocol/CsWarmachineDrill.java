package com.game.network.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CsWarmachineDrill(
    int protocolVersion,
    String matchId,
    String warmachineUnitId
) implements NetEnvelope {
}
