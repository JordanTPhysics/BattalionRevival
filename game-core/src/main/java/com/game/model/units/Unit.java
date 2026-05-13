package com.game.model.units;

import com.game.model.Player;
import com.game.model.Position;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Unit {
    public enum ArmorType { LIGHT, MEDIUM, HEAVY }
    public enum AttackType { LIGHT, MEDIUM, HEAVY, NONE }

    /** Dedicated build budget for {@link UnitType#Warmachine}; other types keep {@code 0}. */
    public static final int WARMACHINE_STARTING_FUNDS = 2000;

    private final String id;
    private UnitType unitType;
    /**
     * Set to the unit's pre-conversion {@link UnitType} when it has been morphed into a transport
     * (Albatross / Leviathan); {@code null} otherwise. Used to revert the unit to its original
     * land-unit form via {@link #revertToOriginalType()}.
     */
    private UnitType originalUnitType;
    private final Player owner;
    private int health;
    private int movementSpeed;
    private int attackRange;
    private int attackPower;
    private ArmorType armorType;
    private AttackType attackType;
    private Position position;
    /**
     * Per-turn action flag: a unit gets a single action per turn, which can be a move, an attack, or
     * a combined move-then-attack. Once the unit's action is consumed this flips to {@code true} and
     * the unit can neither move nor attack again until the next turn.
     */
    private boolean hasMoved;
    /** Cloaker stealth: hidden from opponents until revealed. */
    private boolean cloaked;
    /**
     * Field repair: non-null is the {@link com.game.engine.TurnManager#getRoundNumber()} when repair
     * was committed; unit cannot move that turn. At the start of a later turn for this owner when
     * {@code round > fieldRepairStartedRound}, heals one quarter max HP if still pending and not
     * interrupted by damage.
     */
    private Integer fieldRepairStartedRound;
    /** Non-null when this transport (Albatross / Leviathan) is carrying a friendly unit. */
    private Unit embarkedPassenger;
    /** Non-null when this unit is aboard a friendly transport (not placed on the map grid). */
    private Unit embarkedInTransport;
    /** Private purse for onboard production ({@link UnitType#Warmachine} only). */
    private int warmachineFunds;
    /**
     * Orthogonal path (including start) for the last {@code executeMoveAlongPath}; consumed when
     * emitting the next {@link com.game.network.protocol.MatchSnapshot}.
     */
    private List<Point> pendingClientMovePathIncludingStart;

    public Unit(UnitType unitType, Player owner, Position position) {
        this.id = UUID.randomUUID().toString();
        this.unitType = unitType;
        this.originalUnitType = null;
        this.owner = owner;
        this.position = position;
        this.health = unitType.startingHealth();
        this.movementSpeed = unitType.movementSpeed();
        this.attackRange = unitType.attackRange();
        this.attackPower = unitType.damage();
        this.armorType = unitType.armorType();
        this.attackType = unitType.attackType();
        this.hasMoved = false;
        this.cloaked = false;
        this.fieldRepairStartedRound = null;
        this.embarkedPassenger = null;
        this.embarkedInTransport = null;
        this.warmachineFunds = unitType == UnitType.Warmachine ? WARMACHINE_STARTING_FUNDS : 0;
    }

    public String getId() { return id; }

    public void setPendingClientMovePathIncludingStart(List<Point> path) {
        this.pendingClientMovePathIncludingStart = path == null ? null : new ArrayList<>(path);
    }

    /** Returns and clears the pending move path for snapshot export. */
    public List<Point> takePendingClientMovePathIncludingStart() {
        List<Point> p = this.pendingClientMovePathIncludingStart;
        this.pendingClientMovePathIncludingStart = null;
        return p;
    }
    public UnitType getUnitType() { return unitType; }
    public Player getOwner() { return owner; }
    public int getHealth() { return health; }

    /** Maximum HP for this type (used for UI and damage ratios). */
    public int getMaxHealth() {
        return unitType.startingHealth();
    }
    public int getMovementSpeed() { return movementSpeed; }
    public int getAttackRange() { return attackRange; }

    /**
     * Closest Manhattan distance at which this unit may strike or return fire. Targets closer than
     * this (inside the "dead zone") cannot be attacked, and this unit does not counterattack
     * attackers standing that close. Computed as {@code ceil(maxRange / 2 - 1)} from max range.
     */
    public int getMinAttackRange() {
        return (int) Math.ceil(attackRange / 2.0 - 1);
    }

    /** HUD / tooltip: {@code "5"} or {@code "2–5"} when a dead zone exists below max range. */
    public String getAttackRangeDisplayString() {
        int minR = getMinAttackRange();
        int maxR = attackRange;
        if (minR > 0 && minR < maxR) {
            return minR + "\u2013" + maxR;
        }
        return Integer.toString(maxR);
    }

    public int getAttackPower() { return attackPower; }
    public ArmorType getArmorType() { return armorType; }
    public AttackType getAttackType() { return attackType; }
    public Position getPosition() { return position; }
    public boolean hasMoved() { return hasMoved; }
    public String[] getAbilities() { return unitType.getAbilities(); }

    public boolean hasAbility(String abilityId) {
        if (abilityId == null) {
            return false;
        }
        for (String a : getAbilities()) {
            if (abilityId.equals(a)) {
                return true;
            }
        }
        return false;
    }

    public boolean isCloaked() {
        return cloaked;
    }

    public void setCloaked(boolean cloaked) {
        this.cloaked = cloaked;
    }

    public boolean isRepairing() {
        return fieldRepairStartedRound != null;
    }

    public Integer getFieldRepairStartedRound() {
        return fieldRepairStartedRound;
    }

    public void setFieldRepairStartedRound(Integer fieldRepairStartedRound) {
        this.fieldRepairStartedRound = fieldRepairStartedRound;
    }

    public Unit getEmbarkedPassenger() {
        return embarkedPassenger;
    }

    public void setEmbarkedPassenger(Unit embarkedPassenger) {
        this.embarkedPassenger = embarkedPassenger;
    }

    public Unit getEmbarkedInTransport() {
        return embarkedInTransport;
    }

    public void setEmbarkedInTransport(Unit embarkedInTransport) {
        this.embarkedInTransport = embarkedInTransport;
    }

    public void clearEmbarkedLinks() {
        this.embarkedPassenger = null;
        this.embarkedInTransport = null;
    }

    public boolean isAircraft() {
        return unitType.movementKind() == UnitType.MovementKind.AIR;
    }
    public void setPosition(Position position) { this.position = position; }
    public void setHasMoved(boolean hasMoved) { this.hasMoved = hasMoved; }

    public int getWarmachineFunds() {
        return warmachineFunds;
    }

    public void setWarmachineFunds(int warmachineFunds) {
        this.warmachineFunds = Math.max(0, warmachineFunds);
    }

    public boolean trySpendWarmachineFunds(int amount) {
        if (amount <= 0 || getUnitType() != UnitType.Warmachine || warmachineFunds < amount) {
            return false;
        }
        warmachineFunds -= amount;
        return true;
    }

    public void addWarmachineFunds(int amount) {
        if (amount <= 0 || getUnitType() != UnitType.Warmachine) {
            return;
        }
        warmachineFunds += amount;
    }

    public void applyDamage(int damage) {
        health = Math.max(0, health - damage);
    }

    /** Restores HP up to max health. */
    public void heal(int amount) {
        if (amount <= 0 || !isAlive()) {
            return;
        }
        health = Math.min(getMaxHealth(), health + amount);
    }

    public boolean isAlive() {
        return health > 0;
    }

    /** {@code true} once converted into a transport (Albatross / Leviathan); see {@link #revertToOriginalType()}. */
    public boolean isInTransportForm() {
        return originalUnitType != null;
    }

    /**
     * Restores {@link #originalUnitType} when hydrating from an authoritative snapshot (converted
     * transport on the wire).
     */
    public void restoreOriginalLandUnitTypeFromSnapshot(UnitType sourceLandType) {
        if (sourceLandType == null || !unitType.isTransport()) {
            return;
        }
        this.originalUnitType = sourceLandType;
    }

    /** Land-unit type stored at conversion time, or {@code null} if this unit isn't a converted transport. */
    public UnitType getOriginalUnitType() {
        return originalUnitType;
    }

    /**
     * Morphs the unit into its transport form (Albatross / Leviathan). Health is rescaled so the
     * percentage of max HP is preserved across the swap (a 50%-damaged Commando becomes a
     * 50%-damaged Albatross). Caller is responsible for enforcing the gameplay rules around when
     * this is allowed (see {@link com.game.engine.PlayableGameSession#convertUnitToAlbatross}).
     */
    public void convertToTransport(UnitType target) {
        if (target == null || !target.isTransport()) {
            throw new IllegalArgumentException("Target must be a transport unit type: " + target);
        }
        UnitType source = this.unitType;
        applyTypeSwapPreservingHealthPercent(target);
        this.originalUnitType = source;
    }

    /**
     * Reverts a transport unit back to the land-unit type it was converted from, again preserving
     * the percentage of max HP. No-op if the unit was not converted.
     */
    public void revertToOriginalType() {
        if (originalUnitType == null) {
            return;
        }
        UnitType target = originalUnitType;
        applyTypeSwapPreservingHealthPercent(target);
        this.originalUnitType = null;
    }

    private void applyTypeSwapPreservingHealthPercent(UnitType target) {
        int oldMax = Math.max(1, this.unitType.startingHealth());
        int oldHp = this.health;
        int newMax = target.startingHealth();
        int newHp = (int) Math.round(oldHp * (double) newMax / (double) oldMax);
        if (oldHp > 0 && newHp <= 0) {
            newHp = 1;
        }
        this.unitType = target;
        this.health = Math.min(newMax, Math.max(0, newHp));
        this.movementSpeed = target.movementSpeed();
        this.attackRange = target.attackRange();
        this.attackPower = target.damage();
        this.armorType = target.armorType();
        this.attackType = target.attackType();
    }
}
