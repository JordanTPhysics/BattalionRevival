"use client";

import { useEffect, useRef, useState } from "react";
import { GameCanvas, type GameInteractionConfig } from "@/components/game/GameCanvas";
import { GameInfoStrip } from "@/components/game/GameInfoStrip";
import { ProductionModals } from "@/components/game/ProductionModals";
import { GameUnitContextMenu } from "@/components/game/GameUnitContextMenu";
import { useMatchStore, getMatchWebSocketClient } from "@/stores/matchStore";
import { useSessionStore } from "@/stores/sessionStore";
import { useUiStore } from "@/stores/uiStore";
import { useGameHudStore } from "@/stores/gameHudStore";

export default function PlayPage() {
  const setActive = useUiStore((s) => s.setActivePanel);
  const connectionStatus = useMatchStore((s) => s.connectionStatus);
  const statusDetail = useMatchStore((s) => s.statusDetail);
  const snapshot = useMatchStore((s) => s.snapshot);
  const welcome = useMatchStore((s) => s.welcome);
  const connect = useMatchStore((s) => s.connect);
  const disconnect = useMatchStore((s) => s.disconnect);
  const commandFeedback = useMatchStore((s) => s.commandFeedback);
  const clearCommandFeedback = useMatchStore((s) => s.clearCommandFeedback);

  const gameServerOrigin = useSessionStore((s) => s.gameServerOrigin);
  const matchId = useSessionStore((s) => s.matchId);
  const seat = useSessionStore((s) => s.seat);
  const setGameServerOrigin = useSessionStore((s) => s.setGameServerOrigin);
  const setMatchId = useSessionStore((s) => s.setMatchId);
  const setSeat = useSessionStore((s) => s.setSeat);

  const [showAdvanced, setShowAdvanced] = useState(false);

  const lastAutoConnectMatchIdRef = useRef<string | null>(null);
  useEffect(() => {
    const id = matchId.trim();
    if (!id) return;
    if (connectionStatus !== "idle" && connectionStatus !== "disconnected") return;
    if (lastAutoConnectMatchIdRef.current === id) return;
    lastAutoConnectMatchIdRef.current = id;
    connect();
  }, [matchId, connectionStatus, connect]);

  const interaction: GameInteractionConfig | null =
    connectionStatus === "connected" && welcome && snapshot
      ? {
          matchId: snapshot.matchId,
          yourSeatIndex: welcome.yourSeatIndex,
          activePlayerIndex: snapshot.activePlayerIndex,
        }
      : null;

  const yourTurn =
    snapshot != null &&
    welcome != null &&
    welcome.yourSeatIndex === snapshot.activePlayerIndex;

  useEffect(() => {
    if (!snapshot || !welcome) return;
    if (snapshot.activePlayerIndex !== welcome.yourSeatIndex) {
      useGameHudStore.getState().clearGameSelection();
    }
  }, [snapshot, welcome]);

  useEffect(() => {
    if (connectionStatus === "idle" || connectionStatus === "disconnected") {
      useGameHudStore.getState().clearGameSelection();
    }
  }, [connectionStatus]);

  useEffect(() => {
    setActive("game");
    return () => setActive("home");
  }, [setActive]);

  const missionTitle = snapshot?.matchId ?? matchId.trim();

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
      <div className="flex min-h-0 flex-1 flex-col gap-3 overflow-hidden lg:flex-row">
        <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-zinc-800 bg-[#0F1412]">
          <GameCanvas snapshot={snapshot} interaction={interaction} className="h-full min-h-0 w-full flex-1" />
        </div>
        <aside className="flex w-full shrink-0 flex-col gap-3 lg:w-72 lg:overflow-y-auto">
          <div className="flex flex-col gap-3 rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
            <label className="flex flex-1 flex-col gap-1 text-sm">
              <span className="text-zinc-500">Server origin</span>
              <input
                className="rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-zinc-100"
                value={gameServerOrigin}
                onChange={(e) => setGameServerOrigin(e.target.value)}
              />
            </label>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={connect}
                className="rounded-md bg-emerald-700 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-600"
              >
                Connect
              </button>
              <button
                type="button"
                onClick={disconnect}
                className="rounded-md border border-zinc-600 px-4 py-2 text-sm text-zinc-300 hover:bg-zinc-800"
              >
                Disconnect
              </button>
            </div>
            <button
              type="button"
              onClick={() => setShowAdvanced((s) => !s)}
              className="mt-1 w-fit text-left text-xs text-zinc-500 underline decoration-zinc-600 hover:text-zinc-400"
            >
              {showAdvanced ? "Hide" : "Show"} manual match id / seat (demo, debugging)
            </button>
            {showAdvanced ? (
              <div className="flex flex-col gap-3 border-t border-zinc-800 pt-3 lg:flex-row lg:items-end">
                <label className="flex w-full flex-col gap-1 text-sm lg:max-w-[220px]">
                  <span className="text-zinc-500">Match id</span>
                  <input
                    className="rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-zinc-100"
                    value={matchId}
                    onChange={(e) => setMatchId(e.target.value)}
                  />
                </label>
                <label className="flex w-full flex-col gap-1 text-sm lg:max-w-[110px]">
                  <span className="text-zinc-500">Seat</span>
                  <input
                    type="number"
                    min={0}
                    className="rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-zinc-100"
                    value={seat}
                    onChange={(e) => setSeat(Number(e.target.value))}
                  />
                </label>
                <p className="flex-1 text-xs text-zinc-600">
                  <code className="rounded bg-zinc-900 px-1 text-zinc-500">/ws/match?matchId=&amp;seat=</code>{" "}
                  · env <code className="text-zinc-500">NEXT_PUBLIC_GAME_SERVER_ORIGIN</code>
                </p>
              </div>
            ) : null}
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/50 p-4 text-sm">
            <h2 className="font-medium text-zinc-300">Connection</h2>
            <p className="mt-2 text-zinc-500">
              Status: <span className="text-zinc-200">{connectionStatus}</span>
            </p>
            {statusDetail ? <p className="mt-1 text-amber-600/90">{statusDetail}</p> : null}
            {!matchId.trim() ? (
              <p className="mt-2 text-xs text-zinc-600">
                Empty match id: start from Matchmaking, or expand “manual match id” below and connect (try{" "}
                <code className="rounded bg-zinc-900 px-1 text-zinc-500">demo</code> if the server exposes it).
              </p>
            ) : (
              <p className="mt-2 text-xs text-zinc-600">
                Match id, seat, and server are saved in this browser — refresh or open Play again to reconnect. Clear the
                match id (advanced) to discard the saved match.
              </p>
            )}
            {welcome ? (
              <p className="mt-2 text-zinc-400">
                Seat {welcome.yourSeatIndex} · {welcome.message ?? "Welcome"}
              </p>
            ) : null}
          </div>

          {commandFeedback ? (
            <div className="rounded-xl border border-rose-900/55 bg-rose-950/25 p-4 text-sm text-rose-100">
              <p>{commandFeedback}</p>
              <button
                type="button"
                onClick={() => clearCommandFeedback()}
                className="mt-3 rounded-md bg-rose-900/50 px-3 py-1.5 text-xs text-rose-200 hover:bg-rose-900/70"
              >
                Dismiss
              </button>
            </div>
          ) : null}

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/50 p-4 text-sm">
            <h2 className="font-medium text-zinc-300">Match</h2>
            {snapshot ? (
              <ul className="mt-2 space-y-1 text-zinc-500">
                <li>
                  Round <span className="text-zinc-300">{snapshot.roundNumber}</span>
                </li>
                <li>
                  Active player <span className="text-zinc-300">{snapshot.activePlayerIndex}</span>
                  {!yourTurn ? <span className="text-zinc-600"> — view only</span> : null}
                </li>
                <li>
                  Map{" "}
                  <span className="text-zinc-300">
                    {snapshot.width}×{snapshot.height}
                  </span>
                </li>
                <li>
                  Units <span className="text-zinc-300">{snapshot.units.length}</span>
                </li>
              </ul>
            ) : (
              <p className="mt-2 text-zinc-500">No snapshot yet.</p>
            )}
            {snapshot ? (
              <div className="mt-3 space-y-2">
                <button
                  type="button"
                  disabled={!yourTurn}
                  onClick={() => getMatchWebSocketClient().endTurn(snapshot.matchId)}
                  className="w-full rounded-md border border-zinc-600 px-3 py-2 text-sm text-zinc-200 enabled:hover:bg-zinc-800 disabled:cursor-not-allowed disabled:opacity-40"
                >
                  End turn
                </button>
                {!yourTurn ? <p className="text-xs text-zinc-600">Waiting for other player…</p> : null}
              </div>
            ) : null}
          </div>

          {snapshot?.players?.length ? (
            <div className="rounded-xl border border-zinc-800 bg-zinc-900/50 p-4 text-sm">
              <h2 className="font-medium text-zinc-300">Players</h2>
              <ul className="mt-2 space-y-2">
                {snapshot.players.map((p) => (
                  <li key={p.seatIndex} className="flex justify-between gap-2 text-zinc-400">
                    <span className="text-zinc-200">{p.displayName}</span>
                    <span>${p.money}</span>
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
        </aside>
      </div>

      <GameInfoStrip snapshot={snapshot} welcome={welcome} missionTitle={missionTitle || "Skirmish"} yourTurn={yourTurn} />
      <GameUnitContextMenu />
      <ProductionModals />
    </div>
  );
}
