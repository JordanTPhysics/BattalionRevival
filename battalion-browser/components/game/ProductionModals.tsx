"use client";

/* eslint-disable @next/next/no-img-element -- unit roster thumbnails from /public (Swing AssetManager parity) */
import { useEffect, useMemo, useState } from "react";
import {
  factoryBuildEligiblePreview,
  factoryCategoryAvailability,
  playerMoney,
  structureTeamIdFromSeat,
  warmachineBuildEligiblePreview,
} from "@/lib/game/snapshotStructure";
import { uncolouredUnitTextureUrl } from "@/lib/game/renderPaths";
import { getMovementSheetFirstFrameDataUrl } from "@/lib/game/unitSheetFrames";
import { unitRgbFromOwnerSeat } from "@/lib/game/swingTeamColors";
import { factoryBuildPrice, factoryPurchasableUnitTypes, getUnitTypeStats, isWarmachineProducibleType } from "@/lib/game/unitTypeCatalog";
import { getMatchWebSocketClient } from "@/stores/matchStore";
import { useGameHudStore } from "@/stores/gameHudStore";
import { useMatchStore } from "@/stores/matchStore";

const PANEL = "#1A221F";
const BORDER = "#2E3A35";
const BORDER_STRONG = "#3D4D45";
const ACCENT = "#4CAF50";
const ACCENT_PRICE = "#8BC34A";
const TEXT_PRI = "#E0E6E3";
const TEXT_SEC = "#A5B1AC";
const DANGER = "#F44336";

function niceUnitName(enumName: string): string {
  return enumName.toLowerCase().replace(/_/g, " ");
}

function sortedTypes(types: readonly string[]): string[] {
  return [...types].sort((a, b) => a.localeCompare(b));
}

