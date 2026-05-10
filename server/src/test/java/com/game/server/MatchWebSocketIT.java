package com.game.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for {@code /ws/match} (native WebSocket used by the browser client).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MatchWebSocketIT {

    @TempDir
    static Path sharedMapsDirectory;

    @DynamicPropertySource
    static void registerSharedMapsPath(DynamicPropertyRegistry registry) {
        registry.add(
            "battalion.shared-maps.directory",
            () -> sharedMapsDirectory.toAbsolutePath().toString()
        );
    }

    @LocalServerPort
    int port;

    @Test
    void connectDemoMatch_receivesWelcomeJson() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> firstPayload = new AtomicReference<>();

        StandardWebSocketClient client = new StandardWebSocketClient();
        TextWebSocketHandler handler = new TextWebSocketHandler() {
            @Override
            public void handleTextMessage(WebSocketSession session, TextMessage message) {
                firstPayload.set(message.getPayload());
                latch.countDown();
            }
        };

        WebSocketConnectionManager manager =
            new WebSocketConnectionManager(client, handler, URI.create(wsUrl("demo", 0)));
        manager.start();
        try {
            assertThat(latch.await(15, TimeUnit.SECONDS)).as("welcome message received").isTrue();
            assertThat(firstPayload.get()).contains("SC_WELCOME");
        } finally {
            manager.stop();
        }
    }

    private String wsUrl(String matchId, int seat) {
        return "ws://127.0.0.1:" + port + "/ws/match?matchId=" + matchId + "&seat=" + seat;
    }
}
