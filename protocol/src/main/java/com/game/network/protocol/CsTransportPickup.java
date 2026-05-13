package com.game.network.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CsTransportPickup(
    int protocolVersion,
    String matchId,
    String transportUnitId,
    String passengerUnitId
) implements NetEnvelope {
}
