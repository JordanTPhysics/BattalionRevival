package com.game.model.units;

import com.game.model.map.GameMap;
import com.game.systems.CombatTerrain;

/**
 * Who may attack whom (e.g. aircraft engagement, cloaked targets, canyon cover vs ranged fire).
 */
public final class EngagementRules {
    private EngagementRules() {
    }

    /**
     * Non-air attackers cannot target air defenders unless the attacker's type {@linkplain UnitType#canAttackAircraft()}.
     * Air units may always target other air units.
     *
     * @param map when non-null, {@linkplain CombatTerrain#isRangedAttacker ranged} attackers cannot target
     *     defenders standing on {@linkplain com.game.model.map.TerrainType#isCanyon() canyon} terrain.
     */
    public static boolean attackerCanTargetDefender(Unit attacker, Unit defender, GameMap map) {
        if (defender == null || !defender.isAlive()) return false;

        // Cloaked units are invisible to the enemy team and cannot be directly selected as an
        // attack target by anyone (Tracker included). Trackers reveal-and-attack via movement
        // discovery, which uncloaks the target before any damage is calculated.
        if (defender.isCloaked()) return false;

        if (map != null
            && CombatTerrain.isRangedAttacker(attacker)
            && CombatTerrain.isUnitProtectedByCanyon(map, defender)) {
            return false;
        }

        if (defender.isAircraft() && !attacker.hasAbility(UnitAbilities.ANTI_AIR)) return false;

        if (defender.getUnitType() == UnitType.Uboat && !attacker.hasAbility(UnitAbilities.ANTI_SUBMARINE)) {
            return false;
        }

        if (attacker.getUnitType() == UnitType.Uboat && defender.getUnitType().movementKind() != UnitType.MovementKind.NAVAL) return false;

        return true;
    }
}
