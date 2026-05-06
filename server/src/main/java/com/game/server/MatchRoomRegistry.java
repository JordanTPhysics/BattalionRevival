package com.game.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.engine.PlayableGameSession;
import com.game.network.protocol.MatchSnapshot;
import com.game.network.protocol.ProtocolVersions;
import com.game.network.protocol.ScSnapshot;
import com.game.network.protocol.ScWelcome;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * In-memory authoritative matches (bootstrapped demo room {@code demo}).
 */
@Component
public class MatchRoomRegistry {
    private final ConcurrentHashMap<String, AuthoritativeMatchRoom> rooms = new ConcurrentHashMap<>();

    public AuthoritativeMatchRoom getOrCreateRoom(String matchId, PlayableGameSession session) {
        return rooms.computeIfAbsent(matchId, id -> new AuthoritativeMatchRoom(id, session));
    }

    /**
     * If no room exists for {@code matchId}, creates one; otherwise keeps the existing session (idempotent for joining players).
     */
    public void ensureRoom(String matchId, PlayableGameSession session) {
        rooms.compute(matchId, (id, existing) -> existing != null ? existing : new AuthoritativeMatchRoom(id, session));
    }

    public AuthoritativeMatchRoom get(String matchId) {
        return rooms.get(matchId);
    }

    public Collection<AuthoritativeMatchRoom> allRooms() {
        return rooms.values();
    }

    public void unregisterSession(WebSocketSession ws) {
        rooms.values().forEach(r -> r.unregister(ws));
    }

    public static final class AuthoritativeMatchRoom {
        private final String matchId;
        private final PlayableGameSession session;
        private final CopyOnWriteArraySet<WebSocketSession> sockets = new CopyOnWriteArraySet<>();

        private AuthoritativeMatchRoom(String matchId, PlayableGameSession session) {
            this.matchId = matchId;
            this.session = session;
        }

        public String matchId() {
            return matchId;
        }

        public PlayableGameSession session() {
            return session;
        }

        public void register(WebSocketSession ws) {
            sockets.add(ws);
        }

        public void unregister(WebSocketSession ws) {
            sockets.remove(ws);
        }

        public void broadcastJson(String json) throws IOException {
            for (WebSocketSession s : sockets) {
                if (s.isOpen()) {
                    synchronized (s) {
                        s.sendMessage(new TextMessage(json));
                    }
                }
            }
        }

        public void sendWelcome(WebSocketSession ws, int seatIndex, ObjectMapper mapper) throws IOException {
            ws.sendMessage(new TextMessage(mapper.writeValueAsString(
                new ScWelcome(ProtocolVersions.NETWORK_PROTOCOL_VERSION, matchId, seatIndex, "Connected")
            )));
        }

        public void sendSnapshot(WebSocketSession ws, MatchSnapshot snapshot, ObjectMapper mapper) throws IOException {
            ws.sendMessage(new TextMessage(mapper.writeValueAsString(
                new ScSnapshot(ProtocolVersions.NETWORK_PROTOCOL_VERSION, snapshot)
            )));
        }
    }
}
