/**
 * Mirrors Swing {@code GameWindow.MapPanel#buildUnitContextMenu} eligibility for the active player's unit.
 */
import type { MatchSnapshot, UnitSnapshot } from "@/lib/protocol/types";
import { getUnitTypeStats } from "@/lib/game/unitTypeCatalog";
import type { MovementKind } from "@/lib/game/unitTypeCatalog";
import { canTraverseKind } from "@/lib/game/terrainMovement";

/** Mirrors {@link com.game.engine.PlayableGameSession#WARMACHINE_DRILL_INCOME}. */
export const WARMACHINE_DRILL_INCOME = 600;

export function prettyUnitTypeName(unitType: string): string {
  if (!unitType) return "cargo";
  return unitType.charAt(0).toUpperCase() + unitType.slice(1).toLowerCase();
}

function isDepletedOreTerrain(terrain: string): boolean {
  return terrain.startsWith("DEPLETED_ORE_DEPOSIT");
}

function isWarmachineDrillableOreTerrain(terrain: string): boolean {
  return (
    terrain === "ENRICHED_ORE_DEPOSIT_1" ||
    terrain === "ENRICHED_ORE_DEPOSIT_2" ||
    terrain === "ORE_DEPOSIT_1" ||
    terrain === "ORE_DEPOSIT_2"
  );
}

function tileAt(snapshot: MatchSnapshot, x: number, y: number) {
  return snapshot.tiles[y]?.[x];
}

function isCoastalShoreForSeaFactory(terrain: string): boolean {
  return terrain.startsWith("SHORE_");
}

function isEligiblePickupPassenger(transportType: string, passengerType: string): boolean {
  const pt = getUnitTypeStats(passengerType);
  if (!pt) return false;
  if (transportType === "Albatross") {
    return pt.movementKind === "FOOT";
  }
  if (transportType === "Leviathan") {
    return pt.movementKind === "FOOT" || pt.movementKind === "WHEELED" || pt.movementKind === "TRACKED";
  }
  return false;
}

export function canWarmachineFabricateMenu(
  snapshot: MatchSnapshot,
  u: UnitSnapshot,
  yourSeatIndex: number
): boolean {
  if (snapshot.matchFinished || snapshot.activePlayerIndex !== yourSeatIndex) return false;
  return u.unitType === "Warmachine" && u.health > 0 && !u.hasMoved && u.ownerSeatIndex === yourSeatIndex;
}

export function canWarmachineDrillMenu(
  snapshot: MatchSnapshot,
  u: UnitSnapshot,
  yourSeatIndex: number
): boolean {
  if (snapshot.matchFinished || snapshot.activePlayerIndex !== yourSeatIndex) return false;
  if (u.unitType !== "Warmachine" || u.health <= 0 || u.hasMoved || u.ownerSeatIndex !== yourSeatIndex) {
    return false;
  }
  const t = tileAt(snapshot, u.x, u.y);
  if (!t) return false;
  const terrain = t.terrain;
  if (isDepletedOreTerrain(terrain)) return false;
  if (isWarmachineDrillableOreTerrain(terrain)) return true;
  return t.oreDeposit === true;
}

export function canFieldRepairMenu(
  snapshot: MatchSnapshot,
  u: UnitSnapshot,
  yourSeatIndex: number
): boolean {
  if (snapshot.matchFinished || snapshot.activePlayerIndex !== yourSeatIndex) return false;
  if (u.ownerSeatIndex !== yourSeatIndex || u.health <= 0 || u.hasMoved) return false;
  if (u.embarkedInTransportUnitId != null && u.embarkedInTransportUnitId !== "") return false;
  if (u.fieldRepairStartedRound != null) return false;
  const st = getUnitTypeStats(u.unitType);
  const maxHp = st?.startingHealth ?? 0;
  return maxHp > 0 && u.health < maxHp;
}

export function findPassengerOnTransport(
  snapshot: MatchSnapshot,
  transportId: string
): UnitSnapshot | undefined {
  return snapshot.units.find(
    (un) =>
      un.health > 0 &&
      un.embarkedInTransportUnitId != null &&
      un.embarkedInTransportUnitId !== "" &&
      un.embarkedInTransportUnitId === transportId
  );
}

export function canTransportDisembarkMenu(
  snapshot: MatchSnapshot,
  u: UnitSnapshot,
  yourSeatIndex: number
): boolean {
  if (snapshot.matchFinished || snapshot.activePlayerIndex !== yourSeatIndex) return false;
  if (u.ownerSeatIndex !== yourSeatIndex || u.health <= 0 || u.hasMoved) return false;
  const st = getUnitTypeStats(u.unitType);
  if (!st?.isTransport) return false;
  const cargo = findPassengerOnTransport(snapshot, u.id);
  return cargo != null && cargo.health > 0;
}

export interface PickupMenuEntry {
  directionLabel: string;
  passengerUnitId: string;
  passengerUnitType: string;
}

