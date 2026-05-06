package com.game.network.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScSnapshot(
    int protocolVersion,
    MatchSnapshot snapshot
) implements NetEnvelope {
}
