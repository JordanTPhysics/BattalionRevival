import { create } from "zustand";
import { defaultGameServerOrigin } from "@/lib/network/matchClient";

const REJOIN_STORAGE_KEY = "battalion-browser:rejoin-session";

export interface SessionState {
  gameServerOrigin: string;
  matchId: string;
  seat: number;
  setGameServerOrigin: (o: string) => void;
  setMatchId: (id: string) => void;
  setSeat: (seat: number) => void;
}

function loadPersistedRejoin(): Partial<Pick<SessionState, "gameServerOrigin" | "matchId" | "seat">> {
  if (typeof window === "undefined") {
    return {};
  }
  try {
    const raw = window.localStorage.getItem(REJOIN_STORAGE_KEY);
    if (!raw) {
      return {};
    }
    const o = JSON.parse(raw) as Partial<{ gameServerOrigin: string; matchId: string; seat: unknown }>;
    const out: Partial<Pick<SessionState, "gameServerOrigin" | "matchId" | "seat">> = {};
    if (typeof o.gameServerOrigin === "string" && o.gameServerOrigin.trim().length > 0) {
      out.gameServerOrigin = o.gameServerOrigin.trim();
    }
    if (typeof o.matchId === "string") {
      out.matchId = o.matchId.trim();
    }
    if (typeof o.seat === "number" && Number.isFinite(o.seat)) {
      out.seat = Math.max(0, Math.floor(o.seat));
    }
    return out;
  } catch {
    return {};
  }
}

/**
 * Merge rejoin fields from `localStorage` into the store. Run once on the client after mount
 * (see `SessionStorageHydration`) so SSR and the first hydration pass both use
 * `defaultGameServerOrigin()` only, then the browser’s saved origin / match / seat apply.
 *
 * Note: a saved `gameServerOrigin` in `localStorage` overrides `NEXT_PUBLIC_GAME_SERVER_ORIGIN`
 * until you change it in the UI or clear site data for this origin.
 */
export function hydrateSessionFromBrowserStorage(): void {
  const p = loadPersistedRejoin();
  const patch: Partial<Pick<SessionState, "gameServerOrigin" | "matchId" | "seat">> = {};
  if (typeof p.gameServerOrigin === "string" && p.gameServerOrigin.trim().length > 0) {
    patch.gameServerOrigin = p.gameServerOrigin.trim();
  }
  if (typeof p.matchId === "string") {
    patch.matchId = p.matchId.trim();
  }
  if (typeof p.seat === "number" && Number.isFinite(p.seat)) {
    patch.seat = Math.max(0, Math.floor(p.seat));
  }
  if (Object.keys(patch).length > 0) {
    useSessionStore.setState(patch);
  }
}

export const useSessionStore = create<SessionState>((set) => ({
  gameServerOrigin: defaultGameServerOrigin(),
  matchId: "",
  seat: 0,
  setGameServerOrigin: (gameServerOrigin) => set({ gameServerOrigin }),
  setMatchId: (matchId) => set({ matchId }),
  setSeat: (seat) => set({ seat }),
}));

if (typeof window !== "undefined") {
  useSessionStore.subscribe((state) => {
    try {
      window.localStorage.setItem(
        REJOIN_STORAGE_KEY,
        JSON.stringify({
          gameServerOrigin: state.gameServerOrigin,
          matchId: state.matchId,
          seat: state.seat,
        })
      );
    } catch {
      /* quota / privacy mode */
    }
  });
}
