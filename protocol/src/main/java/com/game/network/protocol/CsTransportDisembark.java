package com.game.network.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CsTransportDisembark(
    int protocolVersion,
    String matchId,
    String transportUnitId
) implements NetEnvelope {
}
