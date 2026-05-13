/**
 * Mirrors Java {@code com.game.network.protocol} envelopes and snapshots.
 * Wire JSON uses {@code kind} as the polymorphic discriminator.
 */

export type NetEnvelopeKind =
  | "CS_MOVE_UNIT"
  | "CS_ATTACK_UNIT"
  | "CS_MOVE_AND_ATTACK_UNIT"
  | "CS_FACTORY_BUILD"
  | "CS_WARMACHINE_BUILD"
  | "CS_WARMACHINE_DRILL"
  | "CS_UNIT_REPAIR"
  | "CS_TRANSPORT_PICKUP"
  | "CS_TRANSPORT_DISEMBARK"
  | "CS_CONVERT_TO_ALBATROSS"
  | "CS_CONVERT_TO_LEVIATHAN"
  | "CS_REVERT_TRANSPORT"
  | "CS_END_TURN"
  | "CS_SURRENDER"
  | "CS_PING"
  | "SC_WELCOME"
  | "SC_SNAPSHOT"
  | "SC_COMMAND_RESULT"
  | "SC_ERROR"
  | "SC_PONG";

export interface GridPoint {
  x: number;
  y: number;
}

export interface PlayerSnapshot {
  seatIndex: number;
  displayName: string;
  money: number;
  eliminated: boolean;
}

export interface TileSnapshot {
  terrain: string;
  structure: string | null;
  structureTeam: number | null;
  unitSprite: string | null;
  unitTeam: number | null;
  unitFacing: string | null;
  oreDeposit: boolean | null;
}

export interface UnitSnapshot {
  id: string;
  unitType: string;
  ownerSeatIndex: number;
  x: number;
  y: number;
  health: number;
  hasMoved: boolean;
  cloaked: boolean;
  facing: string;
  warmachineFunds: number | null;
  /** Authoritative path for the last move step (tile-by-tile); use for animation when present. */
  lastMovePathIncludingStart?: GridPoint[] | null;
  /** Schema v3+: pending field repair (round number when started). */
  fieldRepairStartedRound?: number | null;
  /** Schema v3+: when set, unit is cargo inside this transport id (not on its own tile). */
  embarkedInTransportUnitId?: string | null;
  /** When unitType is Albatross/Leviathan after morph, the prior land unit type (revert UX / rules). */
  originalLandUnitType?: string | null;
}

export interface MatchSnapshot {
  schemaVersion: number;
  matchId: string;
  roundNumber: number;
  activePlayerIndex: number;
  teamCount: number;
  width: number;
  height: number;
  tiles: TileSnapshot[][];
  units: UnitSnapshot[];
  players: PlayerSnapshot[];
  matchFinished: boolean;
}

export interface CsPing {
  kind: "CS_PING";
  protocolVersion: number;
}

export interface ScPong {
  kind: "SC_PONG";
  protocolVersion: number;
}

export interface ScWelcome {
  kind: "SC_WELCOME";
  protocolVersion: number;
  matchId: string;
  yourSeatIndex: number;
  message: string | null;
}

export interface ScSnapshot {
  kind: "SC_SNAPSHOT";
  protocolVersion: number;
  snapshot: MatchSnapshot;
}

export interface ScCommandResult {
  kind: "SC_COMMAND_RESULT";
  protocolVersion: number;
  accepted: boolean;
  reasonCode: string | null;
  detail: string | null;
  snapshotIfAccepted: MatchSnapshot | null;
}

export interface ScError {
  kind: "SC_ERROR";
  protocolVersion: number;
  code: string;
  message: string | null;
}

export interface CsMoveUnit {
  kind: "CS_MOVE_UNIT";
  protocolVersion: number;
  matchId: string;
  unitId: string;
  pathIncludingStart: GridPoint[];
}

export interface CsAttackUnit {
  kind: "CS_ATTACK_UNIT";
  protocolVersion: number;
  matchId: string;
  attackerUnitId: string;
  defenderUnitId: string;
}

export interface CsMoveAndAttackUnit {
  kind: "CS_MOVE_AND_ATTACK_UNIT";
  protocolVersion: number;
  matchId: string;
  unitId: string;
  pathIncludingStart: GridPoint[];
  defenderUnitId: string;
}

export interface CsFactoryBuild {
  kind: "CS_FACTORY_BUILD";
  protocolVersion: number;
  matchId: string;
  factoryX: number;
  factoryY: number;
  unitType: string;
}

export interface CsWarmachineBuild {
  kind: "CS_WARMACHINE_BUILD";
  protocolVersion: number;
  matchId: string;
  warmachineUnitId: string;
  unitType: string;
}

export interface CsWarmachineDrill {
  kind: "CS_WARMACHINE_DRILL";
  protocolVersion: number;
  matchId: string;
  warmachineUnitId: string;
}

export interface CsUnitRepair {
  kind: "CS_UNIT_REPAIR";
  protocolVersion: number;
  matchId: string;
  unitId: string;
}

export interface CsTransportPickup {
  kind: "CS_TRANSPORT_PICKUP";
  protocolVersion: number;
  matchId: string;
  transportUnitId: string;
  passengerUnitId: string;
}

export interface CsTransportDisembark {
  kind: "CS_TRANSPORT_DISEMBARK";
  protocolVersion: number;
  matchId: string;
  transportUnitId: string;
}

export interface CsConvertToAlbatross {
  kind: "CS_CONVERT_TO_ALBATROSS";
  protocolVersion: number;
  matchId: string;
  unitId: string;
}

export interface CsConvertToLeviathan {
  kind: "CS_CONVERT_TO_LEVIATHAN";
  protocolVersion: number;
  matchId: string;
  unitId: string;
}

export interface CsRevertTransport {
  kind: "CS_REVERT_TRANSPORT";
  protocolVersion: number;
  matchId: string;
  unitId: string;
}

export interface CsEndTurn {
  kind: "CS_END_TURN";
  protocolVersion: number;
  matchId: string;
}

export interface CsSurrender {
  kind: "CS_SURRENDER";
  protocolVersion: number;
  matchId: string;
}

export type NetEnvelope =
  | CsPing
  | CsMoveUnit
  | CsAttackUnit
  | CsMoveAndAttackUnit
  | CsFactoryBuild
  | CsWarmachineBuild
  | CsWarmachineDrill
  | CsUnitRepair
  | CsTransportPickup
  | CsTransportDisembark
  | CsConvertToAlbatross
  | CsConvertToLeviathan
  | CsRevertTransport
  | CsEndTurn
  | CsSurrender
  | ScWelcome
  | ScSnapshot
  | ScCommandResult
  | ScError
  | ScPong;

export function isNetEnvelope(raw: unknown): raw is NetEnvelope {
  return (
    typeof raw === "object" &&
    raw !== null &&
    "kind" in raw &&
    typeof (raw as { kind: unknown }).kind === "string"
  );
}
