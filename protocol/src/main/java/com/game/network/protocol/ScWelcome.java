package com.game.network.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScWelcome(
    int protocolVersion,
    String matchId,
    int yourSeatIndex,
    String message
) implements NetEnvelope {
}
