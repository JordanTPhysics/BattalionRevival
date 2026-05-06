package com.game.pathfinding;

import com.game.model.map.GameMap;
import com.game.model.map.Tile;
import com.game.model.units.Unit;
import com.game.model.units.UnitType.MovementKind;
import com.game.systems.JammingRules;

import java.awt.Point;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Dijkstra on the map grid: cumulative {@link com.game.model.map.TerrainType#movementCost}
 * must stay within the unit's movement budget. Enemy units block passing through; friendly
 * units do not. Destination tiles must be empty (no unit or editor sprite). Air ignores
 * terrain costs (always 1) but still respects unit occupancy (enemies block, friendlies pass).
 */
public final class GridPathfinder {

    private static final int INF = Integer.MAX_VALUE / 4;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private GridPathfinder() {
    }

    public static Set<Point> reachableEndTiles(GameMap map, Unit unit) {
        if (map == null || unit == null || unit.hasMoved()) {
            return Set.of();
        }
        int sx = unit.getPosition().getX();
        int sy = unit.getPosition().getY();
        return computeReachable(
            map,
            sx,
            sy,
            unit.getUnitType().movementKind(),
            unit.getMovementSpeed(),
            unit,
            true
        );
    }

    /**
     * Editor preview: no {@link Unit} for occupant ownership — any placed unit or sprite blocks
     * passing through except for {@link MovementKind#AIR}.
     */
    public static Set<Point> reachableEndTilesEditor(
        GameMap map,
        int fromX,
        int fromY,
        MovementKind movementKind,
        int movementBudget
    ) {
        if (map == null) {
            return Set.of();
        }
        return computeReachable(map, fromX, fromY, movementKind, movementBudget, null, false);
    }

    private static Set<Point> computeReachable(
        GameMap map,
        int sx,
        int sy,
        MovementKind kind,
        int budget,
        Unit mover,
        boolean useOwnerForPassThrough
    ) {
        int w = map.getWidth();
        int h = map.getHeight();
        int[] best = new int[w * h];
        Arrays.fill(best, INF);
        int startIdx = sy * w + sx;
        if (sx < 0 || sy < 0 || sx >= w || sy >= h) {
            return Set.of();
        }
        Tile startTile = map.getTile(sx, sy);
        if (startTile == null) {
            return Set.of();
        }
        if (!startTile.getTerrainType().canTraverseKind(kind)) {
            return Set.of();
        }

        best[startIdx] = 0;
        PriorityQueue<PqNode> pq = new PriorityQueue<>(Comparator.comparingInt(n -> n.cost));
        pq.add(new PqNode(sx, sy, 0));

        while (!pq.isEmpty()) {
            PqNode cur = pq.poll();
            int idx = cur.y * w + cur.x;
            if (cur.cost != best[idx]) {
                continue;
            }
            for (int[] d : DIRS) {
                int nx = cur.x + d[0];
                int ny = cur.y + d[1];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                    continue;
                }
                Tile next = map.getTile(nx, ny);
                if (next == null) {
                    continue;
                }
                if (!next.getTerrainType().canTraverseKind(kind)) {
                    continue;
                }
                if (kind == MovementKind.AIR
                    && mover != null
                    && JammingRules.isCellJammedAgainstAircraft(map, mover, nx, ny)) {
                    continue;
                }
                if (blocksPassThrough(kind, mover, next, useOwnerForPassThrough)) {
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
                    pq.add(new PqNode(nx, ny, nextCost));
                }
            }
        }

        Set<Point> out = new HashSet<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (x == sx && y == sy) {
                    continue;
                }
                int i = y * w + x;
                if (best[i] == INF || best[i] > budget) {
                    continue;
                }
                Tile t = map.getTile(x, y);
                if (t == null) {
                    continue;
                }
                if (!isValidDestinationTile(t, mover)) {
                    continue;
                }
                out.add(new Point(x, y));
            }
        }
        return out;
    }

    /**
     * Cloaked enemies are invisible to {@code mover}'s owner, so from the planner's point of view
     * they don't invalidate the destination. Runtime animation will detect them and trigger
     * discovery-interrupt before the mover actually lands on the tile.
     */
    private static boolean isValidDestinationTile(Tile tile, Unit mover) {
        Unit occ = tile.getUnit();
        if (occ != null) {
            if (mover != null && occ.getOwner() != mover.getOwner() && occ.isCloaked()) {
                return true;
            }
            return false;
        }
        return tile.getUnitSpriteId() == null;
    }

    /**
     * Whether entering {@code tile} as the next step is blocked for this mover (same rules as pathfinding).
     */
    public static boolean blocksPassThroughForStep(MovementKind kind, Unit mover, Tile tile) {
        return blocksPassThrough(kind, mover, tile, true);
    }

    private static boolean blocksPassThrough(
        MovementKind kind,
        Unit mover,
        Tile tile,
        boolean useOwnerForPassThrough
    ) {
        Unit occ = tile.getUnit();
        if (occ != null) {
            if (!useOwnerForPassThrough || mover == null) {
                return true;
            }
            if (occ.getOwner() == mover.getOwner()) {
                return false;
            }
            // Cloaked enemy is invisible to mover's owner — the planner treats it as empty.
            // Runtime movement animation will trigger discovery-interrupt at this tile.
            return !occ.isCloaked();
        }
        if (tile.getUnitSpriteId() != null) {
            return !useOwnerForPassThrough;
        }
        return false;
    }

    private record PqNode(int x, int y, int cost) {
    }
}
