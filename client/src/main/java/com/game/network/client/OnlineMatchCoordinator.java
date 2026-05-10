package com.game.network.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.game.network.protocol.CsAttackUnit;
import com.game.network.protocol.CsMoveAndAttackUnit;
import com.game.network.protocol.CsEndTurn;
import com.game.network.protocol.CsFactoryBuild;
import com.game.network.protocol.CsMoveUnit;
import com.game.network.protocol.CsSurrender;
import com.game.network.protocol.CsWarmachineBuild;
import com.game.network.protocol.CsWarmachineDrill;
import com.game.network.protocol.GridPoint;
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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * OkHttp WebSocket client for authoritative snapshots and issuing multiplayer commands.
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
        if (env instanceof ScCommandResult cmd) {
            if (cmd.accepted() && cmd.snapshotIfAccepted() != null) {
                SwingUtilities.invokeLater(() -> onSnapshot.accept(cmd.snapshotIfAccepted()));
            } else if (!cmd.accepted()) {
                String code = cmd.reasonCode() != null ? cmd.reasonCode() : "REJECT";
                String detail = cmd.detail() != null ? cmd.detail() : "";
                SwingUtilities.invokeLater(() -> onIssue.accept(code + ": " + detail));
            }
            return;
        }
        if (env instanceof ScError err) {
            SwingUtilities.invokeLater(() -> onIssue.accept(err.code() + ": " + err.message()));
        }
    }

    private void send(NetEnvelope envelope) {
        WebSocket ws = webSocket;
        if (ws == null || closed) {
            return;
        }
        try {
            ws.send(ProtocolJson.write(envelope));
        } catch (JsonProcessingException e) {
            SwingUtilities.invokeLater(() -> onIssue.accept("Failed to serialize command"));
        }
    }

    public void requestMoveUnit(String unitId, List<GridPoint> pathIncludingStart) {
        send(new CsMoveUnit(ProtocolVersions.NETWORK_PROTOCOL_VERSION, matchId, unitId, pathIncludingStart));
    }

    public void requestAttack(String attackerUnitId, String defenderUnitId) {
        send(new CsAttackUnit(ProtocolVersions.NETWORK_PROTOCOL_VERSION, matchId, attackerUnitId, defenderUnitId));
    }

    public void requestMoveAndAttackUnit(String unitId, List<GridPoint> pathIncludingStart, String defenderUnitId) {
        send(new CsMoveAndAttackUnit(ProtocolVersions.NETWORK_PROTOCOL_VERSION, matchId, unitId, pathIncludingStart, defenderUnitId));
    }

    public void requestFactoryBuild(int factoryX, int factoryY, String unitType) {
        send(new CsFactoryBuild(ProtocolVersions.NETWORK_PROTOCOL_VERSION, matchId, factoryX, factoryY, unitType));
    }

    public void requestWarmachineBuild(String warmachineUnitId, String unitType) {
        send(new CsWarmachineBuild(ProtocolVersions.NETWORK_PROTOCOL_VERSION, matchId, warmachineUnitId, unitType));
    }

    public void requestWarmachineDrill(String warmachineUnitId) {
        send(new CsWarmachineDrill(ProtocolVersions.NETWORK_PROTOCOL_VERSION, matchId, warmachineUnitId));
    }

    public void requestEndTurn() {
        send(new CsEndTurn(ProtocolVersions.NETWORK_PROTOCOL_VERSION, matchId));
    }

    public void requestSurrender() {
        send(new CsSurrender(ProtocolVersions.NETWORK_PROTOCOL_VERSION, matchId));
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
