/**
 * Mirrors {@link com.game.ui.GameWindow.GameMapPanel} overlay sets:
 * reachable (white tint), attack range preview (light red tint), attackable enemies (deep red tint),
 * idle crosshair eligibility.
 */
import type { GridPoint, MatchSnapshot, UnitSnapshot } from "@/lib/protocol/types";
import { canClientPreviewAttack, effectiveMaxAttackRange, isIndirectFireUnit } from "@/lib/game/clientAttack";
import { getUnitTypeStats, minAttackRangeForType } from "@/lib/game/unitTypeCatalog";
import { reachableEndTiles } from "@/lib/game/clientPathfinding";

function xyKey(x: number, y: number): string {
  return `${x},${y}`;
}

function parseKey(k: string): GridPoint | null {
  const p = k.split(",");
  const x = Number(p[0]);
  const y = Number(p[1]);
  if (p.length !== 2 || Number.isNaN(x) || Number.isNaN(y)) {
    return null;
  }
  return { x, y };
}

/** Mirrors Swing session checks for movable unit owned by active player. */
export function canUnitMoveForActiveSeat(snapshot: MatchSnapshot, seat: number, u: UnitSnapshot): boolean {
  if (snapshot.matchFinished || u.hasMoved || u.ownerSeatIndex !== seat) {
    return false;
  }
  return snapshot.activePlayerIndex === seat;
}

/** Mirrors {@link PlayableGameSession#canUnitAttack(Unit)} ownership implied by caller. */
export function canUnitAttackPreview(u: UnitSnapshot): boolean {
  const s = getUnitTypeStats(u.unitType);
  if (!s) {
    return false;
  }
  return s.damage > 0 && s.attackType !== "NONE";
}

/** Mirrors {@link PlayableGameSession#canActivePlayerPerceiveUnit} for idle crosshair visibility. */
export function canSeatPerceiveUnitSnapshot(snapshot: MatchSnapshot, activeSeat: number, u: UnitSnapshot): boolean {
  if (u.ownerSeatIndex === activeSeat) {
    return true;
  }
  if (!u.cloaked) {
    return true;
  }
  for (const f of snapshot.units) {
    if (f.ownerSeatIndex !== activeSeat || !aliveOnMap(f)) {
      continue;
    }
    if (Math.abs(f.x - u.x) + Math.abs(f.y - u.y) <= 1) {
      return true;
    }
  }
  return false;
}

function aliveOnMap(u: UnitSnapshot): boolean {
  return u.health > 0;
}

function oneTileBeyondReachable(reachable: Set<string>, unit: UnitSnapshot): Set<string> {
  const startX = unit.x;
  const startY = unit.y;
  const expandFrom = new Set<string>(reachable);
  expandFrom.add(xyKey(startX, startY));
  const out = new Set<string>();
  const dirs = [
    [1, 0],
    [-1, 0],
    [0, 1],
    [0, -1],
  ];
  for (const k of expandFrom) {
    const p = parseKey(k);
    if (!p) continue;
    for (const [dx, dy] of dirs) {
      const nx = p.x + dx;
      const ny = p.y + dy;
      if (nx === startX && ny === startY) {
        continue;
      }
      const nk = xyKey(nx, ny);
      if (!reachable.has(nk)) {
        out.add(nk);
      }
    }
  }
  return out;
}

function stationaryAttackBand(snapshot: MatchSnapshot, unit: UnitSnapshot): Set<string> {
  const ux = unit.x;
  const uy = unit.y;
  const ar = getUnitTypeStats(unit.unitType)?.attackRange ?? 0;
  const minR = Math.max(0, minAttackRangeForType(ar));
  const maxR = effectiveMaxAttackRange(snapshot, unit);
  const out = new Set<string>();
  const w = snapshot.width;
  const h = snapshot.height;
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const d = Math.abs(x - ux) + Math.abs(y - uy);
      if (d >= minR && d <= maxR) {
        out.add(xyKey(x, y));
      }
    }
  }
  return out;
}

/**
 * Attack range preview: light-red overlay in Swing (not attackable-deep-red).
 */
export function computeAttackRangePreviewCells(snapshot: MatchSnapshot, activeSeat: number, unitId: string): Set<string> {
  const unit = snapshot.units.find((u) => u.id === unitId);
  if (!unit || !canUnitMoveForActiveSeat(snapshot, activeSeat, unit) || !canUnitAttackPreview(unit)) {
    return new Set();
  }
  const reach = reachableEndTiles(snapshot, unitId);
  if (isIndirectFireUnit(unit)) {
    return stationaryAttackBand(snapshot, unit);
  }
  return oneTileBeyondReachable(reach, unit);
}

export function computeAttackableEnemyCells(
  snapshot: MatchSnapshot,
  activeSeat: number,
  attackerId: string
): Set<string> {
  const attacker = snapshot.units.find((u) => u.id === attackerId);
  if (!attacker || !canUnitAttackPreview(attacker)) {
    return new Set();
  }
  const atkAr = getUnitTypeStats(attacker.unitType)?.attackRange ?? 0;
  const minR = Math.max(0, minAttackRangeForType(atkAr));
  const out = new Set<string>();
  for (const other of snapshot.units) {
    if (!aliveOnMap(other) || other.ownerSeatIndex === attacker.ownerSeatIndex) {
      continue;
    }
    const dist = manhattan(attacker.x, attacker.y, other.x, other.y);
    if (
      dist <= effectiveMaxAttackRange(snapshot, attacker) &&
      dist >= minR &&
      canClientPreviewAttack(snapshot, activeSeat, attacker, other)
    ) {
      out.add(xyKey(other.x, other.y));
    }
  }
  return out;
}

function manhattan(ax: number, ay: number, bx: number, by: number): number {
  return Math.abs(ax - bx) + Math.abs(ay - by);
}

/**
 * Idle corner-bracket overlay (Swing): active player's unmoved perceived units eligible to act,
 * plus vacant owned factories. Note: Swing also hides factories that already produced this turn;
 * snapshots do not expose that flag, so this may briefly over-mark after a local build until resync.
 */
export function computeIdleCrosshairCells(snapshot: MatchSnapshot, activeSeat: number): Set<string> {
  const tid = activeSeat + 1;
  const out = new Set<string>();
  for (const u of snapshot.units) {
    if (!aliveOnMap(u)) {
      continue;
    }
    if (
      snapshot.activePlayerIndex === activeSeat &&
      u.ownerSeatIndex === activeSeat &&
      !u.hasMoved &&
      canSeatPerceiveUnitSnapshot(snapshot, activeSeat, u)
    ) {
      out.add(xyKey(u.x, u.y));
    }
  }

  const w = snapshot.width;
  const h = snapshot.height;
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const t = snapshot.tiles[y]?.[x];
      if (!t || t.structure !== "Factory" || t.structureTeam !== tid) {
        continue;
      }
      if (snapshot.units.some((u) => u.x === x && u.y === y)) {
        continue;
      }
      out.add(xyKey(x, y));
    }
  }
  return out;
}
