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

const persisted = loadPersistedRejoin();

export const useSessionStore = create<SessionState>((set) => ({
  gameServerOrigin: persisted.gameServerOrigin ?? defaultGameServerOrigin(),
  matchId: persisted.matchId ?? "",
  seat: typeof persisted.seat === "number" ? persisted.seat : 0,
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
