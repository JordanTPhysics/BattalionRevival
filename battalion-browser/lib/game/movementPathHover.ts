import type { GridPoint, MatchSnapshot, UnitSnapshot } from "@/lib/protocol/types";
import {
  isValidMovementPathSnapshot,
  shortestLegalPathIncludingStart,
} from "@/lib/game/clientPathfinding";
import { canUnitMoveForActiveSeat } from "@/lib/game/swingMapHighlights";

/**
 * Mirrors {@link com.game.ui.GameWindow.GameMapPanel#updatePlannedPathFromHover(Point)}.
 */
export function updateMovementPathFromHover(
  snapshot: MatchSnapshot,
  activeSeat: number,
  unit: UnitSnapshot,
  movementPath: GridPoint[],
  hover: GridPoint
): GridPoint[] {
  if (!canUnitMoveForActiveSeat(snapshot, activeSeat, unit)) {
    return movementPath;
  }
  const path = [...movementPath];
  const ensureStart = (): void => {
    const start: GridPoint = { x: unit.x, y: unit.y };
    if (path.length === 0 || path[0]!.x !== start.x || path[0]!.y !== start.y) {
      path.length = 0;
      path.push(start);
    }
  };
  ensureStart();
  const last = path[path.length - 1]!;
  if (hover.x === last.x && hover.y === last.y) {
    return path;
  }
  if (path.length >= 2) {
    const pen = path[path.length - 2]!;
    if (hover.x === pen.x && hover.y === pen.y) {
      path.pop();
      return path;
    }
  }
  for (let i = 0; i < path.length; i++) {
    const pi = path[i]!;
    if (pi.x === hover.x && pi.y === hover.y) {
      while (path.length > i + 1) {
        path.pop();
      }
      return path;
    }
  }
  const manhattan = Math.abs(hover.x - last.x) + Math.abs(hover.y - last.y);
  if (manhattan === 1) {
    const trial = [...path, hover];
    if (isValidMovementPathSnapshot(snapshot, unit, trial)) {
      return trial;
    }
  }
  const snap = shortestLegalPathIncludingStart(snapshot, unit, hover.x, hover.y);
  if (snap && snap.length >= 2) {
    return [...snap];
  }
  return path;
}
