import type { GridPoint, MatchSnapshot } from "@/lib/protocol/types";

/** Conservative land blocks — server still validates. */
function blocksLandUnit(terrain: string): boolean {
  return terrain === "SEA_MAIN" || terrain === "REEF_1";
}

function key(p: GridPoint): string {
  return `${p.x},${p.y}`;
}

/**
 * Orthogonal BFS for a land unit. Ignores roads, bridges, amphibious rules, movement points — authoritative server rejects illegal moves.
 */
export function findSimpleLandPath(
  snap: MatchSnapshot,
  start: GridPoint,
  goal: GridPoint,
  movingUnitId: string
): GridPoint[] | null {
  if (start.x === goal.x && start.y === goal.y) {
    return [start];
  }
  const w = snap.width;
  const h = snap.height;
  const occupied = new Set<string>();
  for (const u of snap.units) {
    if (u.id !== movingUnitId) {
      occupied.add(key({ x: u.x, y: u.y }));
    }
  }

  const dirs: GridPoint[] = [
    { x: 1, y: 0 },
    { x: -1, y: 0 },
    { x: 0, y: 1 },
    { x: 0, y: -1 },
  ];

  const parent = new Map<string, GridPoint | undefined>();
  parent.set(key(start), undefined);
  const q: GridPoint[] = [{ ...start }];

  while (q.length > 0) {
    const cur = q.shift()!;
    for (const d of dirs) {
      const nx = cur.x + d.x;
      const ny = cur.y + d.y;
      if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
        continue;
      }
      const nk = `${nx},${ny}`;
      if (parent.has(nk)) {
        continue;
      }
      const row = snap.tiles[ny];
      if (!row) {
        continue;
      }
      const tile = row[nx];
      if (!tile || blocksLandUnit(tile.terrain)) {
        continue;
      }
      if (occupied.has(nk)) {
        continue;
      }
      const next: GridPoint = { x: nx, y: ny };
      parent.set(nk, cur);
      if (nx === goal.x && ny === goal.y) {
        const path: GridPoint[] = [];
        let p: GridPoint | undefined = next;
        while (p !== undefined) {
          path.push(p);
          p = parent.get(key(p));
        }
        path.reverse();
        return path;
      }
      q.push(next);
    }
  }
  return null;
}
