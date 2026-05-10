import { create } from "zustand";
import {
  BASE_TILE_SIZE,
  MAX_GRID,
  MAX_TEAMS,
  MAX_TILE_SIZE,
  MIN_GRID,
  MIN_TEAMS,
  MIN_TILE_SIZE,
  TERRAIN_TYPES,
  TILE_SIZE_STEP,
} from "@/lib/editor/catalog";
import { createBlankMap, resizeSnapshot } from "@/lib/maps/mapJson";
import type { EditorMapSnapshot, EditorTile } from "@/lib/maps/editorTypes";

function cloneDeepSnapshot(s: EditorMapSnapshot): EditorMapSnapshot {
  return {
    width: s.width,
    height: s.height,
    teamCount: s.teamCount,
    tiles: s.tiles.map((row) => row.map((t) => ({ ...t }))),
  };
}

/** Mirrors Swing {@link com.game.ui.MapBuilderPanel#brushTeamId} clamp rules. */
function clampBrushTeamId(n: number): number {
  return Math.max(0, Math.min(4, Math.floor(n)));
}

export interface EditorStore {
  map: EditorMapSnapshot;
  revision: number;

  selectedTerrain: string;
  selectedStructure: string | null;
  selectedUnitSprite: string | null;
  brushTeamId: number;
  oreDepositBrushMode: boolean;

  tileSize: number;
  pan: { x: number; y: number };

  selectedCell: { x: number; y: number } | null;
  statusLine: string;
  mapName: string;

  bump: () => void;
  setStatus: (s: string) => void;
  setMapName: (n: string) => void;

  setSelectedTerrain: (t: string) => void;
  setSelectedStructure: (st: string | null) => void;
  setSelectedUnitSprite: (id: string | null) => void;
  setBrushTeamId: (id: number) => void;
  setOreDepositBrushMode: (v: boolean) => void;

  applyGridSize: (w: number, h: number) => void;
  setTeamCount: (n: number) => void;
  zoomIn: () => void;
  zoomOut: () => void;
  getZoomPercent: () => number;
  panBy: (dx: number, dy: number) => void;

  replaceMapFromSnapshot: (s: EditorMapSnapshot) => void;

  resetTerrainComboToDefault: () => void;

  fillTerrainEverywhere: (terrain: string) => void;
  clearAllStructures: () => void;
  clearAllUnits: () => void;
  resetToPlainsDestructive: () => void;

  applyBrushAt: (gx: number, gy: number, kind: "erase" | "ore_toggle" | "paint") => void;
}

