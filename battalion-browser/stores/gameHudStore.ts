import type { GridPoint } from "@/lib/protocol/types";
import { create } from "zustand";

export type ProductionModalState =
  | { kind: "factory"; x: number; y: number }
  | { kind: "warmachine"; unitId: string };

export interface GameContextMenuState {
  readonly clientX: number;
  readonly clientY: number;
  readonly unitId: string;
}

export interface GameHudState {
  /**
   * Every map click updates inspection (Swing always calls {@code selectionConsumer.accept(SelectionInfo)}).
   */
  inspectGrid: { readonly x: number; readonly y: number } | null;
  /** Swing {@code selectedCell}: yellow rectangle; cleared on bare ground clicks. */
  mapSelectedGrid: { readonly x: number; readonly y: number } | null;
  /** Planned move polyline drawn in gold — updated from hover while a mover is selected. */
  movementPath: GridPoint[];

  productionModal: ProductionModalState | null;
  contextMenu: GameContextMenuState | null;
  /**
   * When the local player issues a multi-tile move, reproduce that polyline during the glide animation.
   * Optional {@code chainAttackToward} triggers an attack-sheet animation facing that tile after the path completes (move-and-attack).
   */
  pendingMovePath: {
    readonly unitId: string;
    readonly path: GridPoint[];
    readonly chainAttackToward?: { readonly x: number; readonly y: number };
  } | null;
  pendingAttackVisual: { unitId: string; facing: string } | null;

  setInspectGrid: (g: { readonly x: number; readonly y: number }) => void;
  setMapSelectedGrid: (g: { readonly x: number; readonly y: number } | null) => void;
  setMovementPath: (p: GridPoint[]) => void;
  /** Clears map selection + planned path only (Swing {@code clearSelection} without losing last inspect until next click). */
  resetMapCommandOverlay: () => void;
  clearGameSelection: () => void;
  openProductionModal: (m: ProductionModalState) => void;
  closeProductionModal: () => void;
  openContextMenu: (m: GameContextMenuState) => void;
  closeContextMenu: () => void;
  setPendingMovePath: (p: GameHudState["pendingMovePath"]) => void;
  setPendingAttackVisual: (p: { unitId: string; facing: string } | null) => void;
}

export const useGameHudStore = create<GameHudState>((set) => ({
  inspectGrid: null,
  mapSelectedGrid: null,
  movementPath: [],
  productionModal: null,
  contextMenu: null,
  pendingMovePath: null,
  pendingAttackVisual: null,

  setInspectGrid: (g) =>
    set({
      inspectGrid: g,
    }),

  setMapSelectedGrid: (g) =>
    set({
      mapSelectedGrid: g,
    }),

  setMovementPath: (p) => set({ movementPath: p }),

  resetMapCommandOverlay: () =>
    set({
      mapSelectedGrid: null,
      movementPath: [],
    }),

  clearGameSelection: () =>
    set({
      inspectGrid: null,
      mapSelectedGrid: null,
      movementPath: [],
      productionModal: null,
      contextMenu: null,
      pendingMovePath: null,
      pendingAttackVisual: null,
    }),

  openProductionModal: (m) => set({ productionModal: m, contextMenu: null }),
  closeProductionModal: () => set({ productionModal: null }),
  openContextMenu: (m) => set({ contextMenu: m }),
  closeContextMenu: () => set({ contextMenu: null }),
  setPendingMovePath: (p) => set({ pendingMovePath: p }),
  setPendingAttackVisual: (p) => set({ pendingAttackVisual: p }),
}));
