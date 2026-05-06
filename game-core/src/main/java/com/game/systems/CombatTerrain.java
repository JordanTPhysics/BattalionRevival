package com.game.systems;

import com.game.model.map.GameMap;
import com.game.model.map.Tile;
import com.game.model.units.Unit;

/**
 * Terrain modifiers that affect combat reach (e.g. high ground for indirect fire).
 */
public final class CombatTerrain {
    private CombatTerrain() {
    }

    /** Indirect-fire units: base type range {@code > 1} with a real weapon. */
    public static boolean isRangedAttacker(Unit unit) {
        if (unit == null || !unit.isAlive()) {
            return false;
        }
        if (unit.getAttackRange() <= 1) {
            return false;
        }
        if (unit.getAttackPower() <= 0 || unit.getAttackType() == Unit.AttackType.NONE) {
            return false;
        }
        return true;
    }

    /**
     * Max Manhattan reach for strikes originating on the shooter's current tile
     * (hills +1 for ranged only). Minimum range / dead zone is unchanged.
     */
    public static int effectiveMaxAttackRange(GameMap map, Unit shooter) {
        if (shooter == null) {
            return 0;
        }
        int base = shooter.getAttackRange();
        if (!isRangedAttacker(shooter) || map == null) {
            return base;
        }
        Tile t = map.getTile(shooter.getPosition().getX(), shooter.getPosition().getY());
        if (t == null || t.getTerrainType() == null) {
            return base;
        }
        if (t.getTerrainType().grantsRangedHillRangeBonus()) {
            return base + 1;
        }
        return base;
    }

    /**
     * {@return true} if this unit's tile is canyon terrain — {@linkplain #isRangedAttacker(Unit) Ranged} enemies
     * cannot select it as an attack target (see {@link com.game.model.units.EngagementRules}).
     */
    public static boolean isUnitProtectedByCanyon(GameMap map, Unit unit) {
        if (unit == null || map == null) {
            return false;
        }
        Tile t = map.getTile(unit.getPosition().getX(), unit.getPosition().getY());
        if (t == null || t.getTerrainType() == null) {
            return false;
        }
        return t.getTerrainType().isCanyon();
    }
}
