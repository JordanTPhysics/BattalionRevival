"use client";

import { useEffect, useRef, useState } from "react";
import { GameCanvas, type GameInteractionConfig } from "@/components/game/GameCanvas";
import { GameInfoStrip } from "@/components/game/GameInfoStrip";
import { ProductionModals } from "@/components/game/ProductionModals";
import { GameUnitContextMenu } from "@/components/game/GameUnitContextMenu";
import { defaultGameServerOrigin } from "@/lib/network/matchClient";
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
  const matchId = useSessionStore((s) => s.matchId);


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
          <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-4 text-sm">
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
            <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-4 text-sm">
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
