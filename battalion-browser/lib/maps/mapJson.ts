import {
  MAX_GRID,
  MIN_GRID,
  MAX_TEAMS,
  MIN_TEAMS,
  STRUCTURE_TYPES,
  TERRAIN_TYPES,
} from "@/lib/editor/catalog";
import type { EditorMapSnapshot, EditorTile } from "@/lib/maps/editorTypes";

const TERRAIN_SET = new Set<string>(TERRAIN_TYPES as unknown as string[]);
const STRUCTURE_SET = new Set<string>(STRUCTURE_TYPES as unknown as string[]);

function escapeJsonSegment(s: string): string {
  return s.replaceAll("\\", "\\\\").replaceAll('"', '\\"');
}

/** Mirrors {@link com.game.persistence.MapJsonPersistence#serialize}. */
export function serializeMapJson(snapshot: EditorMapSnapshot): string {
  const lines: string[] = [];
  lines.push("{");
  lines.push(`  "width": ${snapshot.width},`);
  lines.push(`  "height": ${snapshot.height},`);
  lines.push(`  "teamCount": ${snapshot.teamCount},`);
  lines.push("  \"tiles\": [");

  for (let y = 0; y < snapshot.height; y++) {
    const row = snapshot.tiles[y];
    lines.push("    [");
    for (let x = 0; x < snapshot.width; x++) {
      const tile = row[x];
      const struct =
        tile.structure == null ? "null" : `"${tile.structure.replaceAll('"', '\\"')}"`;
      const structureTeam =
        tile.structureTeam == null ? "null" : `${tile.structureTeam}`;
      const unitSprite =
        tile.unitSprite == null ? "null" : `"${escapeJsonSegment(tile.unitSprite)}"`;
      const unitTeam = tile.unitTeam == null ? "null" : `${tile.unitTeam}`;
      const unitFacing = `"${tile.unitFacing}"`;
      lines.push(
        `      {"terrain":"${tile.terrain}","structure":${struct},"structureTeam":${structureTeam},` +
          `"unitSprite":${unitSprite},"unitTeam":${unitTeam},"unitFacing":${unitFacing},` +
          `"oreDeposit":${tile.oreDeposit}}${x < snapshot.width - 1 ? "," : ""}`
      );
    }
    lines.push(`    ]${y < snapshot.height - 1 ? "," : ""}`);
  }

  lines.push("  ]");
  lines.push("}\n");
  return lines.join("\n");
}

interface RawTile {
  terrain?: string;
  structure?: string | null;
  structureTeam?: number | null;
  unitSprite?: string | null;
  unitTeam?: number | null;
  unitFacing?: string | null;
  oreDeposit?: boolean;
}

interface RawDoc {
  width?: unknown;
  height?: unknown;
  teamCount?: unknown;
  tiles?: unknown;
}

/** Parses map JSON ({@link com.game.persistence.MapJsonPersistence#parse} semantics via JSON.parse). */
export function parseMapJson(json: string): EditorMapSnapshot {
  let raw: RawDoc;
  try {
    raw = JSON.parse(json) as RawDoc;
  } catch {
    throw new Error("Invalid JSON");
  }

  const width = Number(raw.width);
  const height = Number(raw.height);
  const teamCount =
    raw.teamCount !== undefined ? Number(raw.teamCount) : MIN_TEAMS;

  if (!Number.isFinite(width) || !Number.isFinite(height)) {
    throw new Error("Missing or invalid width/height");
  }
  if (!Number.isInteger(width) || !Number.isInteger(height)) {
    throw new Error("Width and height must be integers");
  }
  if (width < MIN_GRID || width > MAX_GRID || height < MIN_GRID || height > MAX_GRID) {
    throw new Error(`Map width/height must be between ${MIN_GRID} and ${MAX_GRID}`);
  }
  if (!Number.isInteger(teamCount) || teamCount < MIN_TEAMS || teamCount > MAX_TEAMS) {
    throw new Error(`teamCount must be between ${MIN_TEAMS} and ${MAX_TEAMS}`);
  }

  if (!Array.isArray(raw.tiles)) {
    throw new Error("tiles must be an array");
  }
  if (raw.tiles.length !== height) {
    throw new Error(`Invalid map JSON: expected ${height} tile rows`);
  }

  const tiles: EditorTile[][] = [];
  for (let y = 0; y < height; y++) {
    const row = raw.tiles[y];
    if (!Array.isArray(row)) {
      throw new Error(`Row ${y} invalid`);
    }
    if (row.length !== width) {
      throw new Error(`Invalid map JSON: row ${y} has length ${row.length}, expected ${width}`);
    }

    tiles[y] = [];
    for (let x = 0; x < width; x++) {
      tiles[y][x] = normalizeTile(rawTile(row[x]), teamCount as number);
    }
  }

  return { width, height, teamCount: teamCount as number, tiles };
}

