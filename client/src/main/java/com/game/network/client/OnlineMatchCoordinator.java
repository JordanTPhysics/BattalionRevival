package com.game.network.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.game.network.protocol.CsEndTurn;
import com.game.network.protocol.MatchSnapshot;
import com.game.network.protocol.NetEnvelope;
import com.game.network.protocol.ProtocolJson;
import com.game.network.protocol.ProtocolVersions;
import com.game.network.protocol.ScCommandResult;
import com.game.network.protocol.ScError;
import com.game.network.protocol.ScSnapshot;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import javax.swing.SwingUtilities;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * OkHttp WebSocket client for authoritative snapshots and issuing {@link CsEndTurn}.
 */
public final class OnlineMatchCoordinator implements AutoCloseable {

    private final String matchId;
    private final Consumer<MatchSnapshot> onSnapshot;
    private final Consumer<String> onIssue;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build();

    private WebSocket webSocket;
    private volatile boolean closed;

    public OnlineMatchCoordinator(
        String matchId,
        Consumer<MatchSnapshot> onSnapshot,
        Consumer<String> onIssue
    ) {
        this.matchId = Objects.requireNonNull(matchId, "matchId").trim();
        if (this.matchId.isEmpty()) {
            throw new IllegalArgumentException("matchId");
        }
        this.onSnapshot = Objects.requireNonNull(onSnapshot);
        this.onIssue = Objects.requireNonNull(onIssue);
    }

    /**
     * Opens the socket (e.g. {@code ws://host:8080/ws/match?matchId=demo&seat=0}).
     */
    public synchronized void connect(String wsUrl) {
        Objects.requireNonNull(wsUrl, "wsUrl");
        if (closed) {
            return;
        }
        disconnectSocketOnly();
        Request request = new Request.Builder().url(wsUrl).build();
        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleMessage(text);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                if (closed) {
                    return;
                }
                String detail = t.getMessage() != null ? t.getMessage() : t.toString();
                SwingUtilities.invokeLater(() -> onIssue.accept(detail));
            }
        });
    }

    private void handleMessage(String text) {
        final NetEnvelope env;
        try {
            env = ProtocolJson.readNetEnvelope(text);
        } catch (JsonProcessingException e) {
            SwingUtilities.invokeLater(() -> onIssue.accept("Invalid protocol message"));
            return;
        }
        if (env instanceof ScSnapshot snap) {
            SwingUtilities.invokeLater(() -> onSnapshot.accept(snap.snapshot()));
            return;
        }
        if (env instanceof ScCommandResult cmd && cmd.snapshotIfAccepted() != null) {
            SwingUtilities.invokeLater(() -> onSnapshot.accept(cmd.snapshotIfAccepted()));
            return;
        }
        if (env instanceof ScError err) {
            SwingUtilities.invokeLater(() -> onIssue.accept(err.code() + ": " + err.message()));
        }
    }

    public void requestEndTurn() {
        WebSocket ws = webSocket;
        if (ws == null || closed) {
            return;
        }
        try {
            ws.send(ProtocolJson.write(new CsEndTurn(ProtocolVersions.NETWORK_PROTOCOL_VERSION, matchId)));
        } catch (JsonProcessingException e) {
            SwingUtilities.invokeLater(() -> onIssue.accept("Failed to serialize end-turn"));
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        disconnectSocketOnly();
    }

    private void disconnectSocketOnly() {
        if (webSocket != null) {
            webSocket.close(1000, null);
            webSocket = null;
        }
    }
}
