/** Mirrors {@link com.game.model.map.GameMap}. */
export const MIN_GRID = 10;
export const MAX_GRID = 40;
export const MIN_TEAMS = 2;
export const MAX_TEAMS = 4;

/** Mirrors {@link com.game.ui.MapBuilderPanel#EDGE_PADDING_TILES}. */
export const EDGE_PADDING_TILES = 10;

/** Mirrors {@link com.game.ui.MapBuilderPanel} tile sizing. */
export const BASE_TILE_SIZE = 32;
export const MIN_TILE_SIZE = 16;
export const MAX_TILE_SIZE = 96;
export const TILE_SIZE_STEP = 4;

/** Terrain enum names ({@link com.game.model.map.TerrainType}), Java wire order preserved. */
export const TERRAIN_TYPES: readonly string[] = [
  "ARCHIPELAGO_2",
  "BRIDGE_DOWN",
  "BRIDGE_HORIZONTAL",
  "BRIDGE_LEFT",
  "BRIDGE_LEFTRIGHT",
  "BRIDGE_RIGHT",
  "BRIDGE_UP",
  "BRIDGE_UPDOWN",
  "BRIDGE_VERTICAL",
  "CANYON_DOUBLE_LEFTDOWN",
  "CANYON_DOUBLE_LEFTDOWN_ONE_RIGHTUP",
  "CANYON_DOUBLE_LEFTRIGHT",
  "CANYON_DOUBLE_LEFTUP",
  "CANYON_DOUBLE_LEFTUP_ONE_RIGHTDOWN",
  "CANYON_DOUBLE_RIGHTDOWN",
  "CANYON_DOUBLE_RIGHTDOWN_ONE_LEFTUP",
  "CANYON_DOUBLE_RIGHTUP",
  "CANYON_DOUBLE_RIGHTUP_ONE_LEFTDOWN",
  "CANYON_DOUBLE_UPDOWN",
  "CANYON_MAIN",
  "CANYON_ONE_LEFTDOWN",
  "CANYON_ONE_LEFTDOWN_ONE_RIGHTDOWN",
  "CANYON_ONE_LEFTDOWN_ONE_RIGHTUP",
  "CANYON_ONE_LEFTDOWN_ONE_RIGHTUP_ONE_RIGHTDOWN",
  "CANYON_ONE_LEFTUP",
  "CANYON_ONE_LEFTUP_ONE_LEFTDOWN",
  "CANYON_ONE_LEFTUP_ONE_LEFTDOWN_ONE_RIGHTDOWN",
  "CANYON_ONE_LEFTUP_ONE_LEFTDOWN_ONE_RIGHTUP",
  "FOREST_1",
  "FOREST_2",
  "HIGH_BRIDGE_DOWN",
  "HIGH_BRIDGE_HORIZONTAL",
  "HILLS_1",
  "HILLS_2",
  "MOUNTAINS_1",
  "PLAINS_1",
  "REEF_1",
  "ROCK_FORMATION_1",
  "SEA_MAIN",
  "SHORE_DOUBLE_LEFTDOWN",
  "SHORE_DOUBLE_LEFTUP",
  "SHORE_DOUBLE_RIGHTDOWN",
  "SHORE_DOUBLE_RIGHTUP",
  "SHORE_SINGLE_DOWN",
  "SHORE_SINGLE_LEFT",
  "SHORE_TRIPLE_DOWN",
  "SHORE_TRIPLE_LEFT",
  "SHORE_TRIPLE_RIGHT",
  "SHORE_TRIPLE_UP",
] as const;

/** {@link com.game.model.structures.StructureType} PascalCase JSON names. */
export const STRUCTURE_TYPES: readonly string[] = [
  "AirControl",
  "Capital",
  "SeaControl",
  "GroundControl",
  "OilAdvanced",
  "OilRefinery",
  "OilRig",
  "Factory",
] as const;

/**
 * {@link com.game.ui.AssetManager} registration order: UnitType enum then extras.
 * Web assets only ship a subset — list matches typical desktop classpath.
 */
export const UNIT_SPRITE_IDS: readonly string[] = [
  "Albatross",
  "Annihilator",
  "Battlecruiser",
  "Blockade",
  "Commando",
  "Condor",
  "Corvette",
  "Flak",
  "Hunter",
  "Hcommando",
  "Intrepid",
  "Jammer",
  "Lancer",
  "Leviathan",
  "Mortar",
  "Raptor",
  "Rocket",
  "Scorpion",
  "Spider",
  "Stealth",
  "Turret",
  "Uboat",
  "Vulture",
  "Warmachine",
].sort();
