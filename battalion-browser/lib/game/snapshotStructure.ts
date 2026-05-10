import type { MatchSnapshot, UnitSnapshot } from "@/lib/protocol/types";
import { getUnitTypeStats, isWarmachineProducibleType } from "@/lib/game/unitTypeCatalog";

export function structureTeamIdFromSeat(seatIndex: number): number {
  return seatIndex + 1;
}

export function playerMoney(snapshot: MatchSnapshot, seatIndex: number): number {
  return snapshot.players.find((p) => p.seatIndex === seatIndex)?.money ?? 0;
}

export function playerOwnsStructureType(snapshot: MatchSnapshot, seatIndex: number, type: string): boolean {
  const tid = structureTeamIdFromSeat(seatIndex);
  const h = snapshot.height;
  const w = snapshot.width;
  for (let y = 0; y < h; y++) {
    const row = snapshot.tiles[y];
    if (!row) continue;
    for (let x = 0; x < w; x++) {
      const t = row[x];
      if (!t) continue;
      if (t.structure === type && t.structureTeam === tid) {
        return true;
      }
    }
  }
  return false;
}

export function isCoastalFactory(snapshot: MatchSnapshot, factoryX: number, factoryY: number): boolean {
  const t = snapshot.tiles[factoryY]?.[factoryX]?.terrain;
  return typeof t === "string" && t.startsWith("SHORE_");
}

/** Gates for factory category grids — mirrors Swing {@link com.game.ui.FactoryBuildDialog}. */
export function factoryCategoryAvailability(
  snapshot: MatchSnapshot,
  seatIndex: number,
  factoryX: number,
  factoryY: number
): { land: boolean; sea: boolean; air: boolean } {
  return {
    land: playerOwnsStructureType(snapshot, seatIndex, "GroundControl"),
    sea: playerOwnsStructureType(snapshot, seatIndex, "SeaControl") && isCoastalFactory(snapshot, factoryX, factoryY),
    air: playerOwnsStructureType(snapshot, seatIndex, "AirControl"),
  };
}

/**
 * Mirrors the main gates in {@link com.game.engine.PlayableGameSession#canPlayerBuildUnitAtFactory}
 * excluding spawn-field search — server remains authoritative on placement.
 */
export function factoryBuildEligiblePreview(
  snapshot: MatchSnapshot,
  seatIndex: number,
  factoryX: number,
  factoryY: number,
  unitType: string
): boolean {
  if (snapshot.matchFinished || snapshot.activePlayerIndex !== seatIndex) {
    return false;
  }
  const tid = structureTeamIdFromSeat(seatIndex);
  const tile = snapshot.tiles[factoryY]?.[factoryX];
  if (!tile || tile.structure !== "Factory" || tile.structureTeam !== tid) {
    return false;
  }
  if (snapshot.units.some((u) => u.x === factoryX && u.y === factoryY)) {
    return false;
  }
  const st = getUnitTypeStats(unitType);
  if (!st || st.isTransport || unitType === "Warmachine" || st.movementSpeed === 0) {
    return false;
  }
  switch (st.factoryBuildCategory) {
    case "AIR":
      return playerOwnsStructureType(snapshot, seatIndex, "AirControl");
    case "SEA":
      return (
        playerOwnsStructureType(snapshot, seatIndex, "SeaControl") &&
        isCoastalFactory(snapshot, factoryX, factoryY)
      );
    case "LAND":
      return playerOwnsStructureType(snapshot, seatIndex, "GroundControl");
    default:
      return false;
  }
}

export function warmachineBuildEligiblePreview(
  wm: UnitSnapshot,
  snapshot: MatchSnapshot,
  seatIndex: number,
  unitType: string,
  factoryPrice: number
): boolean {
  if (snapshot.matchFinished || snapshot.activePlayerIndex !== seatIndex || wm.unitType !== "Warmachine") {
    return false;
  }
  if (wm.ownerSeatIndex !== seatIndex || wm.hasMoved) {
    return false;
  }
  const purse = wm.warmachineFunds;
  if (purse === null || purse < factoryPrice) {
    return false;
  }
  return isWarmachineProducibleType(unitType);
}
