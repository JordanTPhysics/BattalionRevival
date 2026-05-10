import { create } from "zustand";

export type AppPanel =
  | "home"
  | "auth"
  | "matchmaking"
  | "game"
  | "replay"
  | "levels"
  | "editor"
  | "settings";

export interface UiState {
  /** Active high-level area for shell highlighting (routes own primary nav). */
  activePanel: AppPanel;
  sidebarOpen: boolean;
  setActivePanel: (p: AppPanel) => void;
  toggleSidebar: () => void;
}

export const useUiStore = create<UiState>((set) => ({
  activePanel: "home",
  sidebarOpen: true,
  setActivePanel: (activePanel) => set({ activePanel }),
  toggleSidebar: () => set((s) => ({ sidebarOpen: !s.sidebarOpen })),
}));
