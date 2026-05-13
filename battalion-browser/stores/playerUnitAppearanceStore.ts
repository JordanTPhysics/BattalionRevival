import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { TeamPaintStyle } from "@/lib/game/teamPaintStyle";
import { DEFAULT_PLAYER_UNIT_RGB } from "@/lib/game/teamPaintStyle";

type PlayerUnitAppearanceState = {
  paintStyle: TeamPaintStyle;
  setPaintStyle: (style: TeamPaintStyle) => void;
};

export const usePlayerUnitAppearanceStore = create<PlayerUnitAppearanceState>()(
  persist(
    (set) => ({
      paintStyle: { kind: "solid", rgb: DEFAULT_PLAYER_UNIT_RGB },
      setPaintStyle: (paintStyle) => set({ paintStyle }),
    }),
    { name: "battalion-player-unit-appearance" }
  )
);
