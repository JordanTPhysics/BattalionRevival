package com.game.model.units;

/**
 * Ability id strings referenced from {@link UnitType#getAbilities()}. Add matching entries on unit types as needed.
 */
public final class UnitAbilities {
    private UnitAbilities() {
    }

    /**
     * Non-air units with this ability in {@link UnitType#getAbilities()} may attack {@link UnitType.MovementKind#AIR}
     * targets. Air units may always attack other air units.
     */
    public static final String ANTI_AIR = "AntiAir";

    /**
     * Units with this ability in {@link UnitType#getAbilities()} get 20% bonus damage when attacking first.
     * targets.
     */
    public static final String BLITZKRIEG = "Blitzkrieg";

    /**
     * Units with this ability in {@link UnitType#getAbilities()} will attack cloaked targets discovered in their path and get a 20% damage bonus.
     */
    public static final String TRACKER = "Tracker";

    /**
     * Units with this ability in {@link UnitType#getAbilities()} after moving will disappear from enemies' view and
     * deal double damage if attacking while cloaked. Orthogonal adjacency to an enemy removes cloak when that unit's
     * turn action is spent (move-only or after attack resolution); the ability itself remains.
     */
    public static final String CLOAKER = "Cloaker";

    /**
     * Units with this ability in {@link UnitType#getAbilities()} emit a jamming area of effect (2 squares manhattan distance) that blocks the movement of aircraft and uncloaks cloaked units.
     */
    public static final String JAMMER = "Jammer";

    /**
     * Units with this ability in {@link UnitType#getAbilities()} deal double damage when attacking targets with light armor.
     */
    public static final String RAPIDFIRE = "RapidFire";

        /**
     * Units with this ability in {@link UnitType#getAbilities()} deal 1.5x damage when attacking targets with light armor.
     */
    public static final String BARRAGE = "Barrage";

    /**
     * Units with this ability in {@link UnitType#getAbilities()} deals an additional hit to the unit behind the target for 60% damage.
     */
    public static final String PIERCING = "Piercing";

    /**
     * Units with this ability in {@link UnitType#getAbilities()} are able to hit U-boats.
     */
    public static final String ANTI_SUBMARINE = "AntiSubmarine";

    /**
     * Units with this ability in {@link UnitType#getAbilities()} are able to capture enemy and neutral structures for their team by remaining on the tile for 2 full turns.
     */
    public static final String CONQUEROR = "Conqueror";

    /**
     * Units with this ability in {@link UnitType#getAbilities()} deal double damage when attacking a unit on a structure, and a unit with heavy armor.
     */
    public static final String EXPLOSIVE = "Explosive";

    /**
     * Units with this ability in {@link UnitType#getAbilities()} keep their per-turn action after
     * destroying the primary target in combat (living attackers can move and/or attack again this turn).
     */
    public static final String SCAVENGER = "Scavenger";
    /**
     * Units with this ability in {@link UnitType#getAbilities()} have a 20% chance to survive a strike that would normally destroy them.
     */
    public static final String STALWART = "Stalwart";

    /**
     * Units with this ability in {@link UnitType#getAbilities()} are crucial for survival. If the last unit of a team with this ability is eliminated, the team loses.
     */
    public static final String KINGPIN = "Kingpin";

    /**
     * Units with this ability in {@link UnitType#getAbilities()} do not prevent defeat if they are the last unit(s) of their team.
     */
    public static final String AIMLESS = "Aimless";

    /**
     * Units with this ability in {@link UnitType#getAbilities()} heals 10% of its max health each turn.
     */
    public static final String MAINTENANCE = "Maintenance";

    /**
     * Units with this ability in {@link UnitType#getAbilities()} heals 10% of nearby (2 squares manhattan distance) friendly units max health each turn.
     */
    public static final String RESUPPLY = "Resupply";

    /**
     * Units with this ability in {@link UnitType#getAbilities()} prevent enemy counterattack when striking.
     */
    public static final String STUNNING = "Stunning";

    /**
     * Units with this ability in {@link UnitType#getAbilities()} cannot enter shallow waters. (shore tiles)
     */
    public static final String MASSIVE_HULL = "MassiveHull";

    /**
     * Units with this ability in {@link UnitType#getAbilities()} deal 15% less damage when counterattacking.
     */
    public static final String BEHEMOTH = "Behemoth";

}