const ORTHO_DIRS: Array<{ dx: number; dy: number; label: string }> = [
  { dx: 0, dy: -1, label: "north" },
  { dx: 1, dy: 0, label: "east" },
  { dx: 0, dy: 1, label: "south" },
  { dx: -1, dy: 0, label: "west" },
];

export function transportPickupMenuEntries(
  snapshot: MatchSnapshot,
  transport: UnitSnapshot,
  yourSeatIndex: number
): PickupMenuEntry[] {
  if (snapshot.matchFinished || snapshot.activePlayerIndex !== yourSeatIndex) return [];
  if (transport.ownerSeatIndex !== yourSeatIndex || transport.health <= 0 || transport.hasMoved) return [];
  const tStats = getUnitTypeStats(transport.unitType);
  if (!tStats?.isTransport) return [];
  if (findPassengerOnTransport(snapshot, transport.id)) return [];

  const out: PickupMenuEntry[] = [];
  for (const { dx, dy, label } of ORTHO_DIRS) {
    const nx = transport.x + dx;
    const ny = transport.y + dy;
    if (nx < 0 || ny < 0 || nx >= snapshot.width || ny >= snapshot.height) continue;
    const neighbor = snapshot.units.find(
      (un) =>
        un.x === nx &&
        un.y === ny &&
        un.health > 0 &&
        (un.embarkedInTransportUnitId == null || un.embarkedInTransportUnitId === "") &&
        un.ownerSeatIndex === transport.ownerSeatIndex
    );
    if (!neighbor || neighbor.id === transport.id) continue;
    if (neighbor.hasMoved) continue;
    if (!isEligiblePickupPassenger(transport.unitType, neighbor.unitType)) continue;
    out.push({ directionLabel: label, passengerUnitId: neighbor.id, passengerUnitType: neighbor.unitType });
  }
  return out;
}

export function canConvertToAlbatrossMenu(
  snapshot: MatchSnapshot,
  u: UnitSnapshot,
  yourSeatIndex: number
): boolean {
  if (snapshot.matchFinished || snapshot.activePlayerIndex !== yourSeatIndex) return false;
  if (u.ownerSeatIndex !== yourSeatIndex || !u.health || u.hasMoved) return false;
  if (u.embarkedInTransportUnitId != null && u.embarkedInTransportUnitId !== "") return false;
  const st = getUnitTypeStats(u.unitType);
  if (!st || st.isTransport) return false;
  return st.movementKind === "FOOT";
}

export function canConvertToLeviathanMenu(
  snapshot: MatchSnapshot,
  u: UnitSnapshot,
  yourSeatIndex: number
): boolean {
  if (snapshot.matchFinished || snapshot.activePlayerIndex !== yourSeatIndex) return false;
  if (u.ownerSeatIndex !== yourSeatIndex || !u.health || u.hasMoved) return false;
  if (u.embarkedInTransportUnitId != null && u.embarkedInTransportUnitId !== "") return false;
  const st = getUnitTypeStats(u.unitType);
  if (!st || st.isTransport) return false;
  if (!(st.movementKind === "FOOT" || st.movementKind === "WHEELED" || st.movementKind === "TRACKED")) {
    return false;
  }
  const t = tileAt(snapshot, u.x, u.y);
  if (!t) return false;
  return isCoastalShoreForSeaFactory(t.terrain);
}

export function canRevertTransportMenu(
  snapshot: MatchSnapshot,
  u: UnitSnapshot,
  yourSeatIndex: number
): boolean {
  if (snapshot.matchFinished || snapshot.activePlayerIndex !== yourSeatIndex) return false;
  if (u.ownerSeatIndex !== yourSeatIndex || !u.health || u.hasMoved) return false;
  const st = getUnitTypeStats(u.unitType);
  if (!st?.isTransport) return false;
  const originType = u.originalLandUnitType;
  if (originType == null || originType === "") return false;
  const oStats = getUnitTypeStats(originType);
  if (!oStats) return false;
  const tile = tileAt(snapshot, u.x, u.y);
  if (!tile) return false;
  const mk = oStats.movementKind as MovementKind;
  return canTraverseKind(tile.terrain, mk);
}

export function contextMenuHasAnyAction(
  snapshot: MatchSnapshot,
  u: UnitSnapshot,
  yourSeatIndex: number
): boolean {
  return (
    canWarmachineFabricateMenu(snapshot, u, yourSeatIndex) ||
    canWarmachineDrillMenu(snapshot, u, yourSeatIndex) ||
    canFieldRepairMenu(snapshot, u, yourSeatIndex) ||
    canTransportDisembarkMenu(snapshot, u, yourSeatIndex) ||
    transportPickupMenuEntries(snapshot, u, yourSeatIndex).length > 0 ||
    canConvertToAlbatrossMenu(snapshot, u, yourSeatIndex) ||
    canConvertToLeviathanMenu(snapshot, u, yourSeatIndex) ||
    canRevertTransportMenu(snapshot, u, yourSeatIndex)
  );
}
