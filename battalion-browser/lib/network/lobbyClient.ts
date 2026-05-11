/**
 * REST lobby API — browse, create, join, set map (host), start (host).
 */

import {
  assertBrowserCanCallGameApi,
  humanizeGameServerFetchFailure,
} from "@/lib/network/browserApiGuards";
import { normalizeServerRoot } from "@/lib/network/mapCatalogClient";

async function gameFetch(url: string, hint: string, init?: RequestInit): Promise<Response> {
  try {
    return await fetch(url, init);
  } catch (e) {
    throw new Error(humanizeGameServerFetchFailure(e, hint));
  }
}

export interface LobbyListItem {
  lobbyId: string;
  playerCount: number;
  maxPlayers: number;
  started: boolean;
  selectedMapSlug: string | null;
}

export interface CreateLobbyResult {
  lobbyId: string;
  playerId: string;
  seatIndex: number;
}

export interface JoinLobbyResult {
  lobbyId: string;
  playerId: string;
  seatIndex: number;
}

export interface LobbyPublicView {
  lobbyId: string;
  started: boolean;
  playerCount: number;
  maxPlayers: number;
  selectedMapSlug: string | null;
  matchId: string | null;
}

export interface LobbyPlayerRow {
  seatIndex: number;
  displayLabel: string;
  host: boolean;
}

export interface LobbyMemberView {
  lobbyId: string;
  started: boolean;
  playerCount: number;
  maxPlayers: number;
  selectedMapSlug: string | null;
  matchId: string | null;
  hostPlayerId: string;
  yourSeatIndex: number;
  players: LobbyPlayerRow[];
}

export interface MapSummary {
  id: number;
  slug: string;
  ownerUsername: string;
  schemaVersion: number;
  createdAt: string;
}

export function lobbyPlayerStorageKey(lobbyId: string): string {
  return `battalion.lobby.${lobbyId}.playerId`;
}

/** Single pre-game session per tab — used to resume matchmaking after refresh. */
const ACTIVE_LOBBY_SESSION_KEY = "battalion.activeLobby";

export interface ActiveLobbySession {
  readonly lobbyId: string;
  readonly playerId: string;
}

export function readActiveLobbySession(): ActiveLobbySession | null {
  if (typeof sessionStorage === "undefined") return null;
  try {
    const raw = sessionStorage.getItem(ACTIVE_LOBBY_SESSION_KEY);
    if (!raw) return null;
    const o = JSON.parse(raw) as unknown;
    if (!o || typeof o !== "object") return null;
    const lobbyId = (o as { lobbyId?: unknown }).lobbyId;
    const playerId = (o as { playerId?: unknown }).playerId;
    if (typeof lobbyId !== "string" || typeof playerId !== "string") return null;
    return { lobbyId, playerId };
  } catch {
    return null;
  }
}

export function writeActiveLobbySession(v: ActiveLobbySession): void {
  if (typeof sessionStorage === "undefined") return;
  sessionStorage.setItem(ACTIVE_LOBBY_SESSION_KEY, JSON.stringify(v));
}

export function clearActiveLobbySession(): void {
  if (typeof sessionStorage === "undefined") return;
  sessionStorage.removeItem(ACTIVE_LOBBY_SESSION_KEY);
}

/** Call after successful {@link createLobby} / {@link joinLobby} responses. */
export function rememberLobbyMembership(lobbyId: string, playerId: string): void {
  if (typeof sessionStorage === "undefined") return;
  sessionStorage.setItem(lobbyPlayerStorageKey(lobbyId), playerId);
  writeActiveLobbySession({ lobbyId, playerId });
}

export async function fetchOpenLobbies(
  serverRoot: string
): Promise<LobbyListItem[]> {
  const root = normalizeServerRoot(serverRoot);
  assertBrowserCanCallGameApi(root);
  const res = await gameFetch(`${root}/api/lobbies`, "GET /api/lobbies");
  if (!res.ok) {
    throw new Error(`${res.status} listing lobbies`);
  }
  return res.json() as Promise<LobbyListItem[]>;
}

export async function createLobby(
  serverRoot: string,
  displayLabel?: string
): Promise<CreateLobbyResult> {
  const root = normalizeServerRoot(serverRoot);
  assertBrowserCanCallGameApi(root);
  const res = await gameFetch(`${root}/api/lobbies`, "POST /api/lobbies", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ displayLabel: displayLabel?.trim() || undefined }),
  });
  if (!res.ok) {
    throw new Error((await res.text()) || `${res.status} create lobby`);
  }
  return res.json() as Promise<CreateLobbyResult>;
}

export async function joinLobby(
  serverRoot: string,
  lobbyId: string,
  displayLabel?: string
): Promise<JoinLobbyResult> {
  const root = normalizeServerRoot(serverRoot);
  assertBrowserCanCallGameApi(root);
  const res = await gameFetch(`${root}/api/lobbies/${encodeURIComponent(lobbyId)}/join`, "POST /api/lobbies/.../join", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ displayLabel: displayLabel?.trim() || undefined }),
  });
  if (!res.ok) {
    throw new Error((await res.text()) || `${res.status} join lobby`);
  }
  return res.json() as Promise<JoinLobbyResult>;
}

export async function fetchLobbyMember(
  serverRoot: string,
  lobbyId: string,
  playerId: string
): Promise<LobbyMemberView> {
  const root = normalizeServerRoot(serverRoot);
  assertBrowserCanCallGameApi(root);
  const q = new URLSearchParams({ playerId });
  const res = await gameFetch(
    `${root}/api/lobbies/${encodeURIComponent(lobbyId)}?${q}`,
    "GET /api/lobbies/:id"
  );
  if (!res.ok) {
    throw new Error((await res.text()) || `${res.status} lobby state`);
  }
  return res.json() as Promise<LobbyMemberView>;
}

export async function setLobbyMap(
  serverRoot: string,
  lobbyId: string,
  playerId: string,
  mapSlug: string
): Promise<void> {
  const root = normalizeServerRoot(serverRoot);
  assertBrowserCanCallGameApi(root);
  const res = await gameFetch(
    `${root}/api/lobbies/${encodeURIComponent(lobbyId)}/map`,
    "POST /api/lobbies/.../map",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ playerId, mapSlug }),
    }
  );
  if (!res.ok) {
    throw new Error((await res.text()) || `${res.status} set map`);
  }
}

export async function startLobbyMatch(
  serverRoot: string,
  lobbyId: string,
  playerId: string
): Promise<string> {
  const root = normalizeServerRoot(serverRoot);
  assertBrowserCanCallGameApi(root);
  const res = await gameFetch(
    `${root}/api/lobbies/${encodeURIComponent(lobbyId)}/start`,
    "POST /api/lobbies/.../start",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ playerId }),
    }
  );
  if (!res.ok) {
    throw new Error((await res.text()) || `${res.status} start match`);
  }
  return res.text();
}

export async function fetchMapSummaries(
  serverRoot: string
): Promise<MapSummary[]> {
  const root = normalizeServerRoot(serverRoot);
  assertBrowserCanCallGameApi(root);
  const res = await gameFetch(`${root}/api/maps`, "GET /api/maps");
  if (!res.ok) {
    throw new Error(`${res.status} listing maps`);
  }
  return res.json() as Promise<MapSummary[]>;
}
