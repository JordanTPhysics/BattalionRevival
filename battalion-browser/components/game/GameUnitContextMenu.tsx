"use client";

import { useEffect } from "react";
import { useGameHudStore } from "@/stores/gameHudStore";
import { useMatchStore } from "@/stores/matchStore";
import type { MatchSnapshot, UnitSnapshot } from "@/lib/protocol/types";

function canWarmachineFabricateMenu(snapshot: MatchSnapshot, u: UnitSnapshot, yourSeatIndex: number): boolean {
  if (snapshot.matchFinished || snapshot.activePlayerIndex !== yourSeatIndex) return false;
  return u.unitType === "Warmachine" && u.health > 0 && !u.hasMoved && u.ownerSeatIndex === yourSeatIndex;
}

/**
 * Swing parity: popup “Fabricate unit…” on movable friendly War Machine
 * ({@link com.game.ui.GameWindow.GameMapPanel#buildUnitContextMenu}).
 */
export function GameUnitContextMenu() {
  const contextMenu = useGameHudStore((s) => s.contextMenu);
  const closeContextMenu = useGameHudStore((s) => s.closeContextMenu);
  const openProductionModal = useGameHudStore((s) => s.openProductionModal);
  const snapshot = useMatchStore((s) => s.snapshot);
  const welcome = useMatchStore((s) => s.welcome);

  const u =
    contextMenu && snapshot ? snapshot.units.find((un) => un.id === contextMenu.unitId) : undefined;
  const menuValid =
    u != null &&
    welcome != null &&
    snapshot != null &&
    canWarmachineFabricateMenu(snapshot, u, welcome.yourSeatIndex);

  useEffect(() => {
    if (!contextMenu) return;
    if (!snapshot || !welcome) return;
    const unit = snapshot.units.find((un) => un.id === contextMenu.unitId);
    if (!unit || !canWarmachineFabricateMenu(snapshot, unit, welcome.yourSeatIndex)) {
      closeContextMenu();
    }
  }, [contextMenu, snapshot, welcome, closeContextMenu]);

  if (!contextMenu || !snapshot || !welcome || !menuValid || !u) return null;

  const left = Math.min(
    contextMenu.clientX,
    typeof window !== "undefined" ? window.innerWidth - 200 : contextMenu.clientX
  );
  const top = Math.min(
    contextMenu.clientY,
    typeof window !== "undefined" ? window.innerHeight - 56 : contextMenu.clientY
  );

  return (
    <>
      <div
        className="fixed inset-0 z-[75]"
        aria-hidden
        onPointerDown={(e) => {
          e.preventDefault();
          closeContextMenu();
        }}
      />
      <div
        data-game-context-menu
        className="fixed z-[76] min-w-[200px] rounded border bg-[#1A221F] py-1 text-[13px] shadow-lg"
        style={{ borderColor: "#2E3A35", left, top, color: "#E0E6E3" }}
        onPointerDown={(e) => e.stopPropagation()}
      >
        <button
          type="button"
          className="flex w-full px-4 py-2 text-left hover:bg-[#2A3530]"
          onClick={() => {
            openProductionModal({ kind: "warmachine", unitId: u.id });
            closeContextMenu();
          }}
        >
          Fabricate unit…
        </button>
      </div>
    </>
  );
}
