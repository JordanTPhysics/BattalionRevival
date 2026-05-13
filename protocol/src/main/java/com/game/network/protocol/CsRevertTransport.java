package com.game.network.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CsRevertTransport(
    int protocolVersion,
    String matchId,
    String unitId
) implements NetEnvelope {
}
