"use client";

import { useEffect, useRef } from "react";
import {
  Application,
  Assets,
  Container,
  FederatedPointerEvent,
  Graphics,
  Sprite,
  Text,
  Texture,
} from "pixi.js";
import type { GridPoint, MatchSnapshot, UnitSnapshot } from "@/lib/protocol/types";
import {
  structureTextureUrl,
  terrainTextureUrl,
  uncolouredStructureTextureUrl,
  uncolouredUnitTextureUrl,
  unitTextureUrl,
} from "@/lib/game/renderPaths";
import {
  getUncolouredEastFrameTeamTexture,
  getUncolouredFullImageTeamTexture,
} from "@/lib/game/uncolouredTextures";
import {
  layoutStructureSprite,
  layoutTerrainSprite,
  layoutUnitSpriteInTileCell,
  terrainScaledDrawHeight,
  TILE_PX,
} from "@/lib/game/pixiTileLayout";
import { terrainFallbackRgb } from "@/lib/game/terrainFallbackRgb";
import { unitRgbFromOwnerSeat, teamRgbFromTeamId } from "@/lib/game/swingTeamColors";
import { getMatchWebSocketClient } from "@/stores/matchStore";
import { useGameHudStore } from "@/stores/gameHudStore";
import {
  isValidMovementPathSnapshot,
  reachableEndTiles,
  shortestLegalPathIncludingStart,
} from "@/lib/game/clientPathfinding";
import { attackPairingReachable, canClientPreviewAttack } from "@/lib/game/clientAttack";
import { getUnitTypeStats } from "@/lib/game/unitTypeCatalog";
import { playAttackSfx, playBuildSfx, playExplosionSfx, playMovementSfx, playScavengeSfx, playCaptureSfx, playCloakingSfx, playExtractResourcesSfx, playStartTurnSfx, playTankCloakingSfx } from "@/lib/game/movementSounds";
import {
  computeAttackableEnemyCells,
  computeAttackRangePreviewCells,
  computeIdleCrosshairCells,
} from "@/lib/game/swingMapHighlights";
import { structureTeamIdFromSeat } from "@/lib/game/snapshotStructure";
import {
  facingFromGridStep,
  parseFacing,
  sheetColumnForAnimation,
  sheetColumnForAttackAnimation,
  sheetRowForAnimation,
  UNIT_ATTACK_SHEET_COLUMNS,
  UNIT_SHEET_COLUMNS,
  type CardinalFacing,
  type SheetLayout,
} from "@/lib/game/unitSpriteSheet";
import { getMaskedSheetFrameTextureSync, pickUnitSheetUrl } from "@/lib/game/unitSheetFrames";
import { updateMovementPathFromHover } from "@/lib/game/movementPathHover";

const DEFAULT_STRUCTURE_COLOR = 0x6b4f2a;
const DEFAULT_UNIT_COLOR = 0xc4c4c4;

const OVERLAY_WHITE = { color: 0xffffff, alpha: 72 / 255 };
const OVERLAY_ATTACK_PREVIEW = { color: 0xff4b4b, alpha: 72 / 255 };
const OVERLAY_ATTACKABLE = { color: 0xff3c3c, alpha: 100 / 255 };

async function loadOrFallback(url: string, color: number, w: number, h: number): Promise<Texture> {
  try {
    return await Assets.load(url);
  } catch {
    const canvas = document.createElement("canvas");
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext("2d");
    if (ctx) {
      ctx.fillStyle = `#${color.toString(16).padStart(6, "0")}`;
      ctx.fillRect(0, 0, w, h);
    }
    return Texture.from(canvas);
  }
}

const textureMemo = new Map<string, Texture>();

async function getTexture(key: string, factory: () => Promise<Texture>): Promise<Texture> {
  const hit = textureMemo.get(key);
  if (hit) {
    return hit;
  }
  const t = await factory();
  textureMemo.set(key, t);
  return t;
}

export interface GameInteractionConfig {
  matchId: string;
  yourSeatIndex: number;
  activePlayerIndex: number;
}

export interface GameCanvasProps {
  snapshot: MatchSnapshot | null;
  className?: string;
  interaction?: GameInteractionConfig | null;
}

function xyKey(x: number, y: number): string {
  return `${x},${y}`;
}

function occupant(snapshot: MatchSnapshot, gx: number, gy: number): UnitSnapshot | undefined {
  return snapshot.units.find((u) => u.x === gx && u.y === gy && u.health > 0);
}

function resolveYellowSelectionUnit(snapshot: MatchSnapshot | null, mapSelectedGrid: { x: number; y: number } | null) {
  if (!snapshot || !mapSelectedGrid) {
    return null;
  }
  const u = occupant(snapshot, mapSelectedGrid.x, mapSelectedGrid.y);
  if (!u) {
    return null;
  }
  if (mapSelectedGrid.x !== u.x || mapSelectedGrid.y !== u.y) {
    return null;
  }
  return u;
}

function drawSwingIdleCrosshairBracket(
  g: Graphics,
  gx: number,
  gy: number,
  ts: number,
  color: number,
  alpha: number,
  offs: number
): void {
  const inset = Math.max(2, ts * 0.08);
  const leg = Math.max(5, ts * 0.26);
  const sw = Math.max(1.35, ts / 20);
  const px = gx * ts;
  const py = gy * ts;
  const x0 = px + inset + offs;
  const y0 = py + inset + offs;
  const x1 = px + ts - inset + offs;
  const y1 = py + ts - inset + offs;

  g.moveTo(x0, y0);
  g.lineTo(x0 + leg, y0);
  g.moveTo(x0, y0);
  g.lineTo(x0, y0 + leg);

  g.moveTo(x1, y0);
  g.lineTo(x1 - leg, y0);
  g.moveTo(x1, y0);
  g.lineTo(x1, y0 + leg);

  g.moveTo(x0, y1);
  g.lineTo(x0 + leg, y1);
  g.moveTo(x0, y1);
  g.lineTo(x0, y1 - leg);

  g.moveTo(x1, y1);
  g.lineTo(x1 - leg, y1);
  g.moveTo(x1, y1);
  g.lineTo(x1, y1 - leg);

  g.setStrokeStyle({ width: sw, color, alpha });
  g.stroke();
}

function drawSwingIdleCrosshair(layer: Container, gx: number, gy: number, ts: number): void {
  const shadow = new Graphics();
  drawSwingIdleCrosshairBracket(shadow, gx, gy, ts, 0x0a0a0a, 0.47, 0.75);
  layer.addChild(shadow);
  const line = new Graphics();
  drawSwingIdleCrosshairBracket(line, gx, gy, ts, 0xf5f5f5, 0.92, 0);
  layer.addChild(line);
}

function shouldDrawFriendlyOccupiedHoverMarker(
  snapshot: MatchSnapshot,
  cfg: GameInteractionConfig,
  mover: UnitSnapshot,
  hover: { x: number; y: number } | null
): boolean {
  if (!hover) {
    return false;
  }
  if (snapshot.activePlayerIndex !== cfg.yourSeatIndex) {
    return false;
  }
  const occ = occupant(snapshot, hover.x, hover.y);
  if (!occ || occ.id === mover.id) {
    return false;
  }
  return occ.ownerSeatIndex === mover.ownerSeatIndex;
}

function drawFriendlyHoverX(g: Graphics, hx: number, hy: number, ts: number): void {
  const inset = Math.max(6, ts * 0.22);
  const px = hx * ts;
  const py = hy * ts;
  const x0 = px + inset;
  const y0 = py + inset;
  const x1 = px + ts - inset;
  const y1 = py + ts - inset;
  const w = Math.max(2.5, ts / 10);
  g.moveTo(x0, y0);
  g.lineTo(x1, y1);
  g.moveTo(x1, y0);
  g.lineTo(x0, y1);
  g.setStrokeStyle({ width: w, color: 0xff7878, alpha: 0.9 });
  g.stroke();

  const o = 0.6;
  g.moveTo(x0 - o, y0 - o);
  g.lineTo(x1 + o, y1 + o);
  g.moveTo(x1 + o, y0 - o);
  g.lineTo(x0 - o, y1 + o);
  g.setStrokeStyle({ width: w, color: 0x5a2323, alpha: 0.78 });
  g.stroke();
}

function drawArrowHead(g: Graphics, from: { x: number; y: number }, to: { x: number; y: number }, ts: number): void {
  const fx = from.x * ts + ts * 0.5;
  const fy = from.y * ts + ts * 0.5;
  const tx = to.x * ts + ts * 0.5;
  const ty = to.y * ts + ts * 0.5;
  const dx = tx - fx;
  const dy = ty - fy;
  const len = Math.hypot(dx, dy);
  if (len < 1e-3) {
    return;
  }
  const ux = dx / len;
  const uy = dy / len;
  const tipInset = Math.min(ts * 0.38, len * 0.45);
  const baseX = tx - ux * tipInset;
  const baseY = ty - uy * tipInset;
  const side = ts * 0.3;
  const px = -uy * side;
  const py = ux * side;
  g.moveTo(tx, ty);
  g.lineTo(baseX + px, baseY + py);
  g.lineTo(baseX - px, baseY - py);
  g.closePath();
  g.fill({ color: 0xffe68c, alpha: 0.92 });
  g.setStrokeStyle({ width: Math.max(1, ts / 28), color: 0x463719, alpha: 0.82 });
  g.stroke();
}

