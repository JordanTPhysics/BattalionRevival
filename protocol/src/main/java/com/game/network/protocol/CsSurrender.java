package com.game.network.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CsSurrender(
    int protocolVersion,
    String matchId
) implements NetEnvelope {
}
