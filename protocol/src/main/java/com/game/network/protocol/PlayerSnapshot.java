package com.game.network.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlayerSnapshot(
    int seatIndex,
    String displayName,
    int money,
    boolean eliminated
) {
}
