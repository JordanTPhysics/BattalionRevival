package com.game.systems;

import com.game.model.Player;
import com.game.model.map.GameMap;
import com.game.model.map.Tile;
import com.game.model.units.Unit;
import com.game.model.units.UnitAbilities;

/**
 * Enemy {@link UnitAbilities#JAMMER} units deny air movement and strip cloak nearby.
 */
public final class JammingRules {
    public static final int JAM_MANHATTAN_RADIUS = 2;

    private JammingRules() {
    }

    private static int manhattan(int ax, int ay, int bx, int by) {
        return Math.abs(ax - bx) + Math.abs(ay - by);
    }

    /**
     * Whether an enemy jammer is within {@link #JAM_MANHATTAN_RADIUS} (Manhattan) of the given cell.
     */
    public static boolean isCellJammedAgainstAircraft(GameMap map, Unit aircraft, int cellX, int cellY) {
        if (map == null || aircraft == null || aircraft.getOwner() == null) {
            return false;
        }
        Player owner = aircraft.getOwner();
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile t = map.getTile(x, y);
                if (t == null) {
                    continue;
                }
                Unit u = t.getUnit();
                if (u == null || !u.isAlive() || u.getOwner() == owner) {
                    continue;
                }
                if (!u.hasAbility(UnitAbilities.JAMMER)) {
                    continue;
                }
                if (manhattan(x, y, cellX, cellY) <= JAM_MANHATTAN_RADIUS) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True if an enemy jammer is close enough to strip this unit's cloak. */
    public static boolean isCloakedUnitAffectedByEnemyJammer(GameMap map, Unit cloaked) {
        if (map == null || cloaked == null || cloaked.getOwner() == null || !cloaked.isCloaked()) {
            return false;
        }
        int cx = cloaked.getPosition().getX();
        int cy = cloaked.getPosition().getY();
        Player owner = cloaked.getOwner();
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile t = map.getTile(x, y);
                if (t == null) {
                    continue;
                }
                Unit u = t.getUnit();
                if (u == null || !u.isAlive() || u.getOwner() == owner) {
                    continue;
                }
                if (!u.hasAbility(UnitAbilities.JAMMER)) {
                    continue;
                }
                if (manhattan(x, y, cx, cy) <= JAM_MANHATTAN_RADIUS) {
                    return true;
                }
            }
        }
        return false;
    }
}