export const useEditorStore = create<EditorStore>((set, get) => ({
  map: createBlankMap(20, 20, MIN_TEAMS),
  revision: 0,

  selectedTerrain: "PLAINS_1",
  selectedStructure: null,
  selectedUnitSprite: null,
  brushTeamId: 1,
  oreDepositBrushMode: false,

  tileSize: BASE_TILE_SIZE,
  pan: { x: 16, y: 16 },

  selectedCell: null,
  statusLine:
    "Ready — arrow keys scroll the view; mouse wheel zooms; hold Space + drag to pan. Shift-click or right-click clears a tile.",
  mapName: "",

  bump: () => set((s) => ({ revision: s.revision + 1 })),
  setStatus: (statusLine) => set({ statusLine }),
  setMapName: (mapName) => set({ mapName }),

  setSelectedTerrain: (selectedTerrain) => set({ selectedTerrain }),
  setSelectedStructure: (selectedStructure) => set({ selectedStructure }),
  setSelectedUnitSprite: (selectedUnitSprite) => set({ selectedUnitSprite }),
  setBrushTeamId: (brushTeamId) => set({ brushTeamId: clampBrushTeamId(brushTeamId) }),
  setOreDepositBrushMode: (oreDepositBrushMode) => set({ oreDepositBrushMode }),

  replaceMapFromSnapshot: (incoming) =>
    set((s) => ({
      map: cloneDeepSnapshot(incoming),
      revision: s.revision + 1,
      brushTeamId: clampBrushTeamId(Math.min(get().brushTeamId, incoming.teamCount)),
    })),

  resetTerrainComboToDefault: () => set({ selectedTerrain: "PLAINS_1" }),

  applyGridSize: (w, h) => {
    if (!Number.isInteger(w) || !Number.isInteger(h)) return;
    if (w < MIN_GRID || w > MAX_GRID || h < MIN_GRID || h > MAX_GRID) {
      get().setStatus(`Invalid size (${MIN_GRID}-${MAX_GRID}).`);
      return;
    }
    set((state) => {
      const resized = resizeSnapshot(state.map, w, h);
      return {
        map: resized,
        revision: state.revision + 1,
        statusLine: `Map size set to ${w} × ${h}`,
      };
    });
  },

  setTeamCount: (n) => {
    let teamCount = Number(n);
    if (!Number.isInteger(teamCount)) return;
    if (teamCount < MIN_TEAMS) teamCount = MIN_TEAMS;
    if (teamCount > MAX_TEAMS) teamCount = MAX_TEAMS;
    set((s) => ({
      map: { ...s.map, teamCount },
      brushTeamId: Math.min(clampBrushTeamId(s.brushTeamId), teamCount),
      revision: s.revision + 1,
      statusLine: `Map team count: ${teamCount}`,
    }));
  },

  zoomIn: () => {
    set((s) => {
      const next = Math.min(MAX_TILE_SIZE, s.tileSize + TILE_SIZE_STEP);
      if (next === s.tileSize) return {};
      return {
        tileSize: next,
        revision: s.revision + 1,
        statusLine: `Map zoom: ${Math.round(next * (100 / BASE_TILE_SIZE))}%`,
      };
    });
  },

  zoomOut: () => {
    set((s) => {
      const next = Math.max(MIN_TILE_SIZE, s.tileSize - TILE_SIZE_STEP);
      if (next === s.tileSize) return {};
      return {
        tileSize: next,
        revision: s.revision + 1,
        statusLine: `Map zoom: ${Math.round(next * (100 / BASE_TILE_SIZE))}%`,
      };
    });
  },

  getZoomPercent: () => Math.round(get().tileSize * (100 / BASE_TILE_SIZE)),

  panBy: (dx, dy) =>
    set((s) => ({
      pan: { x: s.pan.x + dx, y: s.pan.y + dy },
    })),

  fillTerrainEverywhere: (terrain) => {
    if (!TERRAIN_TYPES.includes(terrain as never)) return;
    set((s) => {
      const map = cloneDeepSnapshot(s.map);
      for (let y = 0; y < map.height; y++) {
        for (let x = 0; x < map.width; x++) {
          map.tiles[y][x].terrain = terrain;
        }
      }
      return {
        map,
        revision: s.revision + 1,
        statusLine: `Filled map with ${terrain}`,
      };
    });
  },

  clearAllStructures: () => {
    set((s) => {
      const map = cloneDeepSnapshot(s.map);
      for (let y = 0; y < map.height; y++) {
        for (let x = 0; x < map.width; x++) {
          map.tiles[y][x].structure = null;
          map.tiles[y][x].structureTeam = null;
        }
      }
      return {
        map,
        revision: s.revision + 1,
        statusLine: "Cleared all structures",
      };
    });
  },

  clearAllUnits: () => {
    set((s) => {
      const map = cloneDeepSnapshot(s.map);
      for (let y = 0; y < map.height; y++) {
        for (let x = 0; x < map.width; x++) {
          map.tiles[y][x].unitSprite = null;
          map.tiles[y][x].unitTeam = null;
          map.tiles[y][x].unitFacing = "EAST";
        }
      }
      return {
        map,
        revision: s.revision + 1,
        statusLine: "Cleared all units",
      };
    });
  },

  resetToPlainsDestructive: () => {
    set((s) => {
      const map = cloneDeepSnapshot(s.map);
      for (let y = 0; y < map.height; y++) {
        for (let x = 0; x < map.width; x++) {
          const t = map.tiles[y][x];
          t.terrain = "PLAINS_1";
          t.structure = null;
          t.structureTeam = null;
          t.unitSprite = null;
          t.unitTeam = null;
          t.unitFacing = "EAST";
          t.oreDeposit = false;
        }
      }
      return {
        map,
        revision: s.revision + 1,
        statusLine: "Reset all tiles to PLAINS",
      };
    });
  },

  applyBrushAt: (gx, gy, kind) => {
    const state = get();
    if (gx < 0 || gy < 0 || gx >= state.map.width || gy >= state.map.height) return;

    if (kind === "erase") {
      set((s) => {
        const map = cloneDeepSnapshot(s.map);
        const t = map.tiles[gy][gx];
        t.structure = null;
        t.structureTeam = null;
        t.unitSprite = null;
        t.unitTeam = null;
        t.unitFacing = "EAST";
        t.oreDeposit = false;
        return {
          map,
          selectedCell: { x: gx, y: gy },
          revision: s.revision + 1,
          statusLine: `Removed structure and unit at ${gx},${gy}`,
        };
      });
      return;
    }

    if (kind === "ore_toggle") {
      set((s) => {
        const map = cloneDeepSnapshot(s.map);
        const t = map.tiles[gy][gx];
        t.oreDeposit = !t.oreDeposit;
        const on = t.oreDeposit;
        return {
          map,
          selectedCell: { x: gx, y: gy },
          revision: s.revision + 1,
          statusLine: (on ? "Marked ore deposit at " : "Cleared ore at ") + `${gx},${gy}`,
        };
      });
      return;
    }

    const terrain = state.selectedTerrain;
    const brushTeamId = state.brushTeamId;
    const { map } = state;
    const paintUnitTeam =
      brushTeamId <= 0 ? 1 : Math.min(brushTeamId, map.teamCount);

    const selStructure = state.selectedStructure;
    const blockedCapital =
      selStructure != null && brushTeamId === 0 && selStructure === "Capital";

    set((s) => {
      const map2 = cloneDeepSnapshot(s.map);
      const tile: EditorTile = map2.tiles[gy][gx];

      tile.terrain = terrain;

      if (selStructure != null) {
        if (blockedCapital) {
          /* Swing rejects Capital + Neutral faction for structure layer only */
        } else {
          tile.structure = selStructure;
          if (brushTeamId === 0) {
            tile.structureTeam = null;
          } else {
            tile.structureTeam = Math.min(brushTeamId, map2.teamCount);
          }
        }
      } else {
        tile.structure = null;
        tile.structureTeam = null;
      }

      if (state.selectedUnitSprite != null) {
        tile.unitSprite = state.selectedUnitSprite;
        tile.unitTeam = paintUnitTeam;
        tile.unitFacing = "EAST";
      } else {
        tile.unitSprite = null;
        tile.unitTeam = null;
        tile.unitFacing = "EAST";
      }

      return {
        map: map2,
        selectedCell: { x: gx, y: gy },
        revision: s.revision + 1,
        statusLine: blockedCapital
          ? "Capital must be assigned to a faction; choose a faction (not Neutral) in Brush Faction."
          : `Painted tile at ${gx},${gy}`,
      };
    });
  },
}));
