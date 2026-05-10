import { create } from "zustand";
import { MatchWebSocketClient, buildMatchWebSocketUrl, type ConnectionStatus } from "@/lib/network/matchClient";
import type { MatchSnapshot, ScWelcome } from "@/lib/protocol/types";
import { useSessionStore } from "@/stores/sessionStore";

let sharedClient: MatchWebSocketClient | null = null;

function getClient(): MatchWebSocketClient {
  if (!sharedClient) {
    sharedClient = new MatchWebSocketClient();
  }
  return sharedClient;
}

export interface MatchStore {
  connectionStatus: ConnectionStatus;
  statusDetail: string | null;
  welcome: ScWelcome | null;
  snapshot: MatchSnapshot | null;
  commandFeedback: string | null;

  connect: () => void;
  disconnect: () => void;
  setSnapshot: (s: MatchSnapshot | null) => void;
  clearCommandFeedback: () => void;
}

export const useMatchStore = create<MatchStore>((set) => ({
  connectionStatus: "idle",
  statusDetail: null,
  welcome: null,
  snapshot: null,
  commandFeedback: null,

  setSnapshot: (snapshot) => set({ snapshot }),

  clearCommandFeedback: () => set({ commandFeedback: null }),

  connect: () => {
    const session = useSessionStore.getState();
    const url = buildMatchWebSocketUrl(session.gameServerOrigin, session.matchId, session.seat);
    const client = getClient();

    client.connect(url, {
      onStatus: (connectionStatus, detail) =>
        set({ connectionStatus, statusDetail: detail ?? null }),
      onWelcome: (welcome) => set({ welcome }),
      onSnapshot: (snapshot) =>
        set({ snapshot, commandFeedback: null }),

      onCommandResult: (r) => {
        if (r.snapshotIfAccepted != null) {
          set({ snapshot: r.snapshotIfAccepted });
        }
        if (!r.accepted) {
          const msg = [r.reasonCode ?? "", r.detail ?? ""].filter((s) => s.length > 0).join(": ");
          if (msg) {
            set({ commandFeedback: msg });
          }
        } else {
          set({ commandFeedback: null });
        }
      },
      onPong: () => {},
      onServerError: () => {},
      onRawUnknown: () => {},
    });
  },

  disconnect: () => {
    getClient().disconnect();
    set({
      connectionStatus: "idle",
      welcome: null,
      snapshot: null,
      commandFeedback: null,
    });
  },
}));

export function getMatchWebSocketClient(): MatchWebSocketClient {
  return getClient();
}