/** Crop + team-tint first EAST walk frame from the movement sheet (matches in-map decode). */
function MovementSheetUnitThumb({ unitType, teamRgb }: { unitType: string; teamRgb: number }) {
  const fallbackSrc = uncolouredUnitTextureUrl(unitType);
  const [decodedSrc, setDecodedSrc] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void getMovementSheetFirstFrameDataUrl(unitType, teamRgb).then((dataUrl) => {
      if (!cancelled && dataUrl) {
        setDecodedSrc(dataUrl);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [unitType, teamRgb]);

  return (
    <img
      src={decodedSrc ?? fallbackSrc}
      alt=""
      width={40}
      height={28}
      className="h-7 w-10 object-contain"
      draggable={false}
    />
  );
}

function Pill(props: { label: string; ok: boolean }) {
  const bg = props.ok ? ACCENT : DANGER;
  return (
    <span
      className="rounded px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-white"
      style={{ background: bg }}
    >
      {props.label}
    </span>
  );
}

/** Full-window production UI matching Swing {@code FactoryBuildDialog} / {@code WarmachineBuildDialog}. */
export function ProductionModals() {
  const productionModal = useGameHudStore((s) => s.productionModal);
  const closeProductionModal = useGameHudStore((s) => s.closeProductionModal);
  const snapshot = useMatchStore((s) => s.snapshot);
  const welcome = useMatchStore((s) => s.welcome);

  useEffect(() => {
    if (!productionModal) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") closeProductionModal();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [productionModal, closeProductionModal]);

  const content = useMemo(() => {
    if (!productionModal || !snapshot || !welcome || snapshot.matchFinished) return null;

    const seat = welcome.yourSeatIndex;
    const activeSeat = snapshot.activePlayerIndex;
    /** Local client cannot command when not seated as active player — mirror Swing modal lockouts. */
    if (seat !== activeSeat) return null;

    if (productionModal.kind === "factory") {
      const { x: fx, y: fy } = productionModal;
      const tile = snapshot.tiles[fy]?.[fx];
      if (!tile || tile.structure !== "Factory") return null;
      if (tile.structureTeam !== structureTeamIdFromSeat(activeSeat)) return null;
      if (snapshot.units.some((u) => u.health > 0 && u.x === fx && u.y === fy)) return null;

      const catOn = factoryCategoryAvailability(snapshot, activeSeat, fx, fy);
      const money = playerMoney(snapshot, activeSeat);
      const playerName =
        snapshot.players.find((p) => p.seatIndex === activeSeat)?.displayName?.toUpperCase() ?? `SEAT ${activeSeat}`;
      const roster = sortedTypes(factoryPurchasableUnitTypes());
      const rosterTeamRgb = unitRgbFromOwnerSeat(activeSeat);
      const byCat = { LAND: [] as string[], SEA: [] as string[], AIR: [] as string[] };
      for (const ut of roster) {
        const c = getUnitTypeStats(ut)?.factoryBuildCategory;
        if (c === "LAND") byCat.LAND.push(ut);
        else if (c === "SEA") byCat.SEA.push(ut);
        else if (c === "AIR") byCat.AIR.push(ut);
      }

      const buildCatPanel = (title: string, requirementHtml: string, categoryOnline: boolean, types: string[]) => (
        <div
          key={title}
          className="rounded border p-4"
          style={{
            background: PANEL,
            borderColor: BORDER,
            borderLeftWidth: 4,
            borderLeftColor: categoryOnline ? ACCENT : BORDER_STRONG,
          }}
        >
          <div className="mb-2 flex flex-wrap items-center gap-2">
            <h4 className="text-[15px] font-bold" style={{ color: TEXT_PRI }}>
              {title}
            </h4>
            <Pill label={categoryOnline ? "ONLINE" : "OFFLINE"} ok={categoryOnline} />
          </div>
          <p
            className="mb-4 max-w-[480px] text-[11px] leading-snug"
            style={{ color: TEXT_SEC }}
            dangerouslySetInnerHTML={{ __html: requirementHtml }}
          />
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4 md:grid-cols-8">
            {types.map((ut) => {
              const price = factoryBuildPrice(ut);
              if (price === null) return null;
              const can = factoryBuildEligiblePreview(snapshot, activeSeat, fx, fy, ut);
              const affordable = money >= price;
              const enabled = categoryOnline && can && affordable;
              let tip = `Build ${niceUnitName(ut)} for $${price}`;
              if (!categoryOnline) tip = "Category locked — see requirements above.";
              else if (!can) tip = "Cannot build this unit (no spawn tile or rules).";
              else if (!affordable) tip = `Not enough funds ($${price} required).`;
              return (
                <button
                  key={ut}
                  type="button"
                  title={tip}
                  disabled={!enabled}
                  onClick={() => {
                    getMatchWebSocketClient().factoryBuild({
                      matchId: snapshot.matchId,
                      factoryX: fx,
                      factoryY: fy,
                      unitType: ut,
                    });
                    closeProductionModal();
                  }}
                  className="flex flex-col items-center gap-1 rounded border px-1 py-2 text-[11px] disabled:cursor-not-allowed disabled:opacity-40"
                  style={{
                    borderColor: BORDER,
                    color: TEXT_PRI,
                    background: "#222C28",
                  }}
                >
                  <MovementSheetUnitThumb key={`${ut}-${rosterTeamRgb}`} unitType={ut} teamRgb={rosterTeamRgb} />
                  <span className="text-center font-bold leading-tight">{niceUnitName(ut)}</span>
                  <span style={{ color: affordable ? ACCENT_PRICE : DANGER }}>${price}</span>
                </button>
              );
            })}
          </div>
        </div>
      );

      const landReq = catOn.land
        ? "Requires a Ground Control structure under your command."
        : "Requires a Ground Control structure under your command. <span style='color:#F44336'>— requirements not met.</span>";
      const seaReq = catOn.sea
        ? "Requires Sea Control and a factory adjacent to a coastal shore tile."
        : "Requires Sea Control and a factory adjacent to a coastal shore tile. <span style='color:#F44336'>— requirements not met.</span>";
      const airReq = catOn.air
        ? "Requires an Air Control structure under your command."
        : "Requires an Air Control structure under your command. <span style='color:#F44336'>— requirements not met.</span>";

      const header = (
        <header
          className="mb-4 flex flex-wrap items-start justify-between gap-3 border-b pb-4"
          style={{ borderColor: BORDER }}
        >
          <div>
            <p className="text-[11px] font-bold uppercase tracking-wide" style={{ color: ACCENT }}>
              FORWARD FACTORY
            </p>
            <h2 className="mt-0.5 text-xl font-bold" style={{ color: TEXT_PRI }}>
              Production Console
            </h2>
            <p className="mt-2 flex flex-wrap items-center gap-2 text-[11px]" style={{ color: TEXT_SEC }}>
              <span>
                Position ({fx}, {fy})
              </span>
              <span
                className="rounded-full border px-2 py-px text-[10px] font-bold uppercase"
                style={{ borderColor: ACCENT, color: ACCENT }}
              >
                {playerName}
              </span>
              <span>
                · Funds <span style={{ color: TEXT_PRI }}>${money}</span>
              </span>
            </p>
          </div>
          <button
            type="button"
            onClick={() => closeProductionModal()}
            className="rounded border px-4 py-2 text-[12px] font-bold hover:bg-[#2A3530]"
            style={{ borderColor: BORDER, color: TEXT_SEC }}
          >
            Close [ESC]
          </button>
        </header>
      );

      return (
        <>
          {header}
          <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto pr-1">
            {buildCatPanel("Land Battalion", landReq, catOn.land, byCat.LAND)}
            {buildCatPanel("Naval Forces", seaReq, catOn.sea, byCat.SEA)}
            {buildCatPanel("Air Wing", airReq, catOn.air, byCat.AIR)}
          </div>
        </>
      );
    }

    const wmUnit = snapshot.units.find((u) => u.id === productionModal.unitId);
    if (!wmUnit || wmUnit.unitType !== "Warmachine") return null;
    if (wmUnit.ownerSeatIndex !== activeSeat || wmUnit.hasMoved) return null;

    const purse = wmUnit.warmachineFunds ?? 0;
    const wmRosterTeamRgb = unitRgbFromOwnerSeat(activeSeat);
    const mx = wmUnit.x;
    const my = wmUnit.y;
    const playerName =
      snapshot.players.find((p) => p.seatIndex === activeSeat)?.displayName?.toUpperCase() ?? `SEAT ${activeSeat}`;

    const typesByCat = { LAND: [] as string[], SEA: [] as string[], AIR: [] as string[] };
    for (const ut of sortedTypes(factoryPurchasableUnitTypes())) {
      if (!isWarmachineProducibleType(ut)) continue;
      const c = getUnitTypeStats(ut)?.factoryBuildCategory;
      if (c === "LAND") typesByCat.LAND.push(ut);
      else if (c === "SEA") typesByCat.SEA.push(ut);
      else if (c === "AIR") typesByCat.AIR.push(ut);
    }

    const wmCatPanel = (title: string, captionHtml: string, types: string[]) => (
      <div
        key={title}
        className="rounded border p-4"
        style={{
          background: PANEL,
          borderColor: BORDER,
          borderLeftWidth: 4,
          borderLeftColor: ACCENT,
        }}
      >
        <div className="mb-2">
          <h4 className="text-[15px] font-bold" style={{ color: TEXT_PRI }}>
            {title}
          </h4>
        </div>
        <p
          className="mb-4 max-w-[480px] text-[11px] leading-snug"
          style={{ color: TEXT_SEC }}
          dangerouslySetInnerHTML={{ __html: captionHtml }}
        />
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-4 md:grid-cols-8">
          {types.map((ut) => {
            const price = factoryBuildPrice(ut);
            if (price === null) return null;
            const rulesOk = warmachineBuildEligiblePreview(wmUnit, snapshot, activeSeat, ut, price);
            const affordable = purse >= price;
            const enabled = rulesOk && affordable;
            let tip = `Fabricate ${niceUnitName(ut)} for $${price}`;
            if (!rulesOk) tip = "Cannot fabricate here (blocked or no adjacent spawn).";
            else if (!affordable) tip = `Not enough onboard funds ($${price} required).`;
            return (
              <button
                key={ut}
                type="button"
                title={tip}
                disabled={!enabled}
                onClick={() => {
                  getMatchWebSocketClient().warmachineBuild({
                    matchId: snapshot.matchId,
                    warmachineUnitId: wmUnit.id,
                    unitType: ut,
                  });
                  closeProductionModal();
                }}
                className="flex flex-col items-center gap-1 rounded border px-1 py-2 text-[11px] disabled:cursor-not-allowed disabled:opacity-40"
                style={{
                  borderColor: BORDER,
                  color: TEXT_PRI,
                  background: "#222C28",
                }}
              >
                <MovementSheetUnitThumb key={`${ut}-${wmRosterTeamRgb}`} unitType={ut} teamRgb={wmRosterTeamRgb} />
                <span className="text-center font-bold leading-tight">{niceUnitName(ut)}</span>
                <span style={{ color: affordable ? ACCENT_PRICE : DANGER }}>${price}</span>
              </button>
            );
          })}
        </div>
      </div>
    );

    const wmHeader = (
      <header
        className="mb-4 flex flex-wrap items-start justify-between gap-3 border-b pb-4"
        style={{ borderColor: BORDER }}
      >
        <div>
          <p className="text-[11px] font-bold uppercase tracking-wide" style={{ color: ACCENT }}>
            WAR FORGE
          </p>
          <h2 className="mt-0.5 text-xl font-bold" style={{ color: TEXT_PRI }}>
            Onboard Fabricator
          </h2>
          <p className="mt-2 flex flex-wrap items-center gap-2 text-[11px]" style={{ color: TEXT_SEC }}>
            <span>
              At ({mx}, {my})
            </span>
            <span
              className="rounded-full border px-2 py-px text-[10px] font-bold uppercase"
              style={{ borderColor: ACCENT, color: ACCENT }}
            >
              {playerName}
            </span>
            <span>
              · War purse <span style={{ color: TEXT_PRI }}>${purse}</span>
            </span>
          </p>
        </div>
        <button
          type="button"
          onClick={() => closeProductionModal()}
          className="rounded border px-4 py-2 text-[12px] font-bold hover:bg-[#2A3530]"
          style={{ borderColor: BORDER, color: TEXT_SEC }}
        >
          Close [ESC]
        </button>
      </header>
    );

    return (
      <>
        {wmHeader}
        <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto pr-1">
          {wmCatPanel(
            "Land Battalion",
            "No Ground Control requirement. Unit appears on an empty adjacent tile the type can traverse.",
            typesByCat.LAND
          )}
          {wmCatPanel(
            "Naval Forces",
            "No Sea Control requirement. Naval units need a neighbouring sea lane with a legal spawn.",
            typesByCat.SEA
          )}
          {wmCatPanel("Air Wing", "No Air Control requirement.", typesByCat.AIR)}
        </div>
      </>
    );
  }, [productionModal, snapshot, welcome, closeProductionModal]);

  if (!productionModal || !content) return null;

  return (
    <div
      role="presentation"
      className="fixed inset-0 z-[80] overflow-hidden"
      style={{ background: "#0f1412f0" }}
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) closeProductionModal();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        className="pointer-events-auto absolute inset-0 flex flex-col p-6 md:p-10"
      >
        {content}
      </div>
    </div>
  );
}
