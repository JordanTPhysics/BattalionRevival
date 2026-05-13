package com.game.systems;

import com.game.model.map.GameMap;
import com.game.model.map.Tile;
import com.game.model.units.EngagementRules;
import com.game.model.units.Unit;
import com.game.model.units.UnitAbilities;

import java.util.concurrent.ThreadLocalRandom;

public class CombatSystem {

    public enum DamageStrikeKind {
        /** First strike from the combat initiator (Blitzkrieg / cloaked Cloaker bonuses apply here). */
        OUTGOING_INITIATOR,
        /**
         * Surprise strike from a {@link UnitAbilities#TRACKER} that just stepped onto a cloaked
         * enemy's tile (movement-interrupted discovery). Same as {@link #OUTGOING_INITIATOR} for
         * Blitzkrieg / Cloaker bonuses, plus the Tracker surprise bonus.
         */
        OUTGOING_DISCOVERY_INITIATOR,
        /** Counterattack strike. */
        COUNTER
    }

    /** Main strike: defender takes damage (counter not applied). */
    public void outgoingStrike(Unit attacker, Unit defender, GameMap map) {
        int outgoingDamage = calculateDamage(attacker, defender, DamageStrikeKind.OUTGOING_INITIATOR, map);
        applyDamageInterruptingRepair(defender, outgoingDamage);
        if (attacker.hasAbility(UnitAbilities.CLOAKER)) {
            attacker.setCloaked(false);
        }
        applyPiercingFollowThrough(attacker, defender, map, outgoingDamage);
    }

    /**
     * Discovery surprise strike: a Tracker stepped onto a cloaked enemy's tile. Caller is expected
     * to have already set {@code defender.setCloaked(false)} so engagement / damage calculations
     * see the unit as visible.
     */
    public void outgoingDiscoveryStrike(Unit attacker, Unit defender, GameMap map) {
        int outgoingDamage = calculateDamage(attacker, defender, DamageStrikeKind.OUTGOING_DISCOVERY_INITIATOR, map);
        applyDamageInterruptingRepair(defender, outgoingDamage);
        if (attacker.hasAbility(UnitAbilities.CLOAKER)) {
            attacker.setCloaked(false);
        }
        applyPiercingFollowThrough(attacker, defender, map, outgoingDamage);
    }

    /** Return strike if defender is alive, in range, and engagement rules allow it; no-op otherwise. */
    public void counterStrike(Unit defender, Unit attacker, GameMap map) {
        if (!defenderCanCounterattack(defender, attacker, map)) {
            return;
        }
        int returnDamage = calculateDamage(defender, attacker, DamageStrikeKind.COUNTER, map);
        applyDamageWithStalwart(attacker, returnDamage);
    }

    /**
     * For the counter, the defender takes the attacker role and the original attacker takes the defender
     * role, so {@link EngagementRules#attackerCanTargetDefender(Unit, Unit, GameMap)} is called with swapped arguments.
     * That's what enforces "no anti-air ability → no counter against aircraft", U-boat-only-vs-naval,
     * the cloak/tracker rules on the counter strike, and minimum attack range (no return fire vs
     * enemies inside the defender's dead zone).
     */
    public boolean defenderCanCounterattack(Unit defender, Unit attacker, GameMap map) {
        if (attacker.hasAbility(UnitAbilities.STUNNING)) {
            return false;
        }
        return defender.isAlive()
            && inCounterRange(defender, attacker, map)
            && EngagementRules.attackerCanTargetDefender(defender, attacker, map);
    }

    public void attack(Unit attacker, Unit defender, GameMap map) {
        outgoingStrike(attacker, defender, map);
        counterStrike(defender, attacker, map);
    }

    /**
     * Full damage for one strike, including ability modifiers ({@link UnitAbilities}) and the
     * attacker's current-HP scaling: a wounded unit deals proportionally less damage. Because
     * {@link #counterStrike} runs after {@link #outgoingStrike} has already applied damage to
     * the defender, a counterattacker's HP ratio here is post-hit (i.e. weakened counters hit
     * for less, as expected).
     */
    public int calculateDamage(Unit attacker, Unit defender, DamageStrikeKind strikeKind, GameMap map) {
        double multiplier = armorMultiplier(attacker.getAttackType(), defender.getArmorType());
        double damage = attacker.getAttackPower() * multiplier;
        boolean isOutgoing = strikeKind == DamageStrikeKind.OUTGOING_INITIATOR
            || strikeKind == DamageStrikeKind.OUTGOING_DISCOVERY_INITIATOR;
        if (isOutgoing && attacker.hasAbility(UnitAbilities.BLITZKRIEG)) {
            damage *= 1.2;
        }
        if (strikeKind == DamageStrikeKind.OUTGOING_DISCOVERY_INITIATOR
            && attacker.hasAbility(UnitAbilities.TRACKER)) {
            damage *= 1.2;
        }
        if (defender.getArmorType() == Unit.ArmorType.LIGHT && attacker.hasAbility(UnitAbilities.RAPIDFIRE)) {
            damage *= 2.0;
        }
        if (defender.getArmorType() == Unit.ArmorType.LIGHT && attacker.hasAbility(UnitAbilities.BARRAGE)) {
            damage *= 1.5;
        }
        if (attacker.hasAbility(UnitAbilities.EXPLOSIVE)) {
            boolean heavyTarget = defender.getArmorType() == Unit.ArmorType.HEAVY;
            boolean onStructure = false;
            if (map != null) {
                Tile t = map.getTile(defender.getPosition().getX(), defender.getPosition().getY());
                onStructure = t != null && t.getStructure() != null;
            }
            if (heavyTarget || onStructure) {
                damage *= 2.0;
            }
        }
        if (attacker.hasAbility(UnitAbilities.CLOAKER) && attacker.isCloaked()) {
            damage *= 2.0;
        }
        if (strikeKind == DamageStrikeKind.COUNTER && attacker.hasAbility(UnitAbilities.BEHEMOTH)) {
            damage *= 0.85;
        }
        damage *= attackerHealthRatio(attacker);
        return (int) Math.round(damage);
    }

