package com.game.systems;

import com.game.pathfinding.GridPathfinder;
import com.game.model.map.GameMap;
import com.game.model.units.Unit;
import com.game.model.units.UnitType;

import java.awt.Point;
import java.util.Collections;
import java.util.Set;

/**
 * Reachable destination tiles using the same rules as {@link MovementSystem}: terrain passability
 * and movement costs, movement budget, a clear path (enemy units block; friendly units do not),
 * and an empty destination (no {@link com.game.model.map.Tile#getUnit()} or editor sprite).
 */
public final class MovementReach {

    private MovementReach() {
    }

    public static Set<Point> reachableDestinations(GameMap map, Unit unit) {
        if (unit == null || unit.hasMoved()) {
            return Collections.emptySet();
        }
        return GridPathfinder.reachableEndTiles(map, unit);
    }

    /**
     * Editor-placed sprite (no {@link Unit} instance): approximate preview using foot movement and range 4,
     * matching the default {@link Unit} constructor used by the engine.
     */
    public static Set<Point> reachableForPlacedSprite(GameMap map, int fromX, int fromY) {
        return GridPathfinder.reachableEndTilesEditor(
            map,
            fromX,
            fromY,
            UnitType.Commando.movementKind(),
            4
        );
    }

    public static Set<Point> reachableForKind(
        GameMap map,
        int fromX,
        int fromY,
        UnitType.MovementKind movementKind,
        int movementRange
    ) {
        return GridPathfinder.reachableEndTilesEditor(map, fromX, fromY, movementKind, movementRange);
    }
}
