/**
 * Mirrors {@link com.game.model.map.TerrainType} mobility for pathfinding preview.
 */
import type { MovementKind } from "@/lib/game/unitTypeCatalog";

const INF = Number.MAX_SAFE_INTEGER;

function isSeaTerrain(name: string): boolean {
  return name.startsWith("SEA_");
}

function isReefTerrain(name: string): boolean {
  return name.startsWith("REEF_");
}

function isArchipelagoTerrain(name: string): boolean {
  return name.startsWith("ARCHIPELAGO_");
}

function isShoreTerrain(name: string): boolean {
  return name.startsWith("SHORE_");
}

function isCanyonTerrain(name: string): boolean {
  return name.includes("CANYON");
}

function isHillTerrain(name: string): boolean {
  return name === "HILLS_1" || name === "HILLS_2";
}

export function terrainGrantsRangedHillRangeBonus(terrain: string): boolean {
  return isHillTerrain(terrain);
}

/** {@link com.game.model.map.TerrainType#canTraverse(Unit)} without full Unit — uses kind + flags. */
export function canTraverseTerrain(
  terrain: string,
  kind: MovementKind,
  options: { massiveHull: boolean }
): boolean {
  if (kind === "AIR") {
    return true;
  }
  if (options.massiveHull && isShoreTerrain(terrain)) {
    return false;
  }
  return canTraverseKind(terrain, kind);
}

export function canTraverseKind(terrain: string, kind: MovementKind): boolean {
  switch (kind) {
    case "AIR":
      return true;
    case "NAVAL": {
      /** Crosses land tiles like Swing / Java HIGH_BRIDGE_MOVEMENT (+ non-naval bridges are blocked separately). */
      if (terrain.startsWith("HIGH_BRIDGE_")) {
        return true;
      }
      if (isSeaTerrain(terrain) || isReefTerrain(terrain) || isArchipelagoTerrain(terrain)) {
        return true;
      }
      return terrain.startsWith("SHORE_");
    }
    case "FOOT": {
      if (terrain === "MOUNTAINS_1" || terrain === "VOLCANO" || terrain.startsWith("ROCK_FORMATION_")) {
        return true;
      }
      if (isSeaTerrain(terrain) || isReefTerrain(terrain)) {
        return false;
      }
      if (isArchipelagoTerrain(terrain)) {
        return false;
      }
      return true;
    }
    case "WHEELED":
    case "TRACKED": {
      if (terrain === "MOUNTAINS_1" || terrain === "VOLCANO" || terrain.startsWith("ROCK_FORMATION_")) {
        return false;
      }
      if (isSeaTerrain(terrain) || isReefTerrain(terrain)) {
        return false;
      }
      if (isArchipelagoTerrain(terrain)) {
        return false;
      }
      return true;
    }
    default:
      return false;
  }
}

export function movementCostForKind(terrain: string, kind: MovementKind): number {
  if (kind === "AIR") {
    return 1;
  }
  switch (kind) {
    case "NAVAL": {
      if (!canTraverseKind(terrain, kind)) {
        return INF;
      }
      if (isReefTerrain(terrain) || isArchipelagoTerrain(terrain)) {
        return 2;
      }
      return 1;
    }
    case "FOOT": {
      if (terrain === "MOUNTAINS_1" || terrain === "VOLCANO" || terrain.startsWith("ROCK_FORMATION_")) {
        return 3;
      }
      return landCost(terrain, kind);
    }
    case "WHEELED":
    case "TRACKED":
      return landCost(terrain, kind);
    default:
      return INF;
  }
}

function landCost(terrain: string, kind: MovementKind): number {
  const forestHills =
    terrain.startsWith("FOREST_") || terrain === "HILLS_1" || terrain === "HILLS_2";
  if (forestHills) {
    return kind === "WHEELED" || kind === "TRACKED" ? 2 : 1;
  }
  return 1;
}

export { isCanyonTerrain, isHillTerrain };