    /**
     * Tile beyond {@code defender} in the direction away from {@code attacker} (same axis as a range-1
     * orthogonal hit). Pierce damage is 60% of the primary strike's rolled damage.
     */
    private void applyPiercingFollowThrough(Unit attacker, Unit defender, GameMap map, int outgoingDamage) {
        if (map == null || !attacker.hasAbility(UnitAbilities.PIERCING)) {
            return;
        }
        int dx = defender.getPosition().getX() - attacker.getPosition().getX();
        int dy = defender.getPosition().getY() - attacker.getPosition().getY();
        if ((dx == 0 && dy == 0) || (dx != 0 && dy != 0)) {
            return;
        }
        int bx = defender.getPosition().getX() + dx;
        int by = defender.getPosition().getY() + dy;
        Tile behindTile = map.getTile(bx, by);
        Unit behindDefender = behindTile != null ? behindTile.getUnit() : null;
        if (behindDefender != null && behindDefender.isAlive()) {
            int pierce = (int) Math.round(outgoingDamage * 0.6);
            applyDamageInterruptingRepair(behindDefender, pierce);
            behindDefender.setCloaked(false);
        }
    }

    /**
     * Incoming damage to a unit that committed field repair: +20% and repair is cancelled.
     * Counterattacks use {@link #applyDamageWithStalwart} directly (no repair vulnerability).
     */
    private void applyDamageInterruptingRepair(Unit target, int incomingDamage) {
        if (target == null || incomingDamage <= 0 || !target.isAlive()) {
            return;
        }
        int dmg = incomingDamage;
        if (target.isRepairing()) {
            dmg = (int) Math.round(incomingDamage * 1.2);
            target.setFieldRepairStartedRound(null);
        }
        applyDamageWithStalwart(target, dmg);
    }

    /**
     * Stalwart: 20% chance to survive an otherwise lethal strike at 1 HP.
     */
    private void applyDamageWithStalwart(Unit target, int incomingDamage) {
        if (target == null || incomingDamage <= 0 || !target.isAlive()) {
            return;
        }
        int before = target.getHealth();
        boolean lethal = incomingDamage >= before;
        if (lethal
            && target.hasAbility(UnitAbilities.STALWART)
            && ThreadLocalRandom.current().nextDouble() < 0.20) {
            target.applyDamage(before - 1);
            return;
        }
        target.applyDamage(incomingDamage);
    }

    /**
     * Fraction of {@code attacker}'s max HP it currently has (clamped to {@code [0, 1]}). Acts as
     * a multiplicative "combat effectiveness" modifier so a half-dead unit fires for ~half
     * damage. Returns 0 for a dead unit and 1 for an undamaged one.
     */
    private double attackerHealthRatio(Unit attacker) {
        int max = Math.max(1, attacker.getMaxHealth());
        int current = Math.max(0, attacker.getHealth());
        double ratio = (double) current / max;

        if (ratio < 0.25) {
            return 0.25;
        }

        if (ratio < 0.5) {
            return 0.5;
        }

        if (ratio < 0.75) {
            return 0.75;
        }

        if (ratio < 0.0) {
            return 0.0;
        }

        return 1.0;
    }

    private boolean inCounterRange(Unit defender, Unit attacker, GameMap map) {
        int dist = defender.getPosition().manhattanDistance(attacker.getPosition());
        int maxReach = CombatTerrain.effectiveMaxAttackRange(map, defender);
        return dist <= maxReach && dist >= defender.getMinAttackRange();
    }

    private double armorMultiplier(Unit.AttackType attackType, Unit.ArmorType armorType) {
        return switch (attackType) {
            case NONE -> 0.0;
            case LIGHT -> switch (armorType) {
                case LIGHT -> 1.2;
                case MEDIUM -> 1.0;
                case HEAVY -> 0.8;
            };
            case MEDIUM -> 1.0;
            case HEAVY -> switch (armorType) {
                case LIGHT -> 0.8;
                case MEDIUM -> 1.0;
                case HEAVY -> 1.2;
            };
        };
    }
}
