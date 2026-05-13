import type { GridPoint, MatchSnapshot, TileSnapshot, UnitSnapshot } from "@/lib/protocol/types";
import type { MovementKind } from "@/lib/game/unitTypeCatalog";
import {
  JAMMER,
  MASSIVE_HULL,
  getUnitTypeStats,
  hasAbility,
} from "@/lib/game/unitTypeCatalog";
import { canTraverseKind, canTraverseTerrain, movementCostForKind } from "@/lib/game/terrainMovement";

const INF = Math.floor(Number.MAX_SAFE_INTEGER / 8);
const JAM_RADIUS = 2;

function keyxy(x: number, y: number): string {
  return `${x},${y}`;
}

/** True when this unit is embarked in a transport (not occupying its own map tile). */
function isEmbarkedInSnapshot(u: UnitSnapshot): boolean {
  return u.embarkedInTransportUnitId != null && u.embarkedInTransportUnitId !== "";
}

function unitOccupant(snapshot: MatchSnapshot, x: number, y: number): UnitSnapshot | undefined {
  return snapshot.units.find(
    (u) => u.x === x && u.y === y && !isEmbarkedInSnapshot(u)
  );
}

/** Mirrors {@link com.game.systems.JammingRules#isCellJammedAgainstAircraft} (enemy jammer only). */
function isAirCellJammed(snapshot: MatchSnapshot, aircraft: UnitSnapshot, cellX: number, cellY: number): boolean {
  if (!getUnitTypeStats(aircraft.unitType)) {
    return false;
  }
  const ownerSeat = aircraft.ownerSeatIndex;
  for (const u of snapshot.units) {
    if (u.ownerSeatIndex === ownerSeat) {
      continue;
    }
    const st = getUnitTypeStats(u.unitType);
    if (!st || !hasAbility(st, JAMMER)) {
      continue;
    }
    const d = Math.abs(u.x - cellX) + Math.abs(u.y - cellY);
    if (d <= JAM_RADIUS) {
      return true;
    }
  }
  return false;
}

/**
 * Mirrors {@link com.game.pathfinding.GridPathfinder} pass-through /
 * destination rules using {@link MatchSnapshot}.
 */
export function blocksPassThroughForMove(
  snapshot: MatchSnapshot,
  mover: UnitSnapshot,
  tx: number,
  ty: number,
  tile: TileSnapshot | null | undefined,
  movementKind: MovementKind,
  options: { useOwnerForPassThrough: boolean }
): boolean {
  const occ = unitOccupant(snapshot, tx, ty);
  const useOwnerForPassThrough = options.useOwnerForPassThrough;

  if (occ) {
    if (!useOwnerForPassThrough) {
      return true;
    }
    if (occ.ownerSeatIndex === mover.ownerSeatIndex) {
      return false;
    }
    if (occ.cloaked) {
      return false;
    }
    return true;
  }
  const ghost = tile?.unitSprite ?? null;
  if (ghost !== null && ghost !== "") {
    return useOwnerForPassThrough;
  }
  return false;
}

function destinationOk(snapshot: MatchSnapshot, mover: UnitSnapshot, tx: number, ty: number): boolean {
  const occ = unitOccupant(snapshot, tx, ty);
  if (occ) {
    if (occ.id === mover.id) {
      return true;
    }
    if (occ.ownerSeatIndex !== mover.ownerSeatIndex && occ.cloaked) {
      return true;
    }
    return false;
  }
  const row = snapshot.tiles[ty];
  const tile = row?.[tx];
  const ghost = tile?.unitSprite ?? null;
  return ghost === null || ghost === "";
}

/**
 * Mirrors {@link com.game.pathfinding.GridPathfinder#reachableEndTiles(GameMap, Unit)} —
 * orthogonal steps, cumulative terrain spend with budget, ally pass-through allowed.
 */
