"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import {
  clearActiveLobbySession,
  createLobby,
  fetchLobbyMember,
  fetchMapSummaries,
  fetchOpenLobbies,
  joinLobby,
  readActiveLobbySession,
  rememberLobbyMembership,
  setLobbyMap,
  startLobbyMatch,
  type LobbyListItem,
  type LobbyMemberView,
  type MapSummary,
} from "@/lib/network/lobbyClient";
import { defaultGameServerOrigin } from "@/lib/network/matchClient";
import { useMatchStore } from "@/stores/matchStore";
import { useSessionStore } from "@/stores/sessionStore";

export default function MatchmakingPage() {
  const router = useRouter();
  const gameServerOrigin = useSessionStore((s) => s.gameServerOrigin);
  const setGameServerOrigin = useSessionStore((s) => s.setGameServerOrigin);

  const [lobbies, setLobbies] = useState<LobbyListItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const [activeLobby, setActiveLobby] = useState<{ lobbyId: string; playerId: string } | null>(null);
  const [member, setMember] = useState<LobbyMemberView | null>(null);
  const [maps, setMaps] = useState<MapSummary[]>([]);
  const [mapPick, setMapPick] = useState("");
  const [starting, setStarting] = useState(false);
  const [roomError, setRoomError] = useState<string | null>(null);

  const launchedRef = useRef(false);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await fetchOpenLobbies(gameServerOrigin);
      setLobbies(list);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [gameServerOrigin]);

  useEffect(() => {
    const id = setTimeout(() => void refresh(), 0);
    const t = setInterval(() => void refresh(), 4000);
    return () => {
      clearTimeout(id);
      clearInterval(t);
    };
  }, [refresh]);

  useEffect(() => {
    queueMicrotask(() => {
      const s = readActiveLobbySession();
      if (s) setActiveLobby(s);
    });
  }, []);

  const goToGame = useCallback(
    (matchId: string, seat: number) => {
      if (launchedRef.current) return;
      launchedRef.current = true;
      clearActiveLobbySession();
      setActiveLobby(null);
      setMember(null);
      useMatchStore.getState().disconnect();
      const session = useSessionStore.getState();
      session.setMatchId(matchId.trim());
      session.setSeat(seat);
      router.push("/play");
    },
    [router]
  );

  useEffect(() => {
    launchedRef.current = false;
  }, [activeLobby?.lobbyId, activeLobby?.playerId]);

  useEffect(() => {
    if (!activeLobby) return;

    const lobby = activeLobby;
    let cancelled = false;
    async function poll() {
      try {
        const m = await fetchLobbyMember(gameServerOrigin, lobby.lobbyId, lobby.playerId);
        if (cancelled) return;
        setRoomError(null);
        setMember(m);
        if (m.selectedMapSlug) {
          setMapPick(m.selectedMapSlug);
        }
        if (m.started && m.matchId) {
          goToGame(m.matchId, m.yourSeatIndex);
        }
      } catch (e) {
        if (cancelled) return;
        setRoomError(e instanceof Error ? e.message : String(e));
      }
    }

    void poll();
    const t = setInterval(() => void poll(), 1500);
    return () => {
      cancelled = true;
      clearInterval(t);
    };
  }, [activeLobby, gameServerOrigin, goToGame]);

  useEffect(() => {
    if (!activeLobby) return;
    let cancelled = false;
    void fetchMapSummaries(gameServerOrigin)
      .then((list) => {
        if (!cancelled) setMaps(list);
      })
      .catch(() => {
        if (!cancelled) setMaps([]);
      });
    return () => {
      cancelled = true;
    };
  }, [activeLobby, gameServerOrigin]);

  async function handleCreate() {
    setCreating(true);
    setError(null);
    try {
      const r = await createLobby(gameServerOrigin);
      rememberLobbyMembership(r.lobbyId, r.playerId);
      setActiveLobby({ lobbyId: r.lobbyId, playerId: r.playerId });
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setCreating(false);
    }
  }

  async function handleJoin(lobbyId: string) {
    setError(null);
    try {
      const r = await joinLobby(gameServerOrigin, lobbyId);
      rememberLobbyMembership(r.lobbyId, r.playerId);
      setActiveLobby({ lobbyId: r.lobbyId, playerId: r.playerId });
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  function leaveRoom() {
    clearActiveLobbySession();
    setActiveLobby(null);
    setMember(null);
    setMaps([]);
    setMapPick("");
    setRoomError(null);
    void refresh();
  }

  async function handleMapChange(nextSlug: string) {
    if (!activeLobby || !member) return;
    const isHost = member.hostPlayerId === activeLobby.playerId;
    if (!isHost) return;
    setMapPick(nextSlug);
    setRoomError(null);
    try {
      await setLobbyMap(gameServerOrigin, activeLobby.lobbyId, activeLobby.playerId, nextSlug);
      const m = await fetchLobbyMember(gameServerOrigin, activeLobby.lobbyId, activeLobby.playerId);
      setMember(m);
    } catch (e) {
      setRoomError(e instanceof Error ? e.message : String(e));
    }
  }

  async function handleStart() {
    if (!activeLobby || !member) return;
    setStarting(true);
    setRoomError(null);
    try {
      const mid = await startLobbyMatch(gameServerOrigin, activeLobby.lobbyId, activeLobby.playerId);
      goToGame(mid, member.yourSeatIndex);
    } catch (e) {
      setRoomError(e instanceof Error ? e.message : String(e));
    } finally {
      setStarting(false);
    }
  }

  const isHost = Boolean(
    activeLobby && member && member.hostPlayerId === activeLobby.playerId
  );
  const canStart =
    isHost &&
    member &&
    !member.started &&
    member.playerCount >= 1 &&
    Boolean(member.selectedMapSlug);

  if (activeLobby) {
    return (
      <div className="flex max-w-3xl flex-col gap-6">
        <div>
          <h1 className="text-2xl font-semibold text-zinc-100">Lobby</h1>
          <p className="mt-2 text-sm text-zinc-500">
            Server <span className="text-zinc-400">{gameServerOrigin}</span> · room{" "}
            <span className="font-mono text-zinc-400">{activeLobby.lobbyId.slice(0, 8)}…</span>
          </p>
          <p className="mt-1 text-xs text-zinc-600">
            To use a different server URL, leave this room first — the lobby list view has the server
            field.
          </p>
        </div>

        {roomError ? (
          <p className="rounded-md border border-amber-900/50 bg-amber-950/40 px-3 py-2 text-sm text-amber-200/90">
            {roomError}
          </p>
        ) : null}

        <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
          <h2 className="text-sm font-medium text-zinc-400">Players</h2>
          {!member ? (
            <p className="mt-2 text-sm text-zinc-500">Loading room…</p>
          ) : (
            <ul className="mt-2 space-y-1 text-sm text-zinc-300">
              {member.players.map((p) => (
                <li key={p.seatIndex}>
                  Seat {p.seatIndex}: {p.displayLabel}
                  {p.host ? <span className="ml-2 text-zinc-500">(host)</span> : null}
                  {p.seatIndex === member.yourSeatIndex ? (
                    <span className="ml-2 text-sky-400/90">you</span>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
          <h2 className="text-sm font-medium text-zinc-400">Map</h2>
          {isHost ? (
            <label className="mt-2 flex flex-col gap-1 text-sm">
              <span className="text-zinc-500">Choose map (host)</span>
              <select
                className="rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-zinc-100"
                value={mapPick}
                onChange={(e) => void handleMapChange(e.target.value)}
                disabled={!maps.length}
              >
                <option value="">{maps.length ? "Select a map…" : "Loading maps…"}</option>
                {maps.map((m) => (
                  <option key={m.id} value={m.slug}>
                    {m.slug}
                  </option>
                ))}
              </select>
            </label>
          ) : (
            <p className="mt-2 text-sm text-zinc-500">
              {member?.selectedMapSlug ? (
                <>
                  Host selected: <span className="text-zinc-300">{member.selectedMapSlug}</span>
                </>
              ) : (
                "Waiting for host to pick a map…"
              )}
            </p>
          )}
        </div>

        <div className="flex flex-wrap gap-2">
          {isHost ? (
            <button
              type="button"
              disabled={!canStart || starting}
              onClick={() => void handleStart()}
              className="rounded-md bg-emerald-700 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-600 disabled:opacity-50"
            >
              {starting ? "Starting…" : "Start game"}
            </button>
          ) : (
            <p className="text-sm text-zinc-500">Waiting for host to start…</p>
          )}
          <button
            type="button"
            onClick={leaveRoom}
            className="rounded-md border border-zinc-600 px-4 py-2 text-sm text-zinc-300 hover:bg-zinc-800"
          >
            Leave room
          </button>
        </div>

        <p className="text-xs text-zinc-600">
          Host can start with 1-4 humans; empty seats are auto-filled by AI. When the match starts, this
          page sends everyone to Play and opens the WebSocket automatically.
        </p>
      </div>
    );
  }

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-zinc-100">Matchmaking</h1>
        <p className="mt-2 text-sm text-zinc-500">
          Open lobbies on your server (<span className="text-zinc-400">{gameServerOrigin}</span>).
          Create or join a room (up to 4 players), host picks a map and starts at any time — remaining
          seats are filled by AI, then all players are routed to the game and connected.
        </p>
      </div>

      <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-zinc-500">Game server (HTTP origin)</span>
          <input
            className="rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-zinc-100"
            value={gameServerOrigin}
            onChange={(e) => setGameServerOrigin(e.target.value)}
            placeholder={defaultGameServerOrigin()}
          />
        </label>
        <button
          type="button"
          className="mt-2 text-left text-xs text-sky-400/90 underline decoration-sky-800 hover:text-sky-300"
          onClick={() => setGameServerOrigin(defaultGameServerOrigin())}
        >
          Reset to NEXT_PUBLIC_GAME_SERVER_ORIGIN (build default)
        </button>
        <p className="mt-2 text-xs text-zinc-600">
          This value is stored in the browser with your match id. Changing only{" "}
          <code className="rounded bg-zinc-900 px-1 text-zinc-500">.env.local</code> does not replace a
          saved origin — edit the field above or use reset. On an HTTPS site, use an{" "}
          <code className="text-zinc-500">https://</code> API base and allow this page’s origin in{" "}
          <code className="text-zinc-500">battalion.cors.allowed-origins</code> on the Java server.
        </p>
      </div>

      {error ? (
        <p className="rounded-md border border-amber-900/50 bg-amber-950/40 px-3 py-2 text-sm text-amber-200/90">
          {error}
        </p>
      ) : null}

      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          disabled={creating}
          onClick={() => void handleCreate()}
          className="rounded-md bg-emerald-700 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-600 disabled:opacity-50"
        >
          {creating ? "Creating…" : "Create lobby"}
        </button>
        <button
          type="button"
          onClick={() => void refresh()}
          disabled={loading}
          className="rounded-md border border-zinc-600 px-4 py-2 text-sm text-zinc-300 hover:bg-zinc-800 disabled:opacity-50"
        >
          Refresh
        </button>
        <Link
          href="/play"
          className="rounded-md border border-zinc-600 px-4 py-2 text-sm text-zinc-300 hover:bg-zinc-800"
        >
          Play (manual match id)
        </Link>
      </div>

      <div>
        <h2 className="text-sm font-medium uppercase tracking-wide text-zinc-500">Open lobbies</h2>
        {loading && lobbies.length === 0 ? (
          <p className="mt-3 text-sm text-zinc-500">Loading…</p>
        ) : lobbies.length === 0 ? (
          <p className="mt-3 text-sm text-zinc-500">No open lobbies — create one.</p>
        ) : (
          <ul className="mt-3 divide-y divide-zinc-800 rounded-xl border border-zinc-800 bg-zinc-900/40">
            {lobbies.map((L) => (
              <li
                key={L.lobbyId}
                className="flex flex-wrap items-center justify-between gap-3 px-4 py-3"
              >
                <div className="text-sm">
                  <span className="font-mono text-zinc-300">{L.lobbyId.slice(0, 8)}…</span>
                  <span className="ml-2 text-zinc-500">
                    {L.playerCount}/{L.maxPlayers} players
                    {L.selectedMapSlug ? (
                      <span className="ml-2 text-zinc-400">· map: {L.selectedMapSlug}</span>
                    ) : null}
                  </span>
                </div>
                <button
                  type="button"
                  onClick={() => void handleJoin(L.lobbyId)}
                  className="rounded-md bg-sky-800 px-3 py-1.5 text-sm text-white hover:bg-sky-700"
                >
                  Join
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
