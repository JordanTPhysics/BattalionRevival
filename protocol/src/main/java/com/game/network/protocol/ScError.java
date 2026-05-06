package com.game.network.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScError(
    int protocolVersion,
    String code,
    String message
) implements NetEnvelope {
}