export function reachableEndTiles(snapshot: MatchSnapshot, moverId: string): Set<string> {
  const mover = snapshot.units.find((u) => u.id === moverId);
  const out = new Set<string>();

  const stats = mover ? getUnitTypeStats(mover.unitType) : null;
  if (!mover || !stats || mover.hasMoved) {
    return out;
  }

  const sx = mover.x;
  const sy = mover.y;
  const budget = stats.movementSpeed;
  const kind = stats.movementKind;
  const massiveHull = hasAbility(stats, MASSIVE_HULL);

  const w = snapshot.width;
  const h = snapshot.height;

  const startRow = snapshot.tiles[sy];
  const startTerrain = startRow?.[sx]?.terrain;
  if (
    startTerrain === undefined
  ) {
    return out;
  }

  const best = new Map<string, number>();
  best.set(keyxy(sx, sy), 0);

  interface Node {
    x: number;
    y: number;
    cost: number;
  }

  const pq: Node[] = [{ x: sx, y: sy, cost: 0 }];

  const dirs = [
    [1, 0],
    [-1, 0],
    [0, 1],
    [0, -1],
  ];

  const popMin = (): Node | undefined => {
    if (pq.length === 0) {
      return undefined;
    }
    let mi = 0;
    let mc = pq[0]?.cost ?? INF;
    for (let i = 1; i < pq.length; i++) {
      const q = pq[i];
      if (q && q.cost < mc) {
        mc = q.cost;
        mi = i;
      }
    }
    return pq.splice(mi, 1)[0];
  };

  while (pq.length > 0) {
    const cur = popMin();
    if (!cur) {
      break;
    }
    const ck = keyxy(cur.x, cur.y);
    if ((best.get(ck) ?? INF) !== cur.cost) {
      continue;
    }
    for (const [dx, dy] of dirs) {
      const nx = cur.x + dx;
      const ny = cur.y + dy;
      if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
        continue;
      }
      const nrow = snapshot.tiles[ny];
      const nt = nrow?.[nx];
      if (!nt || !canTraverseKind(nt.terrain, kind)) {
        continue;
      }
      if (kind === "AIR" && isAirCellJammed(snapshot, mover, nx, ny)) {
        continue;
      }
      if (!canTraverseTerrain(nt.terrain, kind, { massiveHull })) {
        continue;
      }
      if (blocksPassThroughForMove(snapshot, mover, nx, ny, nt, kind, { useOwnerForPassThrough: true })) {
        continue;
      }
      const step = movementCostForKind(nt.terrain, kind);
      if (step >= INF) {
        continue;
      }
      const nextCost = cur.cost + step;
      if (nextCost > budget) {
        continue;
      }
      const nk = keyxy(nx, ny);
      const prevBest = best.get(nk) ?? INF;
      if (nextCost < prevBest) {
        best.set(nk, nextCost);
        pq.push({ x: nx, y: ny, cost: nextCost });
      }
    }
  }

  for (let ty = 0; ty < h; ty++) {
    for (let tx = 0; tx < w; tx++) {
      if (tx === sx && ty === sy) {
        continue;
      }
      const k = keyxy(tx, ty);
      const bc = best.get(k) ?? INF;
      if (bc === INF || bc > budget) {
        continue;
      }
      const trow = snapshot.tiles[ty];
      const t = trow?.[tx];
      if (!t || !destinationOk(snapshot, mover, tx, ty)) {
        continue;
      }
      out.add(k);
    }
  }

  return out;
}

