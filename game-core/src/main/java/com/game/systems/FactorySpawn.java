package com.game.systems;

import com.game.model.Player;
import com.game.model.Position;
import com.game.model.map.GameMap;
import com.game.model.map.Tile;
import com.game.model.units.FacingDirection;
import com.game.model.units.Unit;
import com.game.model.units.UnitType;
import com.game.model.units.UnitType.MovementKind;

import java.awt.Point;
import java.util.ArrayDeque;

/**
 * Finds an empty tile to place a unit built from a factory (prefers the factory tile, then BFS rings).
 */
public final class FactorySpawn {
    private FactorySpawn() {
    }

    private static boolean canOccupyAsSpawn(Tile tile, MovementKind kind) {
        if (tile == null) {
            return false;
        }
        if (tile.getUnit() != null || tile.getUnitSpriteId() != null) {
            return false;
        }
        return tile.getTerrainType().canTraverseKind(kind);
    }

    /**
     * @return grid position for the new unit, or {@code null} if none found within {@code maxRadius}.
     */
    public static Point findSpawn(GameMap map, int factoryX, int factoryY, UnitType type, int maxRadius) {
        if (map == null || type == null) {
            return null;
        }
        MovementKind kind = type.movementKind();
        ArrayDeque<Point> q = new ArrayDeque<>();
        boolean[][] seen = new boolean[map.getHeight()][map.getWidth()];
        q.add(new Point(factoryX, factoryY));
        seen[factoryY][factoryX] = true;
        while (!q.isEmpty()) {
            Point p = q.poll();
            if (manhattan(p.x, p.y, factoryX, factoryY) > maxRadius) {
                continue;
            }
            Tile t = map.getTile(p.x, p.y);
            if (canOccupyAsSpawn(t, kind)) {
                return p;
            }
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = p.x + d[0];
                int ny = p.y + d[1];
                if (nx < 0 || ny < 0 || nx >= map.getWidth() || ny >= map.getHeight()) {
                    continue;
                }
                if (seen[ny][nx]) {
                    continue;
                }
                seen[ny][nx] = true;
                q.add(new Point(nx, ny));
            }
        }
        return null;
    }

    /**
     * First empty orthogonally adjacent tile the unit type can occupy (north, east, south, west).
     */
    public static Point findAdjacentSpawn(GameMap map, int originX, int originY, UnitType type) {
        if (map == null || type == null) {
            return null;
        }
        MovementKind kind = type.movementKind();
        int[][] d = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int[] step : d) {
            int nx = originX + step[0];
            int ny = originY + step[1];
            if (nx < 0 || ny < 0 || nx >= map.getWidth() || ny >= map.getHeight()) {
                continue;
            }
            Tile t = map.getTile(nx, ny);
            if (canOccupyAsSpawn(t, kind)) {
                return new Point(nx, ny);
            }
        }
        return null;
    }

    private static int manhattan(int ax, int ay, int bx, int by) {
        return Math.abs(ax - bx) + Math.abs(ay - by);
    }

    public static void placeNewUnit(GameMap map, UnitType type, Player owner, Point spawn) {
        Unit unit = new Unit(type, owner, new Position(spawn.x, spawn.y));
        Tile tile = map.getTile(spawn.x, spawn.y);
        tile.setUnit(unit);
        tile.setUnitSpriteId(type.name());
        tile.setUnitFacing(FacingDirection.EAST);
        owner.getUnits().add(unit);
    }
}
