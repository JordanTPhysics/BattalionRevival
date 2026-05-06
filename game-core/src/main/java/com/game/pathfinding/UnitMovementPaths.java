package com.game.pathfinding;

import com.game.model.map.GameMap;
import com.game.model.map.Tile;
import com.game.model.units.FacingDirection;
import com.game.model.units.Unit;
import com.game.model.units.UnitType.MovementKind;
import com.game.systems.JammingRules;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Multi-tile paths: terrain and movement budget apply to every step; enemy units block passing through;
 * friendly units may be passed through but not stopped on (same rules as {@link GridPathfinder}).
 */
public final class UnitMovementPaths {

    private static final int INF = Integer.MAX_VALUE / 4;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private UnitMovementPaths() {
    }

    public static FacingDirection facingForStep(Point from, Point to) {
        int dx = to.x - from.x;
        int dy = to.y - from.y;
        if (dx == 1) {
            return FacingDirection.EAST;
        }
        if (dx == -1) {
            return FacingDirection.WEST;
        }
        if (dy == 1) {
            return FacingDirection.SOUTH;
        }
        if (dy == -1) {
            return FacingDirection.NORTH;
        }
        return FacingDirection.EAST;
    }

    /**
     * Legal path: starts at unit position, 4-neighbor steps, cumulative terrain cost within budget,
     * pass-through rules as {@link GridPathfinder}. Intermediate tiles may contain friendly units (including
     * several in a row); the final tile must be empty (no unit or editor sprite).
     */
    public static boolean isValidMovementPath(GameMap map, Unit unit, List<Point> pathIncludingStart) {
        if (map == null || unit == null || pathIncludingStart == null || pathIncludingStart.size() < 2) {
            return false;
        }
        Point start = pathIncludingStart.get(0);
        if (start.x != unit.getPosition().getX() || start.y != unit.getPosition().getY()) {
            return false;
        }
        if (unit.hasMoved()) {
            return false;
        }
        MovementKind kind = unit.getUnitType().movementKind();
        int budget = unit.getMovementSpeed();
        int total = 0;
        for (int i = 1; i < pathIncludingStart.size(); i++) {
            Point prev = pathIncludingStart.get(i - 1);
            Point cur = pathIncludingStart.get(i);
            if (Math.abs(cur.x - prev.x) + Math.abs(cur.y - prev.y) != 1) {
                return false;
            }
            Tile toTile = map.getTile(cur.x, cur.y);
            if (toTile == null || !toTile.getTerrainType().canTraverse(unit)) {
                return false;
            }
            if (kind == MovementKind.AIR
                && JammingRules.isCellJammedAgainstAircraft(map, unit, cur.x, cur.y)) {
                return false;
            }
            boolean finalStep = (i == pathIncludingStart.size() - 1);
            if (finalStep) {
                if (!canStopOnTile(toTile, kind, unit)) {
                    return false;
                }
            }
            if (GridPathfinder.blocksPassThroughForStep(kind, unit, toTile)) {
                return false;
            }
            int step = toTile.getTerrainType().movementCostForKind(kind);
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

    /**
     * Shortest cost path under movement budget; may pass through friendly-occupied tiles; the goal must be a valid
     * stop tile (empty for ground). Result is validated with {@link #isValidMovementPath}.
     */
    public static List<Point> shortestLegalPath(GameMap map, Unit unit, int goalX, int goalY) {
        if (map == null || unit == null || unit.hasMoved()) {
            return List.of();
        }
        int sx = unit.getPosition().getX();
        int sy = unit.getPosition().getY();
        if (goalX == sx && goalY == sy) {
            return List.of(new Point(sx, sy));
        }
        int w = map.getWidth();
        int h = map.getHeight();
        int[] best = new int[w * h];
        int[] parent = new int[w * h];
        Arrays.fill(best, INF);
        Arrays.fill(parent, -1);
        int startIdx = sy * w + sx;
        if (sx < 0 || sy < 0 || sx >= w || sy >= h) {
            return List.of();
        }
        Tile startTile = map.getTile(sx, sy);
        if (startTile == null || !startTile.getTerrainType().canTraverse(unit)) {
            return List.of();
        }
        MovementKind kind = unit.getUnitType().movementKind();
        int budget = unit.getMovementSpeed();

        best[startIdx] = 0;
        PriorityQueue<PqNode> pq = new PriorityQueue<>(Comparator.comparingInt(n -> n.cost));
        pq.add(new PqNode(sx, sy, 0));

        while (!pq.isEmpty()) {
            PqNode cur = pq.poll();
            int idx = cur.y * w + cur.x;
            if (cur.cost != best[idx]) {
                continue;
            }
            if (cur.x == goalX && cur.y == goalY) {
                break;
            }
            for (int[] d : DIRS) {
                int nx = cur.x + d[0];
                int ny = cur.y + d[1];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                    continue;
                }
                Tile next = map.getTile(nx, ny);
                if (next == null || !next.getTerrainType().canTraverseKind(kind)) {
                    continue;
                }
                if (kind == MovementKind.AIR
                    && JammingRules.isCellJammedAgainstAircraft(map, unit, nx, ny)) {
                    continue;
                }
                if (GridPathfinder.blocksPassThroughForStep(kind, unit, next)) {
                    continue;
                }
                if (nx == goalX && ny == goalY && !canStopOnTile(next, kind, unit)) {
                    continue;
                }
                int step = next.getTerrainType().movementCostForKind(kind);
                if (step >= INF) {
                    continue;
                }
                int nextCost = cur.cost + step;
                if (nextCost > budget) {
                    continue;
                }
                int nIdx = ny * w + nx;
                if (nextCost < best[nIdx]) {
                    best[nIdx] = nextCost;
                    parent[nIdx] = idx;
                    pq.add(new PqNode(nx, ny, nextCost));
                }
            }
        }

        int gIdx = goalY * w + goalX;
        if (best[gIdx] == INF) {
            return List.of();
        }
        ArrayList<Point> rev = new ArrayList<>();
        int curIdx = gIdx;
        while (curIdx >= 0) {
            int cx = curIdx % w;
            int cy = curIdx / w;
            rev.add(new Point(cx, cy));
            curIdx = parent[curIdx];
        }
        Collections.reverse(rev);
        if (!isValidMovementPath(map, unit, rev)) {
            return List.of();
        }
        return rev;
    }

    /**
     * Tile is a legal end-of-movement cell. A cloaked enemy of {@code mover} doesn't disqualify the
     * tile from the planner's perspective — runtime animation will discovery-interrupt before the
     * mover actually lands.
     */
    private static boolean canStopOnTile(Tile tile, MovementKind kind, Unit mover) {
        Unit occ = tile.getUnit();
        if (occ != null) {
            if (mover != null && occ.getOwner() != mover.getOwner() && occ.isCloaked()) {
                return true;
            }
            return false;
        }
        return tile.getUnitSpriteId() == null;
    }

    private record PqNode(int x, int y, int cost) {
    }
}