export function shortestLegalPathIncludingStart(
  snapshot: MatchSnapshot,
  mover: UnitSnapshot,
  goalX: number,
  goalY: number
): GridPoint[] | null {
  const stats = getUnitTypeStats(mover.unitType);
  if (!stats || mover.hasMoved) {
    return null;
  }

  const sx = mover.x;
  const sy = mover.y;
  if (sx === goalX && sy === goalY) {
    return [{ x: sx, y: sy }];
  }

  const w = snapshot.width;
  const h = snapshot.height;

  const startRow = snapshot.tiles[sy];
  const startTerrain = startRow?.[sx]?.terrain;
  const massiveHull = hasAbility(stats, MASSIVE_HULL);
  if (
    startTerrain === undefined
  ) {
    return null;
  }

  const kind = stats.movementKind;
  const budget = stats.movementSpeed;

  const bestCost = new Map<string, number>();
  const prevKey = new Map<string, string>();
  const pq: Array<{ cx: number; cy: number; cost: number }> = [];

  bestCost.set(keyxy(sx, sy), 0);
  pq.push({ cx: sx, cy: sy, cost: 0 });

  const dirs = [
    [1, 0],
    [-1, 0],
    [0, 1],
    [0, -1],
  ];

  const popMin2 = (): { cx: number; cy: number; cost: number } | undefined => {
    if (pq.length === 0) {
      return undefined;
    }
    let mi = 0;
    let mc = pq[0]!.cost;
    for (let i = 1; i < pq.length; i++) {
      if ((pq[i]?.cost ?? INF) < mc) {
        mc = pq[i]!.cost;
        mi = i;
      }
    }
    return pq.splice(mi, 1)[0];
  };

  while (pq.length > 0) {
    const cur = popMin2();
    if (!cur) {
      break;
    }
    const ck = keyxy(cur.cx, cur.cy);
    if ((bestCost.get(ck) ?? INF) !== cur.cost) {
      continue;
    }

    if (cur.cx === goalX && cur.cy === goalY) {
      break;
    }

    for (const [dx, dy] of dirs) {
      const nx = cur.cx + dx;
      const ny = cur.cy + dy;
      if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
        continue;
      }
      const nrow = snapshot.tiles[ny];
      const nt = nrow?.[nx];
      if (!nt || !canTraverseKind(nt.terrain, kind)) {
        continue;
      }
      if (kind === "AIR" && isAirCellJammed(snapshot, mover, nx, ny)) {
        continue;
      }
      if (!canTraverseTerrain(nt.terrain, kind, { massiveHull })) {
        continue;
      }
      const finalStop = nx === goalX && ny === goalY;
      if (
        blocksPassThroughForMove(snapshot, mover, nx, ny, nt, kind, { useOwnerForPassThrough: true })
      ) {
        continue;
      }
      if (finalStop && !destinationOk(snapshot, mover, nx, ny)) {
        continue;
      }
      const step = movementCostForKind(nt.terrain, kind);
      if (step >= INF) {
        continue;
      }
      const nextCost = cur.cost + step;
      if (nextCost > budget) {
        continue;
      }
      const nk = keyxy(nx, ny);
      const pb = bestCost.get(nk) ?? INF;
      if (nextCost < pb) {
        bestCost.set(nk, nextCost);
        prevKey.set(nk, ck);
        pq.push({ cx: nx, cy: ny, cost: nextCost });
      }
    }
  }

  const gKey = keyxy(goalX, goalY);
  if (!(bestCost.has(gKey) && (bestCost.get(gKey) ?? INF) <= budget)) {
    return null;
  }

  const revGp: GridPoint[] = [];
  let kk = gKey;
  for (;;) {
    const [kx, ky] = kk.split(",").map((n) => Number(n));
    if (kx === undefined || ky === undefined || Number.isNaN(kx) || Number.isNaN(ky)) {
      return null;
    }
    revGp.push({ x: kx, y: ky });
    if (kx === sx && ky === sy) {
      break;
    }
    const p = prevKey.get(kk);
    if (!p) {
      return null;
    }
    kk = p;
  }

  revGp.reverse();
  return revGp.length >= 2 ? revGp : null;
}

/**
 * Mirrors {@link com.game.pathfinding.UnitMovementPaths#isValidMovementPath(GameMap, Unit, List)} using snapshot state.
 */
export function isValidMovementPathSnapshot(snapshot: MatchSnapshot, unit: UnitSnapshot, pathIncludingStart: GridPoint[]): boolean {
  if (pathIncludingStart.length < 2) {
    return false;
  }
  const p0 = pathIncludingStart[0];
  if (p0!.x !== unit.x || p0!.y !== unit.y) {
    return false;
  }
  if (unit.hasMoved) {
    return false;
  }
  const stats = getUnitTypeStats(unit.unitType);
  if (!stats) {
    return false;
  }
  const kind = stats.movementKind;
  const budget = stats.movementSpeed;
  const massiveHull = hasAbility(stats, MASSIVE_HULL);
  let total = 0;

  for (let i = 1; i < pathIncludingStart.length; i++) {
    const prev = pathIncludingStart[i - 1]!;
    const cur = pathIncludingStart[i]!;
    if (Math.abs(cur.x - prev.x) + Math.abs(cur.y - prev.y) !== 1) {
      return false;
    }
    const row = snapshot.tiles[cur.y];
    const toTile = row?.[cur.x];
    if (!toTile || !canTraverseTerrain(toTile.terrain, kind, { massiveHull })) {
      return false;
    }
    if (kind === "AIR" && isAirCellJammed(snapshot, unit, cur.x, cur.y)) {
      return false;
    }
    const finalStep = i === pathIncludingStart.length - 1;
    if (finalStep) {
      if (!destinationOk(snapshot, unit, cur.x, cur.y)) {
        return false;
      }
    }
    if (
      blocksPassThroughForMove(snapshot, unit, cur.x, cur.y, toTile, kind, { useOwnerForPassThrough: true })
    ) {
      return false;
    }
    const step = movementCostForKind(toTile.terrain, kind);
    if (step >= INF) {
      return false;
    }
    total += step;
    if (total > budget) {
      return false;
    }
  }
  return true;
}