function maybeOpenSwingFactoryModal(snap: MatchSnapshot, cfg: GameInteractionConfig, gx: number, gy: number): void {
  if (snap.matchFinished || snap.activePlayerIndex !== cfg.yourSeatIndex) return;
  const tile = snap.tiles[gy]?.[gx];
  if (!tile || tile.structure !== "Factory") return;
  if (tile.structureTeam !== structureTeamIdFromSeat(snap.activePlayerIndex)) return;
  if (snap.units.some((u) => u.health > 0 && u.x === gx && u.y === gy)) return;
  useGameHudStore.getState().openProductionModal({ kind: "factory", x: gx, y: gy });
}

function pathsEqual(a: readonly { x: number; y: number }[], b: readonly { x: number; y: number }[]): boolean {
  if (a.length !== b.length) {
    return false;
  }
  for (let i = 0; i < a.length; i++) {
    const p = a[i];
    const q = b[i];
    if (!p || !q || p.x !== q.x || p.y !== q.y) {
      return false;
    }
  }
  return true;
}

/** Orthogonal steps from a to b (x first, then y) — visual fallback when no navmesh path is found. */
function greedyTaxicabPath(a: GridPoint, b: GridPoint): GridPoint[] {
  const path: GridPoint[] = [{ x: a.x, y: a.y }];
  let x = a.x;
  let y = a.y;
  while (x !== b.x || y !== b.y) {
    if (x < b.x) x++;
    else if (x > b.x) x--;
    else if (y < b.y) y++;
    else if (y > b.y) y--;
    path.push({ x, y });
  }
  return path;
}

function buildTilewiseMovePath(snap: MatchSnapshot, unit: UnitSnapshot, from: GridPoint, to: GridPoint): GridPoint[] {
  if (from.x === to.x && from.y === to.y) {
    return [from];
  }
  const serverPath = unit.lastMovePathIncludingStart;
  if (
    serverPath &&
    serverPath.length >= 2 &&
    serverPath[0]!.x === from.x &&
    serverPath[0]!.y === from.y &&
    serverPath[serverPath.length - 1]!.x === to.x &&
    serverPath[serverPath.length - 1]!.y === to.y
  ) {
    return serverPath.map((p) => ({ x: p.x, y: p.y }));
  }
  const moverAtStart: UnitSnapshot = { ...unit, x: from.x, y: from.y };
  const legal = shortestLegalPathIncludingStart(snap, moverAtStart, to.x, to.y);
  if (legal !== null && legal.length >= 2) {
    return legal.map((p) => ({ x: p.x, y: p.y }));
  }
  return greedyTaxicabPath(from, to);
}

type PriorUnitState = { x: number; y: number; health: number; hasMoved: boolean };

/** One AI / opponent batch step — spawns paint before moves (deterministic {@code unitId}). */
type OpponentQueuedAction =
  | { kind: "spawn"; unitId: string; u: UnitSnapshot }
  | {
    kind: "move";
    unitId: string;
    u: UnitSnapshot;
    from: GridPoint;
    to: GridPoint;
    postMoveAttackToward?: GridPoint;
  }
  | { kind: "attack"; unitId: string; u: UnitSnapshot; toward: GridPoint };

type SequentialHolderInfo = {
  holder: Container;
  sprite: Sprite;
  teamRgb: number;
  sheetUrl: string;
  layout: SheetLayout | null;
  stats: ReturnType<typeof getUnitTypeStats>;
  followupAttack?: {
    atkSheetUrl: string;
    rowCount: number;
    sheetColumns: number;
    facing: CardinalFacing;
  };
  standaloneAttack?: {
    atkSheetUrl: string;
    rowCount: number;
    sheetColumns: number;
    facing: CardinalFacing;
  };
};

const SEQUENTIAL_GAP_SAME_SEAT_MOVE_MS = 500;
const SEQUENTIAL_GAP_SEAT_CHANGE_MS = 2000;

function opponentActionKindRank(k: OpponentQueuedAction["kind"]): number {
  if (k === "spawn") {
    return 0;
  }
  if (k === "move") {
    return 1;
  }
  return 2;
}

function compareOpponentQueuedAction(a: OpponentQueuedAction, b: OpponentQueuedAction): number {
  const seatDelta = a.u.ownerSeatIndex - b.u.ownerSeatIndex;
  if (seatDelta !== 0) {
    return seatDelta;
  }
  const kd = opponentActionKindRank(a.kind) - opponentActionKindRank(b.kind);
  if (kd !== 0) {
    return kd;
  }
  return a.unitId.localeCompare(b.unitId);
}

function sequentialGapMsBetween(prevItem: OpponentQueuedAction | null, curItem: OpponentQueuedAction): number {
  if (!prevItem) {
    return 0;
  }
  if (prevItem.u.ownerSeatIndex !== curItem.u.ownerSeatIndex) {
    return SEQUENTIAL_GAP_SEAT_CHANGE_MS;
  }
  if (prevItem.kind === "move" && curItem.kind === "move") {
    return SEQUENTIAL_GAP_SAME_SEAT_MOVE_MS;
  }
  return 0;
}

/**
 * When it is not the local player's turn and several units change in one snapshot (typical AI turn),
 * return an ordered queue so animations run one-after-another instead of overlapping.
 */
function buildOpponentSequentialPlan(
  snap: MatchSnapshot,
  cfg: GameInteractionConfig | null,
  prevById: Map<string, PriorUnitState>
): OpponentQueuedAction[] | null {
  if (snap.matchFinished) {
    return null;
  }
  if (prevById.size === 0) {
    return null;
  }
  const oppSeat = (seat: number): boolean => !cfg || seat !== cfg.yourSeatIndex;

  const items: OpponentQueuedAction[] = [];
  for (const u of snap.units) {
    const pu = prevById.get(u.id);
    if (pu === undefined) {
      items.push({ kind: "spawn", unitId: u.id, u });
    } else if (pu.x !== u.x || pu.y !== u.y) {
      items.push({
        kind: "move",
        unitId: u.id,
        u,
        from: { x: pu.x, y: pu.y },
        to: { x: u.x, y: u.y },
      });
    }
  }

  const victims: UnitSnapshot[] = [];
  for (const u of snap.units) {
    const p = prevById.get(u.id);
    if (p && p.x === u.x && p.y === u.y && u.health < p.health) {
      victims.push(u);
    }
  }
  victims.sort((a, b) => a.id.localeCompare(b.id));

  const claimedVictims = new Set<string>();
  const usedAttackers = new Set<string>();

  for (const it of items) {
    if (it.kind !== "move" || !it.u.hasMoved) {
      continue;
    }
    const moverAtDest: UnitSnapshot = { ...it.u, x: it.to.x, y: it.to.y };
    for (const v of victims) {
      if (claimedVictims.has(v.id)) {
        continue;
      }
      if (attackPairingReachable(snap, moverAtDest, v)) {
        it.postMoveAttackToward = { x: v.x, y: v.y };
        claimedVictims.add(v.id);
        usedAttackers.add(it.unitId);
        break;
      }
    }
  }

  const sortedAttackers = [...snap.units].sort((a, b) => a.id.localeCompare(b.id));
  for (const u of sortedAttackers) {
    const p = prevById.get(u.id);
    if (!p || p.x !== u.x || p.y !== u.y || !u.hasMoved || p.hasMoved) {
      continue;
    }
    if (usedAttackers.has(u.id) || !oppSeat(u.ownerSeatIndex)) {
      continue;
    }
    for (const v of victims) {
      if (claimedVictims.has(v.id)) {
        continue;
      }
      if (attackPairingReachable(snap, u, v)) {
        items.push({ kind: "attack", unitId: u.id, u, toward: { x: v.x, y: v.y } });
        claimedVictims.add(v.id);
        usedAttackers.add(u.id);
        break;
      }
    }
  }

  if (items.length === 0) {
    return null;
  }
  if (items.length === 1) {
    const only = items[0]!;
    if (only.kind !== "attack") {
      return null;
    }
    if (cfg && only.u.ownerSeatIndex === cfg.yourSeatIndex) {
      return null;
    }
  }
  if (cfg) {
    const hasOpponentAction = items.some((it) => it.u.ownerSeatIndex !== cfg.yourSeatIndex);
    if (!hasOpponentAction) {
      return null;
    }
  }
  items.sort(compareOpponentQueuedAction);
  return items;
}

const WALK_FRAME_MS = 72;
const ATTACK_FRAME_MS = 55;

function clamp01(v: number): number {
  return Math.min(1, Math.max(0, v));
}

