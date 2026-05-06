package com.game.network.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Canonical JSON serialization for WebSocket + REST bodies sharing {@link NetEnvelope}.
 */
public final class ProtocolJson {
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private ProtocolJson() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String write(NetEnvelope envelope) throws JsonProcessingException {
        return MAPPER.writeValueAsString(envelope);
    }

    public static NetEnvelope readNetEnvelope(String json) throws JsonProcessingException {
        return MAPPER.readValue(json, NetEnvelope.class);
    }

    public static String writeSnapshot(MatchSnapshot snapshot) throws JsonProcessingException {
        return MAPPER.writeValueAsString(snapshot);
    }

    public static MatchSnapshot readSnapshot(String json) throws JsonProcessingException {
        return MAPPER.readValue(json, MatchSnapshot.class);
    }
}
