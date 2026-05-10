"use client";

import type { MatchSnapshot, ScWelcome } from "@/lib/protocol/types";
import {
  attackRangeDisplayString,
  getUnitTypeStats,
} from "@/lib/game/unitTypeCatalog";
import { footMoveCostHudNote, terrainDefensePercent } from "@/lib/game/terrainSwingMeta";
import { abilityAbbrev, abilityTooltipText, hslBadgeStyle } from "@/lib/game/abilityHud";
import { useGameHudStore } from "@/stores/gameHudStore";

const THEME = {
  bg: "#1A221F",
  border: "#2E3A35",
  textPri: "#E0E6E3",
  textSec: "#A5B1AC",
  accent: "#4CAF50",
  danger: "#F44336",
  info: "#03A9F4",
} as const;

function escapeHtml(s: string): string {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function niceEnumName(enumValue: string): string {
  return enumValue.toLowerCase().replace(/_/g, " ");
}

export interface GameInfoStripProps {
  snapshot: MatchSnapshot | null;
  welcome: ScWelcome | null;
  missionTitle: string;
  yourTurn: boolean;
}

export function GameInfoStrip({ snapshot, welcome, missionTitle, yourTurn }: GameInfoStripProps) {
  const inspectGrid = useGameHudStore((s) => s.inspectGrid);

  if (!welcome) {
    return (
      <footer
        style={{ background: THEME.bg, borderTop: `1px solid ${THEME.border}` }}
        className="shrink-0 px-4 py-3 text-[13px]"
      >
        <p style={{ color: THEME.textSec }}>Connect to a match to see mission and tile intel.</p>
      </footer>
    );
  }

  const displayLevel = missionTitle?.trim() ? missionTitle.trim() : "Skirmish";

  const gx = inspectGrid?.x;
  const gy = inspectGrid?.y;
  const tile =
    snapshot != null && gx !== undefined && gy !== undefined ? snapshot.tiles[gy]?.[gx] : undefined;
  const tileUnit =
    snapshot?.units.find((u) => gx !== undefined && gy !== undefined && u.x === gx && u.y === gy) ?? null;

  const seat = welcome.yourSeatIndex;
  const activeTeamNum = snapshot ? snapshot.activePlayerIndex + 1 : 0;

  const structureOwnerName = (teamId: number | null | undefined): string => {
    if (teamId == null || teamId < 1 || !snapshot) return "Neutral";
    const p = snapshot.players.find((pl) => pl.seatIndex === teamId - 1);
    return p?.displayName ?? `Team ${teamId}`;
  };

  return (
    <footer
      style={{ background: THEME.bg, borderTop: `1px solid ${THEME.border}` }}
      className="shrink-0 px-4 py-3"
    >
      <div
        className="grid gap-4 md:gap-6"
        style={{
          gridTemplateColumns: "repeat(3, minmax(0, 1fr))",
          minHeight: 180,
          color: THEME.textPri,
          fontSize: 13,
        }}
      >
        <section>
          <h3
            className="mb-2 font-bold uppercase tracking-wide"
            style={{ color: THEME.textSec, fontSize: 10 }}
          >
            Mission
          </h3>
          <p className="font-bold" style={{ fontSize: 15, color: THEME.textPri }}>
            {escapeHtml(displayLevel)}
          </p>
          {snapshot ? (
            <p className="mt-1" style={{ color: THEME.textSec, fontSize: 11 }}>
              Field {snapshot.width} × {snapshot.height} · {snapshot.teamCount} factions
            </p>
          ) : null}
          <p className="mt-1 text-[11px]" style={{ color: THEME.textSec }}>
            Active team · <span className="font-bold text-[#E0E6E3]">{activeTeamNum}</span>
          </p>
        </section>

        <section>
          <h3
            className="mb-2 font-bold uppercase tracking-wide"
            style={{ color: THEME.textSec, fontSize: 10 }}
          >
            Tile
          </h3>
          {inspectGrid == null || !snapshot ? (
            <p style={{ color: THEME.textSec }}>Select a tile on the map.</p>
          ) : !tile ? (
            <p style={{ color: THEME.textSec }}>—</p>
          ) : (
            <>
              <p className="mb-1">
                <span style={{ color: THEME.textSec }}>POS</span>{" "}
                <span className="font-bold">
                  ({inspectGrid.x}, {inspectGrid.y})
                </span>
              </p>
              <p className="leading-snug">
                <span className="font-bold">{niceEnumName(tile.terrain)}</span>
                <br />
                <span style={{ color: THEME.textSec, fontSize: 11 }}>
                  Defense +{terrainDefensePercent(tile.terrain)}% · {footMoveCostHudNote(tile.terrain)}
                </span>
              </p>
              {tile.structure ? (
                <p className="mt-2 leading-snug" style={{ fontSize: 11 }}>
                  <span className="font-bold">{niceEnumName(tile.structure)}</span>
                  <span style={{ color: THEME.textSec }}> · </span>
                  {structureOwnerName(tile.structureTeam)}
                  <br />
                  <span style={{ color: THEME.info, fontSize: 11 }}>
                    Foot units capture by ending turn on tile.
                  </span>
                </p>
              ) : (
                <p className="mt-2" style={{ color: THEME.textSec, fontSize: 11 }}>
                  Structure · none
                </p>
              )}
            </>
          )}
        </section>

        <section>
          <h3
            className="mb-2 font-bold uppercase tracking-wide"
            style={{ color: THEME.textSec, fontSize: 10 }}
          >
            Unit
          </h3>
          {inspectGrid == null || !snapshot ? (
            <p style={{ color: THEME.textSec }}>No tile selected.</p>
          ) : tileUnit ? (
            <UnitBlock
              u={tileUnit}
              snapshot={snapshot}
              seat={seat}
              yourTurn={yourTurn}
            />
          ) : tile?.unitSprite ? (
            <div style={{ maxWidth: 280 }}>
              <span className="font-bold">Placed unit </span>
              <span style={{ color: THEME.textSec }}>(map asset)</span>
              <br />
              <span style={{ fontSize: 11 }}>
                {tile.unitTeam != null ? `Team ${tile.unitTeam} · ` : ""}Sprite:{" "}
                {escapeHtml(tile.unitSprite)}
              </span>
              <p className="mt-1" style={{ color: THEME.textSec, fontSize: 11 }}>
                Editor preview — start mission spawns real units.
              </p>
            </div>
          ) : (
            <>
              <p style={{ color: THEME.textSec }}>No unit on this tile.</p>
              {tile?.structure === "Factory" &&
                tile.structureTeam === seat + 1 &&
                !snapshot.units.some((un) => un.x === gx && un.y === gy) &&
                yourTurn && (
                  <p className="mt-2 text-[11px]" style={{ color: THEME.info }}>
                    Vacant allied factory · left-click tile opens the production console.
                  </p>
                )}
            </>
          )}
        </section>
      </div>
    </footer>
  );
}

function UnitBlock(props: {
  u: NonNullable<MatchSnapshot["units"][number]>;
  snapshot: MatchSnapshot;
  seat: number;
  yourTurn: boolean;
}) {
  const { u, snapshot, seat, yourTurn } = props;
  const activeSeat = snapshot.activePlayerIndex;
  const isYoursVsActive = u.ownerSeatIndex === activeSeat;
  const ownerName = snapshot.players.find((p) => p.seatIndex === u.ownerSeatIndex)?.displayName ?? "—";
  const actionStatus = u.hasMoved ? "used" : "ready";
  const actionColor = u.hasMoved ? THEME.textSec : THEME.accent;
  const st = getUnitTypeStats(u.unitType);

  return (
    <div style={{ maxWidth: 280 }}>
      <p className="leading-snug">
        <span className="font-bold">{niceEnumName(u.unitType)}</span>
        <span style={{ color: THEME.textSec }}> · {escapeHtml(ownerName)}</span>
        <br />
        <span style={{ color: THEME.textSec }}>HP</span> {u.health}
        <span style={{ color: THEME.textSec }}> · Move</span> {st?.movementSpeed ?? "—"}
        <span style={{ color: THEME.textSec }}> · Range</span>{" "}
        {st ? attackRangeDisplayString(st.attackRange) : "—"}
        <span style={{ color: THEME.textSec }}> · Power</span> {st?.damage ?? "—"}
        <br />
        <span style={{ color: THEME.textSec }}>Armor</span> {st?.armorType ?? "—"}
        <span style={{ color: THEME.textSec }}> · Attack</span> {st?.attackType ?? "—"}
        <br />
        {isYoursVsActive ? (
          <span style={{ color: actionColor, fontSize: 12 }}>Action {actionStatus}</span>
        ) : (
          <span style={{ color: THEME.danger, fontSize: 12 }}>Hostile contact</span>
        )}
      </p>
      {st?.abilities?.length ? (
        <div className="mt-2 flex flex-wrap gap-1">
          {Array.from(new Set(st.abilities.filter(Boolean))).map((a) => {
            const hsl = hslBadgeStyle(a);
            const bg = `hsl(${Math.round(hsl.h)} ${Math.round(hsl.s)}% ${Math.round(hsl.l)}%)`;
            return (
              <span
                key={a}
                title={abilityTooltipText(a)}
                className="inline-flex min-w-[26px] items-center justify-center rounded px-1 py-0.5 text-[10px] font-bold text-white shadow-sm ring-1 ring-black/35"
                style={{ background: bg }}
              >
                {abilityAbbrev(a)}
              </span>
            );
          })}
        </div>
      ) : null}
      {yourTurn &&
        u.unitType === "Warmachine" &&
        !u.hasMoved &&
        u.ownerSeatIndex === seat &&
        snapshot.activePlayerIndex === seat && (
          <p className="mt-2 text-[11px]" style={{ color: THEME.textSec }}>
            Right-click the War Machine on the map → <span style={{ color: THEME.textPri }}>Fabricate unit…</span>
          </p>
        )}
    </div>
  );
}