/** Sample movement / attack frame — movement uses 6-column Swing mapping; attack strips use 4 columns + {@code attack_rows.json}. */
function orientationSheetTexture(
  sheetUrl: string,
  teamRgb: number,
  rowCount: number,
  facing: CardinalFacing,
  animMs: number,
  frameMs: number,
  sheetColumns: number = UNIT_SHEET_COLUMNS
): Texture | null {
  const animIx = Math.floor(animMs / frameMs);
  const row = sheetRowForAnimation(animIx, rowCount);
  const col =
    sheetColumns === UNIT_ATTACK_SHEET_COLUMNS
      ? sheetColumnForAttackAnimation(facing)
      : sheetColumnForAnimation(facing, animIx);
  return getMaskedSheetFrameTextureSync(sheetUrl, teamRgb, row, col);
}

function attachUnitHealthBar(holder: Container, u: UnitSnapshot, maxHp: number): void {
  const g = new Graphics();
  const ratio = Math.min(1, Math.max(0, u.health / Math.max(1, maxHp)));
  const pad = Math.max(1, Math.round(TILE_PX * 0.06));
  const barW = Math.floor(TILE_PX / 2);
  const barH = Math.max(2, Math.round(TILE_PX * 0.09));
  g.rect(0, pad, barW, barH);
  g.fill({ color: 0x0c0e12, alpha: 230 / 255 });
  const fillW = Math.max(0, Math.round(barW * ratio));
  let fillRgb = 0x37c85f;
  if (ratio < 1 / 3) {
    fillRgb = 0xe64141;
  } else if (ratio < 2 / 3) {
    fillRgb = 0xe6c837;
  }
  if (fillW > 0) {
    g.rect(0, pad, fillW, barH);
    g.fill({ color: fillRgb, alpha: 1 });
  }
  g.rect(0, pad, barW, barH);
  g.stroke({ width: 1, color: 0x000000, alpha: 140 / 255 });
  holder.addChild(g);
}

type UnitAnim =
  | {
    kind: "moveLinear";
    holder: Container;
    sprite: Sprite;
    sheetUrl: string;
    teamRgb: number;
    rowCount: number;
    facing: CardinalFacing;
    sx: number;
    sy: number;
    ex: number;
    ey: number;
    tMs: number;
    durationMs: number;
    animMs: number;
    onDone?: () => void;
  }
  | {
    kind: "movePath";
    holder: Container;
    sprite: Sprite;
    sheetUrl: string;
    teamRgb: number;
    rowCount: number;
    path: GridPoint[];
    segIndex: number;
    segTMs: number;
    segDurations: number[];
    animMs: number;
    followupAttack?: {
      atkSheetUrl: string;
      rowCount: number;
      sheetColumns: number;
      facing: CardinalFacing;
    };
    /** When set, play attack SFX when the chained attack strip starts after the path completes. */
    chainedAttackUnitType?: string;
    onDone?: () => void;
  }
  | {
    kind: "attack";
    holder: Container;
    sprite: Sprite;
    sheetUrl: string;
    teamRgb: number;
    rowCount: number;
    sheetColumns: number;
    facing: CardinalFacing;
    tMs: number;
    durationMs: number;
    animMs: number;
    onDone?: () => void;
  }
  | {
    kind: "spawnReveal";
    holder: Container;
    tMs: number;
    durationMs: number;
    animMs: number;
    onDone?: () => void;
  }
  | {
    kind: "gap";
    tMs: number;
    durationMs: number;
    animMs: number;
    onDone?: () => void;
  }
  | {
    kind: "capture";
    tMs: number;
    durationMs: number;
    animMs: number;
    onDone?: () => void;
  }
  | {
    kind: "extractResources";
    tMs: number;
    durationMs: number;
    animMs: number;
    onDone?: () => void;
  }
  | {
    kind: "startTurn";
    tMs: number;
    durationMs: number;
    animMs: number;
    onDone?: () => void;
  }
  | {
    kind: "cloaking";
    tMs: number;
    durationMs: number;
    animMs: number;
    onDone?: () => void;
  }
  | {
    kind: "explosion";
    holder: Container;
    g: Graphics;
    tMs: number;
    durationMs: number;
    animMs: number;
    maxR: number;
    onDone?: () => void;
  };

