/** Mirrors {@link com.game.model.map.TerrainType} defense modifiers (second constructor arg). */
import { movementCostForKind } from "@/lib/game/terrainMovement";

const TERRAIN_DEFENSE_MODIFIER: Record<string, number> = {
  ARCHIPELAGO_2: 0.15,
  BRIDGE_DOWN: 0.4,
  BRIDGE_HORIZONTAL: 0.4,
  BRIDGE_LEFT: 0.4,
  BRIDGE_LEFTRIGHT: 0.4,
  BRIDGE_RIGHT: 0.4,
  BRIDGE_UP: 0.4,
  BRIDGE_UPDOWN: 0.4,
  BRIDGE_VERTICAL: 0.4,
  CANYON_DOUBLE_LEFTDOWN: 0.3,
  CANYON_DOUBLE_LEFTDOWN_ONE_RIGHTUP: 0.3,
  CANYON_DOUBLE_LEFTRIGHT: 0.3,
  CANYON_DOUBLE_LEFTUP: 0.3,
  CANYON_DOUBLE_LEFTUP_ONE_RIGHTDOWN: 0.3,
  CANYON_DOUBLE_RIGHTDOWN: 0.3,
  CANYON_DOUBLE_RIGHTDOWN_ONE_LEFTUP: 0.3,
  CANYON_DOUBLE_RIGHTUP: 0.3,
  CANYON_DOUBLE_RIGHTUP_ONE_LEFTDOWN: 0.3,
  CANYON_DOUBLE_UPDOWN: 0.3,
  CANYON_MAIN: 0.3,
  CANYON_ONE_LEFTDOWN: 0.3,
  CANYON_ONE_LEFTDOWN_ONE_RIGHTDOWN: 0.3,
  CANYON_ONE_LEFTDOWN_ONE_RIGHTUP: 0.3,
  CANYON_ONE_LEFTDOWN_ONE_RIGHTUP_ONE_RIGHTDOWN: 0.3,
  CANYON_ONE_LEFTUP: 0.3,
  CANYON_ONE_LEFTUP_ONE_LEFTDOWN: 0.3,
  CANYON_ONE_LEFTUP_ONE_LEFTDOWN_ONE_RIGHTDOWN: 0.3,
  CANYON_ONE_LEFTUP_ONE_LEFTDOWN_ONE_RIGHTUP: 0.3,
  FOREST_1: 0.2,
  FOREST_2: 0.2,
  HIGH_BRIDGE_DOWN: 0.45,
  HIGH_BRIDGE_HORIZONTAL: 0.45,
  HILLS_1: 0.2,
  HILLS_2: 0.2,
  MOUNTAINS_1: 0.35,
  PLAINS_1: 0,
  REEF_1: 0,
  ROCK_FORMATION_1: 0.35,
  SEA_MAIN: 0,
  SHORE_DOUBLE_LEFTDOWN: 0,
  SHORE_DOUBLE_LEFTUP: 0,
  SHORE_DOUBLE_RIGHTDOWN: 0,
  SHORE_DOUBLE_RIGHTUP: 0,
  SHORE_SINGLE_DOWN: 0,
  SHORE_SINGLE_LEFT: 0,
  SHORE_TRIPLE_DOWN: 0,
  SHORE_TRIPLE_LEFT: 0,
  SHORE_TRIPLE_RIGHT: 0,
  SHORE_TRIPLE_UP: 0,
};

/** Defense bonus percent shown in Swing GameInfoPanel. */
export function terrainDefensePercent(terrainEnumName: string): number {
  return Math.round((TERRAIN_DEFENSE_MODIFIER[terrainEnumName] ?? 0) * 100);
}

const LARGE_COST = Math.floor(Number.MAX_SAFE_INTEGER / 8);

/** Swing GameInfoPanel "Move cost (foot)" caption. */
export function footMoveCostHudNote(terrainEnumName: string): string {
  const c = movementCostForKind(terrainEnumName, "FOOT");
  if (!Number.isFinite(c) || c >= LARGE_COST / 4) {
    return "Impassable (foot)";
  }
  return `Move cost (foot) ${c}`;
}
