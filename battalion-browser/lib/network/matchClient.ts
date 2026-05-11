import { NETWORK_PROTOCOL_VERSION } from "@/lib/protocol/constants";
import {
  isNetEnvelope,
  type CsAttackUnit,
  type CsEndTurn,
  type CsFactoryBuild,
  type CsMoveUnit,
  type CsMoveAndAttackUnit,
  type CsPing,
  type CsSurrender,
  type CsWarmachineBuild,
  type MatchSnapshot,
  type NetEnvelope,
  type ScCommandResult,
  type ScError,
  type ScPong,
  type ScWelcome,
} from "@/lib/protocol/types";

export type ConnectionStatus = "idle" | "connecting" | "connected" | "disconnected" | "error";

export function httpOriginToWebSocketBase(httpOrigin: string): string {
  if (httpOrigin.startsWith("https://")) {
    return "wss://" + httpOrigin.slice("https://".length);
  }
  if (httpOrigin.startsWith("http://")) {
    return "ws://" + httpOrigin.slice("http://".length);
  }
  return httpOrigin;
}

export function buildMatchWebSocketUrl(
  httpOrigin: string,
  matchId: string,
  seat: number
): string {
  const base = httpOriginToWebSocketBase(httpOrigin).replace(/\/$/, "");
  const path = "/ws/match";
  const q = new URLSearchParams({ matchId, seat: String(seat) });
  return `${base}${path}?${q.toString()}`;
}

export function defaultGameServerOrigin(): string {
  if (typeof process !== "undefined" && process.env.NEXT_PUBLIC_GAME_SERVER_ORIGIN) {
    return process.env.NEXT_PUBLIC_GAME_SERVER_ORIGIN;
  }
  return "https://battalionrevival.onrender.com/";
}

export interface MatchClientHandlers {
  onStatus: (s: ConnectionStatus, detail?: string) => void;
  onWelcome: (w: ScWelcome) => void;
  onSnapshot: (s: MatchSnapshot) => void;
  onCommandResult: (r: ScCommandResult) => void;
  onPong: (p: ScPong) => void;
  onServerError: (e: ScError) => void;
  onRawUnknown: (json: unknown) => void;
}

function handleEnvelope(env: NetEnvelope, h: MatchClientHandlers): void {
  switch (env.kind) {
    case "SC_WELCOME":
      h.onWelcome(env);
      break;
    case "SC_SNAPSHOT":
      h.onSnapshot(env.snapshot);
      break;
    case "SC_COMMAND_RESULT":
      h.onCommandResult(env);
      break;
    case "SC_PONG":
      h.onPong(env);
      break;
    case "SC_ERROR":
      h.onServerError(env);
      break;
    default:
      h.onRawUnknown(env);
  }
}

export class MatchWebSocketClient {
  private socket: WebSocket | null = null;

  connect(url: string, handlers: MatchClientHandlers): void {
    this.disconnect();
    handlers.onStatus("connecting");
    const ws = new WebSocket(url);
    this.socket = ws;

    ws.onopen = () => {
      handlers.onStatus("connected");
      const ping: CsPing = { kind: "CS_PING", protocolVersion: NETWORK_PROTOCOL_VERSION };
      ws.send(JSON.stringify(ping));
    };

    ws.onerror = () => {
      handlers.onStatus("error", "WebSocket error");
    };

    ws.onclose = () => {
      handlers.onStatus("disconnected");
      if (this.socket === ws) {
        this.socket = null;
      }
    };

    ws.onmessage = (ev) => {
      try {
        const raw: unknown = JSON.parse(String(ev.data));
        if (!isNetEnvelope(raw)) {
          handlers.onRawUnknown(raw);
          return;
        }
        handleEnvelope(raw, handlers);
      } catch {
        handlers.onStatus("error", "Invalid JSON from server");
      }
    };
  }

  disconnect(): void {
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
  }

  send(envelope: NetEnvelope): boolean {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      return false;
    }
    this.socket.send(JSON.stringify(envelope));
    return true;
  }

  moveUnit(cmd: Omit<CsMoveUnit, "kind" | "protocolVersion">): boolean {
    const payload: CsMoveUnit = {
      kind: "CS_MOVE_UNIT",
      protocolVersion: NETWORK_PROTOCOL_VERSION,
      matchId: cmd.matchId,
      unitId: cmd.unitId,
      pathIncludingStart: cmd.pathIncludingStart,
    };
    return this.send(payload);
  }

  attackUnit(cmd: Omit<CsAttackUnit, "kind" | "protocolVersion">): boolean {
    const payload: CsAttackUnit = {
      kind: "CS_ATTACK_UNIT",
      protocolVersion: NETWORK_PROTOCOL_VERSION,
      matchId: cmd.matchId,
      attackerUnitId: cmd.attackerUnitId,
      defenderUnitId: cmd.defenderUnitId,
    };
    return this.send(payload);
  }

  moveAndAttackUnit(cmd: Omit<CsMoveAndAttackUnit, "kind" | "protocolVersion">): boolean {
    const payload: CsMoveAndAttackUnit = {
      kind: "CS_MOVE_AND_ATTACK_UNIT",
      protocolVersion: NETWORK_PROTOCOL_VERSION,
      matchId: cmd.matchId,
      unitId: cmd.unitId,
      pathIncludingStart: cmd.pathIncludingStart,
      defenderUnitId: cmd.defenderUnitId,
    };
    return this.send(payload);
  }

  factoryBuild(cmd: Omit<CsFactoryBuild, "kind" | "protocolVersion">): boolean {
    const payload: CsFactoryBuild = {
      kind: "CS_FACTORY_BUILD",
      protocolVersion: NETWORK_PROTOCOL_VERSION,
      matchId: cmd.matchId,
      factoryX: cmd.factoryX,
      factoryY: cmd.factoryY,
      unitType: cmd.unitType,
    };
    return this.send(payload);
  }

  warmachineBuild(cmd: Omit<CsWarmachineBuild, "kind" | "protocolVersion">): boolean {
    const payload: CsWarmachineBuild = {
      kind: "CS_WARMACHINE_BUILD",
      protocolVersion: NETWORK_PROTOCOL_VERSION,
      matchId: cmd.matchId,
      warmachineUnitId: cmd.warmachineUnitId,
      unitType: cmd.unitType,
    };
    return this.send(payload);
  }
  endTurn(matchId: string): boolean {
    const payload: CsEndTurn = {
      kind: "CS_END_TURN",
      protocolVersion: NETWORK_PROTOCOL_VERSION,
      matchId,
    };
    return this.send(payload);
  }

  surrender(matchId: string): boolean {
    const payload: CsSurrender = {
      kind: "CS_SURRENDER",
      protocolVersion: NETWORK_PROTOCOL_VERSION,
      matchId,
    };
    return this.send(payload);
  }
}
