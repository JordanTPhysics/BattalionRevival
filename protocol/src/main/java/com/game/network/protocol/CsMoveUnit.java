package com.game.network.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CsMoveUnit(
    int protocolVersion,
    String matchId,
    String unitId,
    List<GridPoint> pathIncludingStart
) implements NetEnvelope {
}
