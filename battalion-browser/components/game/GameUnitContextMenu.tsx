"use client";

import { useEffect } from "react";
import { useGameHudStore } from "@/stores/gameHudStore";
import { useMatchStore, getMatchWebSocketClient } from "@/stores/matchStore";
import {
  WARMACHINE_DRILL_INCOME,
  canConvertToAlbatrossMenu,
  canConvertToLeviathanMenu,
  canFieldRepairMenu,
  canRevertTransportMenu,
  canTransportDisembarkMenu,
  canWarmachineDrillMenu,
  canWarmachineFabricateMenu,
  contextMenuHasAnyAction,
  findPassengerOnTransport,
  prettyUnitTypeName,
  transportPickupMenuEntries,
} from "@/lib/game/unitContextMenuEligibility";

/**
 * Swing parity: context menu entries for the active player's unit (War Machine fabrication/drill,
 * field repair, transport pickup/disembark, Albatross/Leviathan morph or revert).
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
    contextMenuHasAnyAction(snapshot, u, welcome.yourSeatIndex);

  useEffect(() => {
    if (!contextMenu) return;
    if (!snapshot || !welcome) return;
    const unit = snapshot.units.find((un) => un.id === contextMenu.unitId);
    if (!unit || !contextMenuHasAnyAction(snapshot, unit, welcome.yourSeatIndex)) {
      closeContextMenu();
    }
  }, [contextMenu, snapshot, welcome, closeContextMenu]);

  if (!contextMenu || !snapshot || !welcome || !menuValid || !u) return null;

  const seat = welcome.yourSeatIndex;

  const left = Math.min(
    contextMenu.clientX,
    typeof window !== "undefined" ? window.innerWidth - 200 : contextMenu.clientX
  );
  const top = Math.min(
    contextMenu.clientY,
    typeof window !== "undefined" ? window.innerHeight - 56 : contextMenu.clientY
  );

  const showFab = canWarmachineFabricateMenu(snapshot, u, seat);
  const showDrill = canWarmachineDrillMenu(snapshot, u, seat);
  const showRepair = canFieldRepairMenu(snapshot, u, seat);
  const showDisembark = canTransportDisembarkMenu(snapshot, u, seat);
  const pickups = transportPickupMenuEntries(snapshot, u, seat);
  const showAlbatross = canConvertToAlbatrossMenu(snapshot, u, seat);
  const showLeviathan = canConvertToLeviathanMenu(snapshot, u, seat);
  const showRevert = canRevertTransportMenu(snapshot, u, seat);

  const cargo = showDisembark ? findPassengerOnTransport(snapshot, u.id) : undefined;

  const btn =
    "flex w-full px-4 py-2 text-left hover:bg-[#2A3530] disabled:cursor-not-allowed disabled:opacity-40";
  const ws = getMatchWebSocketClient();

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
        className="fixed z-[76] min-w-[200px] max-w-[min(100vw-16px,360px)] rounded border bg-[#1A221F] py-1 text-[13px] shadow-lg"
        style={{ borderColor: "#2E3A35", left, top, color: "#E0E6E3" }}
        onPointerDown={(e) => e.stopPropagation()}
      >
        {showFab ? (
          <button
            type="button"
            className={btn}
            onClick={() => {
              openProductionModal({ kind: "warmachine", unitId: u.id });
              closeContextMenu();
            }}
          >
            Fabricate unit…
          </button>
        ) : null}
        {showDrill ? (
          <button
            type="button"
            className={btn}
            onClick={() => {
              ws.warmachineDrill({ matchId: snapshot.matchId, warmachineUnitId: u.id });
              closeContextMenu();
            }}
          >
            Drill ore deposit (+${WARMACHINE_DRILL_INCOME})
          </button>
        ) : null}
        {showRepair ? (
          <button
            type="button"
            className={btn}
            onClick={() => {
              ws.unitRepair({ matchId: snapshot.matchId, unitId: u.id });
              closeContextMenu();
            }}
          >
            Field repair (no move this turn; heal ¼ max HP next turn if not hit; +20% damage if hit)
          </button>
        ) : null}
        {showDisembark ? (
          <button
            type="button"
            className={btn}
            onClick={() => {
              ws.transportDisembark({ matchId: snapshot.matchId, transportUnitId: u.id });
              closeContextMenu();
            }}
          >
            Disembark {cargo ? prettyUnitTypeName(cargo.unitType) : "cargo"}
          </button>
        ) : null}
        {pickups.map((p) => (
          <button
            key={`${p.passengerUnitId}-${p.directionLabel}`}
            type="button"
            className={btn}
            onClick={() => {
              ws.transportPickup({
                matchId: snapshot.matchId,
                transportUnitId: u.id,
                passengerUnitId: p.passengerUnitId,
              });
              closeContextMenu();
            }}
          >
            Pick up {prettyUnitTypeName(p.passengerUnitType)} ({p.directionLabel})
          </button>
        ))}
        {showAlbatross ? (
          <button
            type="button"
            className={btn}
            onClick={() => {
              ws.convertToAlbatross({ matchId: snapshot.matchId, unitId: u.id });
              closeContextMenu();
            }}
          >
            Convert to Albatross (sky transport)
          </button>
        ) : null}
        {showLeviathan ? (
          <button
            type="button"
            className={btn}
            onClick={() => {
              ws.convertToLeviathan({ matchId: snapshot.matchId, unitId: u.id });
              closeContextMenu();
            }}
          >
            Convert to Leviathan (sea transport)
          </button>
        ) : null}
        {showRevert ? (
          <button
            type="button"
            className={btn}
            onClick={() => {
              ws.revertTransport({ matchId: snapshot.matchId, unitId: u.id });
              closeContextMenu();
            }}
          >
            Disembark to {prettyUnitTypeName(u.originalLandUnitType ?? "original")}
          </button>
        ) : null}
      </div>
    </>
  );
}
