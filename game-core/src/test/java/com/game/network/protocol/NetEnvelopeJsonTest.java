package com.game.network.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Round-trip and wire-shape tests for {@link NetEnvelope} using {@link ProtocolJson},
 * matching how the WebSocket handler reads client text and writes server payloads.
 */
class NetEnvelopeJsonTest {

    @Test
    void receive_csPing_deserializesFromClientJson() throws JsonProcessingException {
        String json = "{\"kind\":\"CS_PING\",\"protocolVersion\":1}";
        NetEnvelope env = ProtocolJson.readNetEnvelope(json);
        CsPing ping = assertInstanceOf(CsPing.class, env);
        assertEquals(1, ping.protocolVersion());
    }

    @Test
    void send_scPong_serializesWithKindAndRoundTrips() throws JsonProcessingException {
        ScPong original = new ScPong(1);
        String json = ProtocolJson.write(original);

        JsonNode node = ProtocolJson.mapper().readTree(json);
        assertEquals("SC_PONG", node.get("kind").asText());
        assertEquals(1, node.get("protocolVersion").asInt());

        NetEnvelope env = ProtocolJson.readNetEnvelope(json);
        ScPong pong = assertInstanceOf(ScPong.class, env);
        assertEquals(1, pong.protocolVersion());
    }

    @Test
    void receive_csEndTurn_deserializesAndRoundTrips() throws JsonProcessingException {
        String json = "{\"kind\":\"CS_END_TURN\",\"protocolVersion\":1,\"matchId\":\"m-1\"}";
        NetEnvelope env = ProtocolJson.readNetEnvelope(json);
        CsEndTurn end = assertInstanceOf(CsEndTurn.class, env);
        assertEquals(1, end.protocolVersion());
        assertEquals("m-1", end.matchId());

        assertEquals(env, ProtocolJson.readNetEnvelope(ProtocolJson.write(end)));
    }

    @Test
    void receive_csMoveUnit_withPath_deserializesAndRoundTrips() throws JsonProcessingException {
        String json = """
            {
              "kind": "CS_MOVE_UNIT",
              "protocolVersion": 1,
              "matchId": "m-1",
              "unitId": "u-1",
              "pathIncludingStart": [{"x": 0, "y": 0}, {"x": 1, "y": 0}]
            }
            """;
        NetEnvelope env = ProtocolJson.readNetEnvelope(json);
        CsMoveUnit move = assertInstanceOf(CsMoveUnit.class, env);
        assertEquals(1, move.protocolVersion());
        assertEquals("m-1", move.matchId());
        assertEquals("u-1", move.unitId());
        assertEquals(List.of(new GridPoint(0, 0), new GridPoint(1, 0)), move.pathIncludingStart());

        assertEquals(move, ProtocolJson.readNetEnvelope(ProtocolJson.write(move)));
    }

    @Test
    void receive_csMoveAndAttackUnit_roundTrips() throws JsonProcessingException {
        CsMoveAndAttackUnit maa =
            new CsMoveAndAttackUnit(1, "m-9", "atk", List.of(new GridPoint(0, 0), new GridPoint(1, 0)), "def");
        String json = ProtocolJson.write(maa);
        NetEnvelope env = ProtocolJson.readNetEnvelope(json);
        CsMoveAndAttackUnit parsed = assertInstanceOf(CsMoveAndAttackUnit.class, env);
        assertEquals(maa, parsed);
    }

    @Test
    void send_scError_serializesWithKindCodeMessage() throws JsonProcessingException {
        ScError err = new ScError(1, "NO_ROOM", "bad match");
        String json = ProtocolJson.write(err);

        JsonNode node = ProtocolJson.mapper().readTree(json);
        assertEquals("SC_ERROR", node.get("kind").asText());
        assertEquals(1, node.get("protocolVersion").asInt());
        assertEquals("NO_ROOM", node.get("code").asText());
        assertEquals("bad match", node.get("message").asText());

        NetEnvelope env = ProtocolJson.readNetEnvelope(json);
        ScError parsed = assertInstanceOf(ScError.class, env);
        assertEquals("NO_ROOM", parsed.code());
        assertEquals("bad match", parsed.message());
    }
}
