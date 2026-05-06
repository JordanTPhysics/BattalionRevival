package com.game.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.engine.AuthoritativeCommandExecutor;
import com.game.engine.MatchSnapshotExporter;
import com.game.engine.PlayableGameSession;
import com.game.network.protocol.CsPing;
import com.game.network.protocol.NetEnvelope;
import com.game.network.protocol.ProtocolVersions;
import com.game.network.protocol.ScCommandResult;
import com.game.network.protocol.ScError;
import com.game.network.protocol.ScPong;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

@Component
public class MatchSocketHandler extends TextWebSocketHandler {
    public static final String ATTR_MATCH_ID = "matchId";
    public static final String ATTR_SEAT = "seat";

    private final MatchRoomRegistry registry;
    private final ObjectMapper mapper;

    public MatchSocketHandler(MatchRoomRegistry registry, ObjectMapper battalionProtocolMapper) {
        this.registry = registry;
        this.mapper = battalionProtocolMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String matchId = String.valueOf(session.getAttributes().getOrDefault(ATTR_MATCH_ID, "demo"));
        int seat = parseSeat(session.getAttributes().get(ATTR_SEAT));
        MatchRoomRegistry.AuthoritativeMatchRoom room = registry.get(matchId);
        if (room == null) {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(
                new ScError(ProtocolVersions.NETWORK_PROTOCOL_VERSION, "NO_ROOM", "Unknown match: " + matchId)
            )));
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        room.register(session);
        room.sendWelcome(session, seat, mapper);
        room.sendSnapshot(session, MatchSnapshotExporter.export(room.session(), matchId), mapper);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String matchId = String.valueOf(session.getAttributes().getOrDefault(ATTR_MATCH_ID, "demo"));
        int seat = parseSeat(session.getAttributes().get(ATTR_SEAT));
        MatchRoomRegistry.AuthoritativeMatchRoom room = registry.get(matchId);
        if (room == null) {
            return;
        }

        NetEnvelope env = mapper.readValue(message.getPayload(), NetEnvelope.class);
        if (env instanceof CsPing p) {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(
                new ScPong(p.protocolVersion())
            )));
            return;
        }

        AuthoritativeCommandExecutor.CommandApplyResult result =
            AuthoritativeCommandExecutor.execute(room.session(), seat, matchId, env);

        ScCommandResult payload = new ScCommandResult(
            ProtocolVersions.NETWORK_PROTOCOL_VERSION,
            result.accepted(),
            result.reasonCode(),
            result.detail(),
            result.snapshot()
        );
        room.broadcastJson(mapper.writeValueAsString(payload));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.unregisterSession(session);
    }

    private static int parseSeat(Object raw) {
        if (raw == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