function rawTile(cell: unknown): RawTile {
  if (typeof cell !== "object" || cell === null) {
    throw new Error("Tile entry must be an object");
  }
  return cell as RawTile;
}

function normalizeTeamId(v: unknown, teamCount: number): number | null {
  if (v === null || v === undefined || v === "") {
    return null;
  }
  const n = Number(v);
  if (!Number.isInteger(n)) {
    throw new Error("structureTeam/unitTeam must be integers or null");
  }
  if (n < 1 || n > teamCount) {
    throw new Error(`Team id out of range for this map: ${n}`);
  }
  return n;
}

function normalizeTile(raw: RawTile, teamCount: number): EditorTile {
  const terrain =
    typeof raw.terrain === "string" ? raw.terrain : (() => {
      throw new Error("terrain must be a string");
    })();
  if (!TERRAIN_SET.has(terrain)) {
    throw new Error(`Unknown terrain: ${terrain}`);
  }

  let structure: string | null = null;
  if (raw.structure !== undefined && raw.structure !== null) {
    if (typeof raw.structure !== "string") throw new Error("structure must be string or null");
    if (!STRUCTURE_SET.has(raw.structure)) {
      throw new Error(`Unknown structure: ${raw.structure}`);
    }
    structure = raw.structure;
  }

  let unitSprite: string | null = null;
  if (raw.unitSprite !== undefined && raw.unitSprite !== null) {
    if (typeof raw.unitSprite !== "string") throw new Error("unitSprite must be string or null");
    unitSprite = raw.unitSprite;
  }

  const structureTeam =
    raw.structureTeam === undefined || raw.structureTeam === null || raw.structure === null
      ? null
      : normalizeTeamId(raw.structureTeam, teamCount);

  const unitTeam =
    raw.unitTeam === undefined || raw.unitTeam === null ? null : normalizeTeamId(raw.unitTeam, teamCount);

  const unitFacing =
    typeof raw.unitFacing === "string" && raw.unitFacing.length > 0 ? raw.unitFacing : "EAST";

  const oreDeposit = Boolean(raw.oreDeposit);

  return {
    terrain,
    structure,
    structureTeam,
    unitSprite,
    unitTeam,
    unitFacing,
    oreDeposit,
  };
}

/** Creates a new plains map with given dimensions ({@link com.game.server.DemoMaps#plains20} semantics). */
export function createBlankMap(width: number, height: number, teamCount = MIN_TEAMS): EditorMapSnapshot {
  const row = (): EditorTile[] =>
    Array.from({ length: width }, (): EditorTile => ({
      terrain: "PLAINS_1",
      structure: null,
      structureTeam: null,
      unitSprite: null,
      unitTeam: null,
      unitFacing: "EAST",
      oreDeposit: false,
    }));

  const tiles: EditorTile[][] = [];
  for (let y = 0; y < height; y++) {
    tiles.push(row());
  }
  return {
    width,
    height,
    teamCount,
    tiles,
  };
}

/** Mirrors {@link com.game.model.map.GameMap#resize}: preserve overlapping cells, PLAINS elsewhere. */
export function resizeSnapshot(snapshot: EditorMapSnapshot, newWidth: number, newHeight: number): EditorMapSnapshot {
  if (newWidth < MIN_GRID || newWidth > MAX_GRID || newHeight < MIN_GRID || newHeight > MAX_GRID) {
    throw new Error(`Grid size must be between ${MIN_GRID} and ${MAX_GRID}`);
  }

  const nextTiles: EditorTile[][] = [];
  for (let y = 0; y < newHeight; y++) {
    const row: EditorTile[] = [];
    for (let x = 0; x < newWidth; x++) {
      if (x < snapshot.width && y < snapshot.height && snapshot.tiles[y][x]) {
        row.push({ ...snapshot.tiles[y][x] });
      } else {
        row.push({
          terrain: "PLAINS_1",
          structure: null,
          structureTeam: null,
          unitSprite: null,
          unitTeam: null,
          unitFacing: "EAST",
          oreDeposit: false,
        });
      }
    }
    nextTiles.push(row);
  }

  return { ...snapshot, width: newWidth, height: newHeight, tiles: nextTiles };
}