export function GameCanvas({ snapshot, className, interaction }: GameCanvasProps) {
  const mapSelectedGrid = useGameHudStore((s) => s.mapSelectedGrid);

  const hostRef = useRef<HTMLDivElement>(null);
  const snapshotRef = useRef(snapshot);
  const interactionRef = useRef(interaction);
  const lastHoverGridRef = useRef<{ x: number; y: number } | null>(null);
  const engineRef = useRef<{
    rebuild: (snap: MatchSnapshot | null) => Promise<void>;
    repaintCommandOverlays: (snap: MatchSnapshot) => void;
  } | null>(null);

  const priorUnitStateRef = useRef(new Map<string, PriorUnitState>());

  /** When map size is unchanged, {@link rebuild} must not reset {@code world.position} (pan) or users lose camera after hover rebuild. */
  const viewMapKeyRef = useRef<string | null>(null);

  const unitAnimsRef = useRef<UnitAnim[]>([]);

  useEffect(() => {
    snapshotRef.current = snapshot;
  }, [snapshot]);

  useEffect(() => {
    interactionRef.current = interaction;
  }, [interaction]);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;

    const unitAnimList = unitAnimsRef.current;

    const app = new Application();
    const world = new Container();
    let destroyed = false;
    let panDrag: {
      start: { x: number; y: number };
      world: { x: number; y: number };
    } | null = null;
    let rmbPanCandidate: {
      start: { x: number; y: number };
      world: { x: number; y: number };
    } | null = null;
    let pointerDown: { x: number; y: number; button: number } | null = null;
    let scale = 1;
    let placeholder: Text | null = null;

    const drawTileTint = (
      gr: Graphics,
      gx: number,
      gy: number,
      color: number,
      alpha: number
    ) => {
      const px = gx * TILE_PX;
      const py = gy * TILE_PX;
      gr.rect(px, py, TILE_PX, TILE_PX);
      gr.fill({ color, alpha });
    };

    const drawSelectOutline = (g: Graphics, gx: number, gy: number) => {
      g.clear();
      const px = gx * TILE_PX;
      const py = gy * TILE_PX;
      g.rect(px + 1, py + 1, TILE_PX - 2, TILE_PX - 2);
      g.stroke({ width: 2, color: 0xfff56e, alpha: 0.86 });
      g.rect(px + 2, py + 2, TILE_PX - 4, TILE_PX - 4);
      g.stroke({ width: 2, color: 0xffb400, alpha: 0.86 });
    };

    const drawOreMarker = (g: Graphics, gx: number, gy: number) => {
      const r = Math.max(4, TILE_PX / 6);
      const px = gx * TILE_PX + TILE_PX - r - 3;
      const py = gy * TILE_PX + TILE_PX - r - 3;
      g.ellipse(px + r / 2, py + r / 2, r / 2, r / 2);
      g.fill({ color: 0xffc83c, alpha: 200 / 255 });
      g.setStrokeStyle({ width: 1, color: 0x5a3c0a, alpha: 180 / 255 });
      g.stroke();
    };

    type SequentialRunCtx = {
      items: OpponentQueuedAction[];
      snap: MatchSnapshot;
      holders: Map<string, SequentialHolderInfo>;
    };
    let sequentialCtx: SequentialRunCtx | null = null;
    let sequentialFollowHolder: Container | null = null;

    const centerCameraOnHolder = (holder: Container): void => {
      if (!app.renderer) {
        return;
      }
      const cx = holder.x + TILE_PX * 0.5;
      const cy = holder.y + TILE_PX * 0.5;
      world.position.x = app.renderer.width / 2 - (cx - world.pivot.x) * scale;
      world.position.y = app.renderer.height / 2 - (cy - world.pivot.y) * scale;
      world.scale.set(scale);
    };

    function runSequentialOpponentSlot(slotIndex: number): void {
      const ctx = sequentialCtx;
      if (!ctx) {
        return;
      }
      if (slotIndex >= ctx.items.length) {
        const full = new Map<string, PriorUnitState>();
        for (const u of ctx.snap.units) {
          full.set(u.id, { x: u.x, y: u.y, health: u.health, hasMoved: u.hasMoved });
        }
        priorUnitStateRef.current = full;
        sequentialFollowHolder = null;
        sequentialCtx = null;
        return;
      }

      const item = ctx.items[slotIndex]!;
      const prevItem = slotIndex > 0 ? ctx.items[slotIndex - 1]! : null;
      const gapMs = sequentialGapMsBetween(prevItem, item);
      const kickBody = (): void => {
        runSequentialOpponentSlotBody(slotIndex);
      };
      if (gapMs > 0) {
        unitAnimList.push({
          kind: "gap",
          tMs: 0,
          durationMs: gapMs,
          animMs: 0,
          onDone: kickBody,
        });
      } else {
        kickBody();
      }
    }

    function runSequentialOpponentSlotBody(slotIndex: number): void {
      const ctx = sequentialCtx;
      if (!ctx) {
        return;
      }

      const item = ctx.items[slotIndex]!;
      const info = ctx.holders.get(item.unitId);
      const patchPriorAndContinue = (): void => {
        if (item.kind === "move") {
          priorUnitStateRef.current.set(item.unitId, {
            x: item.to.x,
            y: item.to.y,
            health: item.u.health,
            hasMoved: item.u.hasMoved,
          });
        } else {
          priorUnitStateRef.current.set(item.unitId, {
            x: item.u.x,
            y: item.u.y,
            health: item.u.health,
            hasMoved: item.u.hasMoved,
          });
        }
        runSequentialOpponentSlot(slotIndex + 1);
      };

      if (!info) {
        patchPriorAndContinue();
        return;
      }

      const onAdvance = patchPriorAndContinue;

      if (item.kind === "spawn") {
        info.holder.alpha = 0;
        sequentialFollowHolder = info.holder;
        centerCameraOnHolder(info.holder);
        playBuildSfx();
        unitAnimList.push({
          kind: "spawnReveal",
          holder: info.holder,
          tMs: 0,
          durationMs: 220,
          animMs: 0,
          onDone: onAdvance,
        });
        return;
      }

      if (item.kind === "attack") {
        info.holder.x = item.u.x * TILE_PX;
        info.holder.y = item.u.y * TILE_PX;
        sequentialFollowHolder = info.holder;
        centerCameraOnHolder(info.holder);
        const sa = info.standaloneAttack;
        if (sa == null) {
          patchPriorAndContinue();
          return;
        }
        playAttackSfx(item.u.unitType);
        unitAnimList.push({
          kind: "attack",
          holder: info.holder,
          sprite: info.sprite,
          sheetUrl: sa.atkSheetUrl,
          teamRgb: info.teamRgb,
          rowCount: sa.rowCount,
          sheetColumns: sa.sheetColumns,
          facing: sa.facing,
          tMs: 0,
          durationMs: 520,
          animMs: 0,
          onDone: onAdvance,
        });
        return;
      }

      const { from, to, u } = item;
      const displayPathCandidates = buildTilewiseMovePath(ctx.snap, u, from, to);
      const displayPath = displayPathCandidates.length >= 2 ? displayPathCandidates : null;

      if (info.layout !== null && displayPath !== null) {
        const pathValid = displayPath;
        const start = pathValid[0]!;
        info.holder.x = start.x * TILE_PX;
        info.holder.y = start.y * TILE_PX;
        sequentialFollowHolder = info.holder;
        centerCameraOnHolder(info.holder);
        const segDurations: number[] = [];
        for (let pi = 0; pi < pathValid.length - 1; pi++) {
          const a = pathValid[pi]!;
          const b = pathValid[pi + 1]!;
          const man = Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
          segDurations.push(Math.min(420, 75 + man * 88));
        }
        if (info.stats !== null) {
          playMovementSfx(info.stats.unitType);
        }
        const fu = info.followupAttack;
        unitAnimList.push({
          kind: "movePath",
          holder: info.holder,
          sprite: info.sprite,
          sheetUrl: info.sheetUrl,
          teamRgb: info.teamRgb,
          rowCount: info.layout.rows,
          path: pathValid,
          segIndex: 0,
          segTMs: 0,
          segDurations,
          animMs: 0,
          followupAttack: fu ?? undefined,
          chainedAttackUnitType: fu != null ? u.unitType : undefined,
          onDone: onAdvance,
        });
        return;
      }

      const facingMv = facingFromGridStep(from, to);
      info.holder.x = from.x * TILE_PX;
      info.holder.y = from.y * TILE_PX;
      sequentialFollowHolder = info.holder;
      centerCameraOnHolder(info.holder);
      const dist = Math.abs(to.x - from.x) + Math.abs(to.y - from.y);
      if (info.stats !== null) {
        playMovementSfx(info.stats.movementKind);
        if (info.stats.unitType === "Stealth") {
          playTankCloakingSfx();
        }
      }
      const fuLin = info.followupAttack;
      const afterLinear = (): void => {
        if (fuLin != null) {
          playAttackSfx(u.unitType);
          unitAnimList.push({
            kind: "attack",
            holder: info.holder,
            sprite: info.sprite,
            sheetUrl: fuLin.atkSheetUrl,
            teamRgb: info.teamRgb,
            rowCount: fuLin.rowCount,
            sheetColumns: fuLin.sheetColumns,
            facing: fuLin.facing,
            tMs: 0,
            durationMs: 520,
            animMs: 0,
            onDone: onAdvance,
          });
        } else {
          onAdvance();
        }
      };
      unitAnimList.push({
        kind: "moveLinear",
        holder: info.holder,
        sprite: info.sprite,
        sheetUrl: info.sheetUrl,
        teamRgb: info.teamRgb,
        rowCount: info.layout?.rows ?? 1,
        facing: facingMv,
        sx: from.x * TILE_PX,
        sy: from.y * TILE_PX,
        ex: to.x * TILE_PX,
        ey: to.y * TILE_PX,
        tMs: 0,
        durationMs: Math.min(900, 120 + dist * 85),
        animMs: 0,
        onDone: afterLinear,
      });
    }

    let tickerAdded = false;
    const addTicker = () => {
      if (tickerAdded || !app.ticker) {
        return;
      }
      tickerAdded = true;
      app.ticker.add(() => {
        const dt = Math.min(40, ((app.ticker as { deltaMS?: number }).deltaMS ?? 16.6667));
        if (sequentialFollowHolder !== null) {
          centerCameraOnHolder(sequentialFollowHolder);
        }
        const list = unitAnimList;
        for (let i = list.length - 1; i >= 0; i--) {
          const tw = list[i];
          if (!tw) continue;
          if (tw.kind !== "spawnReveal" && tw.kind !== "gap" && tw.kind !== "explosion") {
            tw.animMs += dt;
          }
          if (tw.kind === "moveLinear") {
            tw.tMs += dt;
            const p = clamp01(tw.tMs / tw.durationMs);
            tw.holder.x = tw.sx + (tw.ex - tw.sx) * p;
            tw.holder.y = tw.sy + (tw.ey - tw.sy) * p;
            const nt = orientationSheetTexture(
              tw.sheetUrl,
              tw.teamRgb,
              tw.rowCount,
              tw.facing,
              tw.animMs,
              WALK_FRAME_MS
            );
            if (nt) {
              layoutUnitSpriteInTileCell(tw.sprite, nt);
            }
            if (tw.tMs >= tw.durationMs) {
              tw.holder.x = tw.ex;
              tw.holder.y = tw.ey;
              const cb = tw.onDone;
              list.splice(i, 1);
              cb?.();
            }
          } else if (tw.kind === "movePath") {
            tw.segTMs += dt;
            const from = tw.path[tw.segIndex];
            const to = tw.path[tw.segIndex + 1];
            if (!from || !to) {
              const cb = tw.onDone;
              list.splice(i, 1);
              cb?.();
              continue;
            }
            const segDur = tw.segDurations[tw.segIndex] ?? 140;
            const t = clamp01(tw.segTMs / segDur);
            tw.holder.x = from.x * TILE_PX + (to.x - from.x) * TILE_PX * t;
            tw.holder.y = from.y * TILE_PX + (to.y - from.y) * TILE_PX * t;
            const facingSeg = facingFromGridStep(from, to);
            const nt = orientationSheetTexture(
              tw.sheetUrl,
              tw.teamRgb,
              tw.rowCount,
              facingSeg,
              tw.animMs,
              WALK_FRAME_MS
            );
            if (nt) {
              layoutUnitSpriteInTileCell(tw.sprite, nt);
            }
            if (tw.segTMs >= segDur) {
              const carry = tw.segTMs - segDur;
              tw.segIndex += 1;
              tw.segTMs = carry;
              if (tw.segIndex >= tw.path.length - 1) {
                const end = tw.path[tw.path.length - 1]!;
                tw.holder.x = end.x * TILE_PX;
                tw.holder.y = end.y * TILE_PX;
                const fu = tw.followupAttack;
                if (fu != null) {
                  if (tw.chainedAttackUnitType != null) {
                    playAttackSfx(tw.chainedAttackUnitType);
                  }
                  list[i] = {
                    kind: "attack",
                    holder: tw.holder,
                    sprite: tw.sprite,
                    sheetUrl: fu.atkSheetUrl,
                    teamRgb: tw.teamRgb,
                    rowCount: fu.rowCount,
                    sheetColumns: fu.sheetColumns,
                    facing: fu.facing,
                    tMs: 0,
                    durationMs: 520,
                    animMs: 0,
                    onDone: tw.onDone,
                  };
                } else {
                  const cb = tw.onDone;
                  list.splice(i, 1);
                  cb?.();
                }
              }
            }
          } else if (tw.kind === "gap") {
            tw.tMs += dt;
            if (tw.tMs >= tw.durationMs) {
              const cb = tw.onDone;
              list.splice(i, 1);
              cb?.();
            }
          } else if (tw.kind === "spawnReveal") {
            tw.tMs += dt;
            const pa = clamp01(tw.tMs / tw.durationMs);
            tw.holder.alpha = pa;
            tw.animMs += dt;
            if (tw.tMs >= tw.durationMs) {
              tw.holder.alpha = 1;
              const cb = tw.onDone;
              list.splice(i, 1);
              cb?.();
            }
          } else if (tw.kind === "explosion") {
            tw.tMs += dt;
            const p = clamp01(tw.tMs / tw.durationMs);
            const g = tw.g;
            const cx = TILE_PX * 0.5;
            const cy = TILE_PX * 0.52;
            const fade = 1 - p;
            const rOuter = tw.maxR * (0.12 + 0.88 * Math.pow(p, 0.5));
            const rCore = tw.maxR * (0.06 + 0.42 * p);
            g.clear();
            g.circle(cx, cy, rOuter);
            g.fill({ color: 0xff4400, alpha: fade * 0.55 });
            g.circle(cx, cy, rCore);
            g.fill({ color: 0xffee99, alpha: fade * 0.42 });
            g.circle(cx, cy, rCore * 0.35);
            g.fill({ color: 0xffffff, alpha: fade * 0.25 });
            if (tw.tMs >= tw.durationMs) {
              tw.holder.removeFromParent();
              const cb = tw.onDone;
              list.splice(i, 1);
              cb?.();
            }
          } else if (tw.kind === "attack") {
            tw.tMs += dt;
            const nt = orientationSheetTexture(
              tw.sheetUrl,
              tw.teamRgb,
              tw.rowCount,
              tw.facing,
              tw.animMs,
              ATTACK_FRAME_MS,
              tw.sheetColumns
            );
            if (nt) {
              layoutUnitSpriteInTileCell(tw.sprite, nt);
            }
            if (tw.tMs >= tw.durationMs) {
              const cb = tw.onDone;
              list.splice(i, 1);
              cb?.();
            }
          }
        }
      });
    };

    let overlayLayerRoots: { low: Container; high: Container } | null = null;

    function parseTk(k: string): { x: number; y: number } | null {
      const parts = k.split(",");
      const x = Number(parts[0]);
      const y = Number(parts[1]);
      if (parts.length !== 2 || Number.isNaN(x) || Number.isNaN(y)) {
        return null;
      }
      return { x, y };
    }

    const finalizeViewAfterRebuild = (
      snap: MatchSnapshot,
      mapW: number,
      mapH: number,
      /** When sequencing opponent moves, {@link priorUnitStateRef} advances per step — skip bulk reset here. */
      holdPriorPositions = false
    ) => {
      if (!holdPriorPositions) {
        const nextPos = new Map<string, PriorUnitState>();
        for (const u of snap.units) {
          nextPos.set(u.id, { x: u.x, y: u.y, health: u.health, hasMoved: u.hasMoved });
        }
        priorUnitStateRef.current = nextPos;
      }
      const mapKey = `${snap.matchId}|${mapW}x${mapH}`;
      world.pivot.set((mapW * TILE_PX) / 2, (mapH * TILE_PX) / 2);
      if (viewMapKeyRef.current !== mapKey) {
        viewMapKeyRef.current = mapKey;
        world.position.set(app.renderer.width / 2, app.renderer.height / 2);
      }
      world.scale.set(scale);
    };

    const repaintCommandOverlays = (snap: MatchSnapshot) => {
      if (!app.renderer) return;
      const roots = overlayLayerRoots;
      if (!roots) return;
      const lowOverlayRoot = roots.low;
      const highOverlayRoot = roots.high;
      const cfg = interactionRef.current;
      const hud = useGameHudStore.getState();
      const activeSeat = snap.activePlayerIndex;
      const yourCommandTurn = cfg != null && cfg.yourSeatIndex === activeSeat && !snap.matchFinished;
      const yellowSelGrid = hud.mapSelectedGrid;
      const selectedUnit = resolveYellowSelectionUnit(snap, yellowSelGrid);

      lowOverlayRoot.removeChildren();
      highOverlayRoot.removeChildren();

      if (snap.matchFinished) {
        if (yellowSelGrid) {
          const gYellow = new Graphics();
          drawSelectOutline(gYellow, yellowSelGrid.x, yellowSelGrid.y);
          highOverlayRoot.addChild(gYellow);
        }
        lastHoverGridRef.current = null;
        return;
      }

      if (
        yellowSelGrid &&
        selectedUnit &&
        selectedUnit.ownerSeatIndex === activeSeat &&
        yourCommandTurn &&
        !selectedUnit.hasMoved
      ) {
        const selId = selectedUnit.id;

        const gReach = new Graphics();
        const reachKeys = reachableEndTiles(snap, selId);
        for (const k of reachKeys) {
          const p = parseTk(k);
          if (p) drawTileTint(gReach, p.x, p.y, OVERLAY_WHITE.color, OVERLAY_WHITE.alpha);
        }
        lowOverlayRoot.addChild(gReach);

        const gAtkPrev = new Graphics();
        for (const k of computeAttackRangePreviewCells(snap, activeSeat, selId)) {
          const p = parseTk(k);
          if (p) drawTileTint(gAtkPrev, p.x, p.y, OVERLAY_ATTACK_PREVIEW.color, OVERLAY_ATTACK_PREVIEW.alpha);
        }
        lowOverlayRoot.addChild(gAtkPrev);

        const gAtk = new Graphics();
        for (const k of computeAttackableEnemyCells(snap, activeSeat, selId)) {
          const p = parseTk(k);
          if (p) drawTileTint(gAtk, p.x, p.y, OVERLAY_ATTACKABLE.color, OVERLAY_ATTACKABLE.alpha);
        }
        lowOverlayRoot.addChild(gAtk);
      }

      const pathHud = hud.movementPath;
      if (
        cfg &&
        yourCommandTurn &&
        selectedUnit &&
        !selectedUnit.hasMoved &&
        selectedUnit.ownerSeatIndex === cfg.yourSeatIndex &&
        pathHud.length >= 2
      ) {
        const gPath = new Graphics();
        const strokeW = Math.max(2.2, TILE_PX / 12);
        const pts = pathHud;
        const p0 = pts[0]!;
        let cx = p0.x * TILE_PX + TILE_PX * 0.5;
        let cy = p0.y * TILE_PX + TILE_PX * 0.5;
        gPath.moveTo(cx, cy);
        for (let i = 1; i < pts.length; i++) {
          const pi = pts[i]!;
          cx = pi.x * TILE_PX + TILE_PX * 0.5;
          cy = pi.y * TILE_PX + TILE_PX * 0.5;
          gPath.lineTo(cx, cy);
        }
        gPath.stroke({
          width: strokeW,
          color: 0xffd250,
          alpha: 220 / 255,
          cap: "round",
          join: "round",
        });

        const hoverGrid = lastHoverGridRef.current;
        const marker = shouldDrawFriendlyOccupiedHoverMarker(snap, cfg, selectedUnit, hoverGrid);
        lowOverlayRoot.addChild(gPath);
        if (marker && hoverGrid) {
          const gX = new Graphics();
          drawFriendlyHoverX(gX, hoverGrid.x, hoverGrid.y, TILE_PX);
          lowOverlayRoot.addChild(gX);
        } else {
          const prev = pts[pts.length - 2]!;
          const last = pts[pts.length - 1]!;
          const gArr = new Graphics();
          drawArrowHead(gArr, prev, last, TILE_PX);
          lowOverlayRoot.addChild(gArr);
        }
      }

      for (const k of computeIdleCrosshairCells(snap, activeSeat)) {
        const p = parseTk(k);
        if (!p) continue;
        const gIdle = new Container();
        drawSwingIdleCrosshair(gIdle, p.x, p.y, TILE_PX);
        highOverlayRoot.addChild(gIdle);
      }

      if (yellowSelGrid) {
        const gYellow = new Graphics();
        drawSelectOutline(gYellow, yellowSelGrid.x, yellowSelGrid.y);
        highOverlayRoot.addChild(gYellow);
      }
    };

    const rebuild = async (snap: MatchSnapshot | null) => {
      if (!app.renderer) return;

      unitAnimList.length = 0;
      sequentialCtx = null;

      if (placeholder) {
        app.stage.removeChild(placeholder);
        placeholder.destroy();
        placeholder = null;
      }

      world.removeChildren();
      overlayLayerRoots = null;

      if (!snap) {
        world.visible = false;
        viewMapKeyRef.current = null;
        const hint = new Text({
          text: "Connect to the game server to load the map.",
          style: {
            fill: 0x888888,
            fontSize: 14,
            fontFamily: "system-ui, sans-serif",
          },
        });
        hint.anchor.set(0.5);
        hint.x = app.renderer.width / 2;
        hint.y = app.renderer.height / 2;
        placeholder = hint;
        app.stage.addChild(hint);
        priorUnitStateRef.current.clear();
        return;
      }

      world.visible = true;

      const tilesRoot = new Container();
      const structsRoot = new Container();
      const oreRoot = new Container();
      const lowOverlayRoot = new Container();
      const unitsRoot = new Container();
      const highOverlayRoot = new Container();
      world.addChild(tilesRoot);
      world.addChild(structsRoot);
      world.addChild(oreRoot);
      world.addChild(lowOverlayRoot);
      world.addChild(unitsRoot);
      world.addChild(highOverlayRoot);
      overlayLayerRoots = { low: lowOverlayRoot, high: highOverlayRoot };

      const mapW = snap.width;
      const mapH = snap.height;

      const prevPos = priorUnitStateRef.current;

      const liveUnitIds = new Set(snap.units.map((u) => u.id));
      for (const [goneId, p] of prevPos) {
        if (!liveUnitIds.has(goneId)) {
          playExplosionSfx();
          const exh = new Container();
          exh.x = p.x * TILE_PX;
          exh.y = p.y * TILE_PX;
          const eg = new Graphics();
          exh.addChild(eg);
          highOverlayRoot.addChild(exh);
          unitAnimList.push({
            kind: "explosion",
            holder: exh,
            g: eg,
            tMs: 0,
            durationMs: 640,
            animMs: 0,
            maxR: TILE_PX * 0.58,
          });
        }
      }

      for (let y = 0; y < mapH; y++) {
        const row = snap.tiles[y];
        if (!row) continue;
        for (let x = 0; x < mapW; x++) {
          const tile = row[x];
          const u = terrainTextureUrl(tile.terrain);
          const fallRgb = terrainFallbackRgb(tile.terrain);
          const tex = await getTexture(u, () => loadOrFallback(u, fallRgb, TILE_PX, TILE_PX));
          if (!app.renderer) return;
          const drawH = terrainScaledDrawHeight(tex);
          if (drawH < TILE_PX) {
            const gap = TILE_PX - drawH;
            const topFill = new Graphics();
            topFill.rect(x * TILE_PX, y * TILE_PX, TILE_PX, gap);
            topFill.fill({ color: fallRgb, alpha: 1 });
            tilesRoot.addChild(topFill);
          }
          const s = new Sprite(tex);
          layoutTerrainSprite(s, tex, x, y);
          tilesRoot.addChild(s);

          if (tile.oreDeposit) {
            const og = new Graphics();
            drawOreMarker(og, x, y);
            oreRoot.addChild(og);
          }

          if (tile.structure) {
            const su = structureTextureUrl(tile.structure);
            if (su) {
              const rgb = teamRgbFromTeamId(tile.structureTeam);
              const uncUrl = uncolouredStructureTextureUrl(tile.structure);
              const maskedSt = await getUncolouredFullImageTeamTexture(uncUrl, rgb);
              let st = maskedSt;
              let stTint = 0xffffff;
              if (!st) {
                st = await getTexture(su, () => loadOrFallback(su, DEFAULT_STRUCTURE_COLOR, TILE_PX, TILE_PX));
                stTint = rgb;
              }
              if (!app.renderer) return;
              const ss = new Sprite(st);
              layoutStructureSprite(ss, st, x, y);
              ss.tint = stTint;
              structsRoot.addChild(ss);
            }
          }
        }
      }

      const opponentSeqPlan = buildOpponentSequentialPlan(snap, interactionRef.current ?? null, prevPos);
      const seqByUnitId = opponentSeqPlan
        ? new Map(opponentSeqPlan.map((it) => [it.unitId, it] as const))
        : null;
      const seqHoldersAccum = new Map<string, SequentialHolderInfo>();

      const hud = useGameHudStore.getState();

      for (const u of snap.units) {
        const rgb = unitRgbFromOwnerSeat(u.ownerSeatIndex);
        const stats = getUnitTypeStats(u.unitType);
        const maxHp = stats?.startingHealth ?? Math.max(1, u.health);
        const { url: sheetUrl, layout } = await pickUnitSheetUrl(u.unitType, "move");

        let tex: Texture | null = null;
        let tint = 0xffffff;
        if (layout != null) {
          tex = getMaskedSheetFrameTextureSync(
            sheetUrl,
            rgb,
            sheetRowForAnimation(0, layout.rows),
            sheetColumnForAnimation(parseFacing(u.facing), 0)
          );
        }
        if (!tex) {
          const uncUrl = uncolouredUnitTextureUrl(u.unitType);
          const masked = await getUncolouredEastFrameTeamTexture(uncUrl, rgb);
          if (masked) {
            tex = masked;
          } else {
            const legacyPath = unitTextureUrl(u.unitType);
            tex = await getTexture(legacyPath, () =>
              loadOrFallback(legacyPath, DEFAULT_UNIT_COLOR, TILE_PX, TILE_PX)
            );
            tint = rgb;
          }
        }
        if (!app.renderer) return;

        const seqEntry = seqByUnitId?.get(u.id);
        const holder = new Container();
        if (seqEntry) {
          if (seqEntry.kind === "spawn") {
            holder.x = seqEntry.u.x * TILE_PX;
            holder.y = seqEntry.u.y * TILE_PX;
            holder.alpha = 0;
          } else if (seqEntry.kind === "move") {
            holder.x = seqEntry.from.x * TILE_PX;
            holder.y = seqEntry.from.y * TILE_PX;
            holder.alpha = u.cloaked ? 0.45 : 1;
          } else {
            holder.x = seqEntry.u.x * TILE_PX;
            holder.y = seqEntry.u.y * TILE_PX;
            holder.alpha = u.cloaked ? 0.45 : 1;
          }
        } else {
          holder.x = u.x * TILE_PX;
          holder.y = u.y * TILE_PX;
          holder.alpha = u.cloaked ? 0.45 : 1;
        }

        const sprite = new Sprite(tex);
        layoutUnitSpriteInTileCell(sprite, tex);
        holder.addChild(sprite);
        attachUnitHealthBar(holder, u, maxHp);
        sprite.tint = tint;
        unitsRoot.addChild(holder);

        const pu = prevPos.get(u.id);
        const moved =
          pu !== undefined && pu.x !== undefined && pu.y !== undefined && (pu.x !== u.x || pu.y !== u.y);

        if (seqEntry) {
          let followupAttack: SequentialHolderInfo["followupAttack"];
          let standaloneAttack: SequentialHolderInfo["standaloneAttack"];
          if (seqEntry.kind === "move" && seqEntry.postMoveAttackToward && layout != null) {
            const atkPick = await pickUnitSheetUrl(u.unitType, "attack");
            if (atkPick.layout !== null) {
              const endTile = { x: u.x, y: u.y };
              followupAttack = {
                atkSheetUrl: atkPick.url,
                rowCount: atkPick.layout.rows,
                sheetColumns: atkPick.layout.columns,
                facing: facingFromGridStep(endTile, seqEntry.postMoveAttackToward),
              };
            }
          }
          if (seqEntry.kind === "attack") {
            const atkPick = await pickUnitSheetUrl(u.unitType, "attack");
            if (atkPick.layout !== null) {
              standaloneAttack = {
                atkSheetUrl: atkPick.url,
                rowCount: atkPick.layout.rows,
                sheetColumns: atkPick.layout.columns,
                facing: facingFromGridStep({ x: u.x, y: u.y }, seqEntry.toward),
              };
            }
          }
          seqHoldersAccum.set(u.id, {
            holder,
            sprite,
            teamRgb: rgb,
            sheetUrl,
            layout,
            stats,
            followupAttack,
            standaloneAttack,
          });
        }

        if (!seqEntry) {
          if (moved && stats !== null) {
            playMovementSfx(stats.movementKind);
          }

          let pathFromHud: GridPoint[] | null = null;
          const pendingMove = hud.pendingMovePath;
          let chainAttackToward: { x: number; y: number } | undefined;
          if (moved && pendingMove?.unitId === u.id && layout != null) {
            const P = pendingMove.path;
            chainAttackToward = pendingMove.chainAttackToward;
            if (
              P.length >= 2 &&
              pu &&
              P[0]!.x === pu.x &&
              P[0]!.y === pu.y &&
              P[P.length - 1]!.x === u.x &&
              P[P.length - 1]!.y === u.y
            ) {
              pathFromHud = P.map((p) => ({ x: p.x, y: p.y }));
            }
          }

          if (moved && pendingMove?.unitId === u.id) {
            hud.setPendingMovePath(null);
          }

          let displayPath: GridPoint[] | null = null;
          if (moved && pu !== undefined) {
            if (pathFromHud !== null && pathFromHud.length >= 2) {
              displayPath = pathFromHud;
            } else {
              const tilewise = buildTilewiseMovePath(snap, u, { x: pu.x, y: pu.y }, { x: u.x, y: u.y });
              if (tilewise.length >= 2) {
                displayPath = tilewise;
              }
            }
          }

          if (moved && layout !== null && displayPath !== null && displayPath.length >= 2) {
            const pathValid = displayPath;
            const start = pathValid[0]!;
            holder.x = start.x * TILE_PX;
            holder.y = start.y * TILE_PX;
            const segDurations: number[] = [];
            for (let pi = 0; pi < pathValid.length - 1; pi++) {
              const a = pathValid[pi]!;
              const b = pathValid[pi + 1]!;
              const man = Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
              segDurations.push(Math.min(420, 75 + man * 88));
            }
            let followupAttack:
              | {
                atkSheetUrl: string;
                rowCount: number;
                sheetColumns: number;
                facing: CardinalFacing;
              }
              | undefined;
            if (chainAttackToward !== undefined) {
              const endTile = pathValid[pathValid.length - 1]!;
              const facingAtk = facingFromGridStep(endTile, chainAttackToward);
              const atkPick = await pickUnitSheetUrl(u.unitType, "attack");
              if (atkPick.layout !== null) {
                followupAttack = {
                  atkSheetUrl: atkPick.url,
                  rowCount: atkPick.layout.rows,
                  sheetColumns: atkPick.layout.columns,
                  facing: facingAtk,
                };
              }
            }
            unitAnimList.push({
              kind: "movePath",
              holder,
              sprite,
              sheetUrl,
              teamRgb: rgb,
              rowCount: layout.rows,
              path: pathValid,
              segIndex: 0,
              segTMs: 0,
              segDurations,
              animMs: 0,
              followupAttack,
              chainedAttackUnitType: followupAttack != null ? u.unitType : undefined,
            });
          } else if (moved && pu !== undefined) {
            const facingMv = facingFromGridStep({ x: pu.x, y: pu.y }, { x: u.x, y: u.y });
            holder.x = pu.x * TILE_PX;
            holder.y = pu.y * TILE_PX;
            const sx = pu.x * TILE_PX;
            const sy = pu.y * TILE_PX;
            const ex = u.x * TILE_PX;
            const ey = u.y * TILE_PX;
            const dist = Math.abs(u.x - pu.x) + Math.abs(u.y - pu.y);
            unitAnimList.push({
              kind: "moveLinear",
              holder,
              sprite,
              sheetUrl,
              teamRgb: rgb,
              rowCount: layout?.rows ?? 1,
              facing: facingMv,
              sx,
              sy,
              ex,
              ey,
              tMs: 0,
              durationMs: Math.min(900, 120 + dist * 85),
              animMs: 0,
            });
          } else if (hud.pendingAttackVisual?.unitId === u.id) {
            const vis = hud.pendingAttackVisual;
            hud.setPendingAttackVisual(null);
            const atkPick = await pickUnitSheetUrl(u.unitType, "attack");
            if (atkPick.layout !== null && vis !== null) {
              playAttackSfx(u.unitType);
              unitAnimList.push({
                kind: "attack",
                holder,
                sprite,
                sheetUrl: atkPick.url,
                teamRgb: rgb,
                rowCount: atkPick.layout.rows,
                sheetColumns: atkPick.layout.columns,
                facing: parseFacing(vis.facing),
                tMs: 0,
                durationMs: 520,
                animMs: 0,
              });
            }
          }
        }
      }

      if (opponentSeqPlan !== null && opponentSeqPlan.length > 0) {
        sequentialCtx = { items: opponentSeqPlan, snap, holders: seqHoldersAccum };
        runSequentialOpponentSlot(0);
      }

      repaintCommandOverlays(snap);
      finalizeViewAfterRebuild(snap, mapW, mapH, opponentSeqPlan !== null);
    };

    const onWheel = (ev: WheelEvent) => {
      ev.preventDefault();
      const factor = ev.deltaY > 0 ? 0.92 : 1.09;
      scale = Math.min(3, Math.max(0.25, scale * factor));
      world.scale.set(scale);
    };

    const onContextMenu = (ev: Event) => ev.preventDefault();

    const isPanButton = (e: FederatedPointerEvent) => e.shiftKey || e.button === 1 || e.button === 2;

    const onDown = (e: FederatedPointerEvent) => {
      if (!world.visible) return;
      pointerDown = { x: e.global.x, y: e.global.y, button: e.button };
      rmbPanCandidate = null;
      if (isPanButton(e)) {
        const pureRmb = e.button === 2 && !e.shiftKey;
        if (pureRmb) {
          rmbPanCandidate = {
            start: { x: e.global.x, y: e.global.y },
            world: { x: world.position.x, y: world.position.y },
          };
        } else {
          panDrag = {
            start: { x: e.global.x, y: e.global.y },
            world: { x: world.position.x, y: world.position.y },
          };
        }
      }
    };

    const promoteRmbCandidateToPanIfNeeded = (e: FederatedPointerEvent): void => {
      if (!rmbPanCandidate || panDrag) return;
      const dx = e.global.x - rmbPanCandidate.start.x;
      const dy = e.global.y - rmbPanCandidate.start.y;
      if (Math.hypot(dx, dy) <= 8) return;
      panDrag = {
        start: { x: rmbPanCandidate.start.x, y: rmbPanCandidate.start.y },
        world: { x: rmbPanCandidate.world.x, y: rmbPanCandidate.world.y },
      };
      rmbPanCandidate = null;
    };

    const onHoverMove = (e: FederatedPointerEvent) => {
      if (!world.visible || panDrag || rmbPanCandidate) return;
      const snap = snapshotRef.current;
      if (!snap || snap.matchFinished) {
        return;
      }
      const cfg = interactionRef.current;
      const hud = useGameHudStore.getState();
      const local = world.toLocal(e.global);
      const hx = Math.floor(local.x / TILE_PX);
      const hy = Math.floor(local.y / TILE_PX);
      if (hx < 0 || hy < 0 || hx >= snap.width || hy >= snap.height) {
        lastHoverGridRef.current = null;
        engineRef.current?.repaintCommandOverlays(snap);
        return;
      }

      lastHoverGridRef.current = { x: hx, y: hy };

      const selected = resolveYellowSelectionUnit(snap, hud.mapSelectedGrid);
      const canHoverPath =
        cfg != null &&
        cfg.yourSeatIndex === snap.activePlayerIndex &&
        selected != null &&
        !selected.hasMoved &&
        selected.ownerSeatIndex === cfg.yourSeatIndex;

      if (!canHoverPath) {
        engineRef.current?.repaintCommandOverlays(snap);
        return;
      }

      const next = updateMovementPathFromHover(
        snap,
        cfg!.yourSeatIndex,
        selected!,
        [...hud.movementPath],
        { x: hx, y: hy }
      );

      if (!pathsEqual(next, hud.movementPath)) {
        hud.setMovementPath(next);
      }
      engineRef.current?.repaintCommandOverlays(snap);
    };

    const onUp = (e: FederatedPointerEvent) => {
      const wasPanning = panDrag != null;
      panDrag = null;
      rmbPanCandidate = null;
      const down = pointerDown;
      pointerDown = null;

      if (wasPanning || !world.visible || !down) {
        return;
      }

      if (down.button === 2) {
        const moved = Math.hypot(e.global.x - down.x, e.global.y - down.y);
        if (moved > 8) return;
        const snap = snapshotRef.current;
        const cfg = interactionRef.current;
        if (!snap || !cfg || snap.matchFinished) return;
        const local = world.toLocal(e.global);
        const gx = Math.floor(local.x / TILE_PX);
        const gy = Math.floor(local.y / TILE_PX);
        if (gx < 0 || gy < 0 || gx >= snap.width || gy >= snap.height) return;
        const occ = snap.units.filter((u) => u.health > 0 && u.x === gx && u.y === gy);
        const u = occ[0];
        if (
          u &&
          cfg.yourSeatIndex === snap.activePlayerIndex &&
          u.ownerSeatIndex === cfg.yourSeatIndex &&
          u.unitType === "Warmachine" &&
          !u.hasMoved
        ) {
          useGameHudStore.getState().closeContextMenu();
          const ne = e.nativeEvent as PointerEvent | undefined;
          const cx = typeof ne?.clientX === "number" ? ne.clientX : 0;
          const cy = typeof ne?.clientY === "number" ? ne.clientY : 0;
          useGameHudStore.getState().openContextMenu({ clientX: cx, clientY: cy, unitId: u.id });
        }
        return;
      }

      if (down.button !== 0) {
        return;
      }

      const moved = Math.hypot(e.global.x - down.x, e.global.y - down.y);
      if (moved > 6) {
        return;
      }

      const cfg = interactionRef.current;
      const snap = snapshotRef.current;
      const hudActions = useGameHudStore.getState();

      const local = world.toLocal(e.global);
      const gx = Math.floor(local.x / TILE_PX);
      const gy = Math.floor(local.y / TILE_PX);

      if (!snap || gx < 0 || gy < 0 || gx >= snap.width || gy >= snap.height) {
        return;
      }

      const finishInspect = () => {
        hudActions.setInspectGrid({ x: gx, y: gy });
        if (snap && cfg) {
          maybeOpenSwingFactoryModal(snap, cfg, gx, gy);
        }
      };

      if (snap.matchFinished) {
        return;
      }

      const clickedUnitsAt = snap.units.filter((u) => u.health > 0 && u.x === gx && u.y === gy);

      const yourCommandTurn =
        cfg != null && cfg.yourSeatIndex === snap.activePlayerIndex && !snap.matchFinished;

      const selGrid = hudActions.mapSelectedGrid;
      const selectedUnit: UnitSnapshot | null =
        selGrid != null ? resolveYellowSelectionUnit(snap, selGrid) : null;
      const commandingSelectedUnit =
        selectedUnit &&
        selGrid!.x === selectedUnit.x &&
        selGrid!.y === selectedUnit.y &&
        selectedUnit.health > 0 &&
        selectedUnit.ownerSeatIndex === snap.activePlayerIndex;

      /** Swing {@code GameMapPanel} selected-cell command branch — own active unit at cursor. */
      if (commandingSelectedUnit && selectedUnit && yourCommandTurn && selGrid != null) {
        const path = hudActions.movementPath;
        const enemyUnder = clickedUnitsAt.find((u) => u.ownerSeatIndex !== cfg!.yourSeatIndex);
        const moverStats = getUnitTypeStats(selectedUnit.unitType);

        if (
          enemyUnder !== undefined &&
          path.length >= 2 &&
          moverStats !== null &&
          moverStats.attackRange === 1 &&
          !selectedUnit.hasMoved
        ) {
          const pathStart = path[0]!;
          const pathEnd = path[path.length - 1]!;
          if (pathEnd.x !== pathStart.x || pathEnd.y !== pathStart.y) {
            const destToEnemy =
              Math.abs(pathEnd.x - enemyUnder.x) + Math.abs(pathEnd.y - enemyUnder.y);
            if (destToEnemy === 1 && isValidMovementPathSnapshot(snap, selectedUnit, path)) {
              finishInspect();
              playAttackSfx(selectedUnit.unitType);
              hudActions.setPendingMovePath({
                unitId: selectedUnit.id,
                path: path.map((p) => ({ x: p.x, y: p.y })),
                chainAttackToward: { x: enemyUnder.x, y: enemyUnder.y },
              });
              getMatchWebSocketClient().moveAndAttackUnit({
                matchId: cfg!.matchId,
                unitId: selectedUnit.id,
                pathIncludingStart: [...path],
                defenderUnitId: enemyUnder.id,
              });
              hudActions.resetMapCommandOverlay();
              hudActions.setInspectGrid({ x: selectedUnit.x, y: selectedUnit.y });
              void engineRef.current?.rebuild(snap);
              return;
            }
          }
        }

        const atkKeys = computeAttackableEnemyCells(snap, cfg!.yourSeatIndex, selectedUnit.id);
        if (enemyUnder !== undefined && atkKeys.has(xyKey(enemyUnder.x, enemyUnder.y))) {
          const canAtk = canClientPreviewAttack(snap, cfg!.yourSeatIndex, selectedUnit, enemyUnder);
          if (canAtk) {
            finishInspect();
            playAttackSfx(selectedUnit.unitType);
            hudActions.setPendingAttackVisual({
              unitId: selectedUnit.id,
              facing: facingFromGridStep(
                { x: selectedUnit.x, y: selectedUnit.y },
                { x: enemyUnder.x, y: enemyUnder.y }
              ),
            });
            getMatchWebSocketClient().attackUnit({
              matchId: cfg!.matchId,
              attackerUnitId: selectedUnit.id,
              defenderUnitId: enemyUnder.id,
            });
            hudActions.resetMapCommandOverlay();
            void engineRef.current?.rebuild(snap);
            return;
          }
        }

        const reach = reachableEndTiles(snap, selectedUnit.id);

        const canStillMove = !selectedUnit.hasMoved && cfg!.yourSeatIndex === snap.activePlayerIndex;
        if (canStillMove && reach.has(xyKey(gx, gy))) {
          const path = hudActions.movementPath;
          if (path.length > 1 && path[path.length - 1]?.x === gx && path[path.length - 1]?.y === gy) {
            if (isValidMovementPathSnapshot(snap, selectedUnit, path)) {
              finishInspect();
              hudActions.setPendingMovePath({
                unitId: selectedUnit.id,
                path: path.map((p) => ({ x: p.x, y: p.y })),
              });
              getMatchWebSocketClient().moveUnit({
                matchId: cfg!.matchId,
                unitId: selectedUnit.id,
                pathIncludingStart: [...path],
              });
              hudActions.resetMapCommandOverlay();
              hudActions.setInspectGrid({ x: selectedUnit.x, y: selectedUnit.y });
              void engineRef.current?.rebuild(snap);
              return;
            }
          }
          const pathTrunc = [...path];
          for (let i = 0; i < pathTrunc.length; i++) {
            const pi = pathTrunc[i]!;
            if (pi.x === gx && pi.y === gy) {
              while (pathTrunc.length > i + 1) {
                pathTrunc.pop();
              }
              hudActions.setMovementPath(pathTrunc);
              finishInspect();
              void engineRef.current?.rebuild(snap);
              return;
            }
          }
          const snapPath = shortestLegalPathIncludingStart(snap, selectedUnit, gx, gy);
          if (snapPath != null && snapPath.length >= 2) {
            hudActions.setMovementPath(snapPath);
            finishInspect();
            void engineRef.current?.rebuild(snap);
            return;
          }
        }
      }

      const pick = clickedUnitsAt[0];
      if (pick != null) {
        hudActions.setMapSelectedGrid({ x: gx, y: gy });
        if (yourCommandTurn && pick.ownerSeatIndex === snap.activePlayerIndex) {
          hudActions.setMovementPath([{ x: pick.x, y: pick.y }]);
        } else {
          hudActions.setMovementPath([]);
        }
        finishInspect();
        void engineRef.current?.rebuild(snap);
        return;
      }

      hudActions.resetMapCommandOverlay();
      hudActions.setMapSelectedGrid(null);
      finishInspect();
      void engineRef.current?.rebuild(snap);
    };

    const onPointerMove = (ev: FederatedPointerEvent): void => {
      promoteRmbCandidateToPanIfNeeded(ev);
      if (!world.visible) return;
      if (!rmbPanCandidate && !panDrag) {
        onHoverMove(ev);
      }
      if (panDrag) {
        const dx = ev.global.x - panDrag.start.x;
        const dy = ev.global.y - panDrag.start.y;
        world.position.x = panDrag.world.x + dx;
        world.position.y = panDrag.world.y + dy;
      }
    };

    let teardown: (() => void) | null = null;
    let tornDown = false;

    const runTeardown = () => {
      if (tornDown) return;
      tornDown = true;
      try {
        app.stage?.off("pointerdown", onDown);
        app.stage?.off("pointerup", onUp);
        app.stage?.off("pointerupoutside", onUp);
        app.stage?.off("pointermove", onPointerMove);
        app.canvas?.removeEventListener("wheel", onWheel);
        app.canvas?.removeEventListener("contextmenu", onContextMenu);
      } finally {
        app.destroy(true, { children: true, texture: false });
      }
    };

    void app
      .init({
        background: 0x0f1412,
        antialias: true,
        resizeTo: host,
      })
      .then(async () => {
        if (destroyed) {
          app.destroy(true, { children: true, texture: false });
          return;
        }
        teardown = runTeardown;

        host.appendChild(app.canvas);
        app.stage.addChild(world);
        engineRef.current = { rebuild, repaintCommandOverlays };
        addTicker();

        try {
          await rebuild(snapshotRef.current);
        } catch {
          if (!tornDown) runTeardown();
          teardown = null;
          return;
        }

        if (tornDown || !app.renderer) {
          teardown = null;
          return;
        }

        app.stage.eventMode = "static";
        app.stage.hitArea = app.screen;
        app.stage.on("pointerdown", onDown);
        app.stage.on("pointerup", onUp);
        app.stage.on("pointerupoutside", onUp);
        app.stage.on("pointermove", onPointerMove);
        app.canvas.addEventListener("wheel", onWheel, { passive: false });
        app.canvas.addEventListener("contextmenu", onContextMenu);
      })
      .catch(() => {
        /* init failed */
      });

    return () => {
      destroyed = true;
      unitAnimList.length = 0;
      engineRef.current = null;
      if (teardown) {
        teardown();
        teardown = null;
      }
    };
  }, []);

  useEffect(() => {
    if (engineRef.current) {
      void engineRef.current.rebuild(snapshot);
    }
  }, [snapshot, interaction]);

  /** Selection overlays only — full rebuild would clear the Pixi scene and interrupt opponent sequential move animations. */
  useEffect(() => {
    const s = snapshotRef.current;
    if (!engineRef.current || !s) {
      return;
    }
    engineRef.current.repaintCommandOverlays(s);
  }, [mapSelectedGrid]);

  return <div ref={hostRef} className={className ?? "min-h-[420px] w-full flex-1 rounded-lg"} />;
}
