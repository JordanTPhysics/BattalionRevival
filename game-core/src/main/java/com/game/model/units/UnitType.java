package com.game.model.units;

/**
 * Base stats for each unit. {@link MovementKind} drives terrain cost and passability in
 * {@link com.game.model.map.TerrainType}; {@link Unit} copies these values at construction.
 */
public enum UnitType {
    Albatross(50, 0, MovementKind.AIR, 6, 1, Unit.ArmorType.LIGHT, Unit.AttackType.NONE),
    Battlecruiser(140, 55, MovementKind.NAVAL, 4, 6, Unit.ArmorType.HEAVY, Unit.AttackType.HEAVY),
    Blockade(100, 0, MovementKind.TRACKED, 0, 0, Unit.ArmorType.HEAVY, Unit.AttackType.NONE),
    Commando(50, 25, MovementKind.FOOT, 4, 1, Unit.ArmorType.LIGHT, Unit.AttackType.LIGHT),
    Condor(55, 60, MovementKind.AIR, 5, 1, Unit.ArmorType.LIGHT, Unit.AttackType.HEAVY),
    Corvette(90, 45, MovementKind.NAVAL, 6, 1, Unit.ArmorType.HEAVY, Unit.AttackType.MEDIUM),
    Hunter(90, 35, MovementKind.NAVAL, 5, 1, Unit.ArmorType.MEDIUM, Unit.AttackType.LIGHT),
    Raptor(50, 20, MovementKind.AIR, 7, 1, Unit.ArmorType.LIGHT, Unit.AttackType.LIGHT),
    Scorpion(70, 35, MovementKind.TRACKED, 6, 1, Unit.ArmorType.MEDIUM, Unit.AttackType.MEDIUM),
    Leviathan(100, 0, MovementKind.NAVAL, 6, 2, Unit.ArmorType.HEAVY, Unit.AttackType.NONE),
    Spider(50, 30, MovementKind.FOOT, 4, 1, Unit.ArmorType.LIGHT, Unit.AttackType.LIGHT),
    Mortar(55, 40, MovementKind.WHEELED, 4, 3, Unit.ArmorType.LIGHT, Unit.AttackType.LIGHT),
    Lancer(70, 35, MovementKind.TRACKED, 6, 1, Unit.ArmorType.MEDIUM, Unit.AttackType.MEDIUM),
    Jammer(40, 0, MovementKind.WHEELED, 4, 1, Unit.ArmorType.LIGHT, Unit.AttackType.NONE),
    Flak(70, 20, MovementKind.TRACKED, 5, 1, Unit.ArmorType.MEDIUM, Unit.AttackType.LIGHT),
    Intrepid(50, 25, MovementKind.NAVAL, 5, 1, Unit.ArmorType.LIGHT, Unit.AttackType.LIGHT),
    Hcommando(50, 40, MovementKind.FOOT, 4, 1, Unit.ArmorType.LIGHT, Unit.AttackType.HEAVY),
    Rocket(40, 50, MovementKind.WHEELED, 4, 5, Unit.ArmorType.LIGHT, Unit.AttackType.HEAVY),
    Turret(100, 45, MovementKind.TRACKED, 0, 5, Unit.ArmorType.HEAVY, Unit.AttackType.MEDIUM),
    Uboat(40, 40, MovementKind.NAVAL, 4, 1, Unit.ArmorType.LIGHT, Unit.AttackType.HEAVY),
    Vulture(60, 35, MovementKind.AIR, 6, 1, Unit.ArmorType.LIGHT, Unit.AttackType.MEDIUM),
    Warmachine(160, 48, MovementKind.TRACKED, 3, 3, Unit.ArmorType.HEAVY, Unit.AttackType.HEAVY),
    Stealth(40, 35, MovementKind.TRACKED, 5, 1, Unit.ArmorType.LIGHT, Unit.AttackType.MEDIUM),
    Annihilator(140, 70, MovementKind.TRACKED, 4, 1, Unit.ArmorType.HEAVY, Unit.AttackType.HEAVY);

    private final int startingHealth;
    private final int damage;
    private final MovementKind movementKind;
    private final int movementSpeed;
    private final int attackRange;
    private final Unit.ArmorType armorType;
    private final Unit.AttackType attackType;

    UnitType(
        int startingHealth,
        int damage,
        MovementKind movementKind,
        int movementSpeed,
        int attackRange,
        Unit.ArmorType armorType,
        Unit.AttackType attackType
    ) {
        this.startingHealth = startingHealth;
        this.damage = damage;
        this.movementKind = movementKind;
        this.movementSpeed = movementSpeed;
        this.attackRange = attackRange;
        this.armorType = armorType;
        this.attackType = attackType;
    }

    public int startingHealth() {
        return startingHealth;
    }

    public int damage() {
        return damage;
    }

    public MovementKind movementKind() {
        return movementKind;
    }

    public int movementSpeed() {
        return movementSpeed;
    }

    public int attackRange() {
        return attackRange;
    }

    public Unit.ArmorType armorType() {
        return armorType;
    }
    public Unit.AttackType attackType() {
        return attackType;
    }


    public String[] getAbilities() {
        switch (this) {
            case Albatross:
                return new String[] { };
            case Battlecruiser:
                return new String[] { UnitAbilities.MASSIVE_HULL };
            case Blockade:
                return new String[] { UnitAbilities.MAINTENANCE, UnitAbilities.AIMLESS };
            case Commando:
                return new String[] { UnitAbilities.TRACKER, UnitAbilities.CONQUEROR };
            case Condor:
                return new String[] { UnitAbilities.ANTI_SUBMARINE, UnitAbilities.EXPLOSIVE };
            case Corvette:
                return new String[] { UnitAbilities.BLITZKRIEG, UnitAbilities.MASSIVE_HULL };
            case Hunter:
                return new String[] { UnitAbilities.ANTI_AIR, UnitAbilities.ANTI_SUBMARINE, UnitAbilities.BARRAGE };
            case Raptor:
                return new String[] { UnitAbilities.ANTI_AIR, UnitAbilities.RAPIDFIRE };
            case Scorpion:
                return new String[] { UnitAbilities.BLITZKRIEG };
            case Leviathan:
                return new String[] { };
            case Spider:
                return new String[] { UnitAbilities.STUNNING };
            case Mortar:
                return new String[] { };
            case Lancer:
                return new String[] { UnitAbilities.PIERCING };
            case Jammer:
                return new String[] { UnitAbilities.JAMMER };
            case Flak:
                return new String[] { UnitAbilities.ANTI_AIR, UnitAbilities.RAPIDFIRE };
            case Intrepid:
                return new String[] { UnitAbilities.CONQUEROR, UnitAbilities.SCAVENGER };
            case Hcommando:
                return new String[] { UnitAbilities.CONQUEROR, UnitAbilities.EXPLOSIVE };
            case Rocket:
                return new String[] { UnitAbilities.ANTI_AIR, UnitAbilities.ANTI_SUBMARINE, UnitAbilities.EXPLOSIVE };
            case Turret:
                return new String[] { UnitAbilities.ANTI_AIR, UnitAbilities.AIMLESS, UnitAbilities.MAINTENANCE };
            case Uboat:
                return new String[] { UnitAbilities.CLOAKER, UnitAbilities.ANTI_SUBMARINE };
            case Vulture:
                return new String[] { UnitAbilities.SCAVENGER };
            case Warmachine:
                return new String[] { UnitAbilities.KINGPIN, UnitAbilities.RESUPPLY };
            case Stealth:
                return new String[] { UnitAbilities.CLOAKER };
            case Annihilator:
                return new String[] { UnitAbilities.BEHEMOTH };
            default:
                return new String[] {};
        }
    }

    /** Used when grouping units at factories (land / sea / air). */
    public enum FactoryBuildCategory {
        LAND,
        SEA,
        AIR
    }

    public FactoryBuildCategory factoryBuildCategory() {
        // if(movementSpeed == 0) return null;

        return switch (movementKind) {
            case NAVAL -> FactoryBuildCategory.SEA;
            case AIR -> FactoryBuildCategory.AIR;
            case WHEELED, TRACKED, FOOT-> FactoryBuildCategory.LAND;
        };
    }

    /**
     * Transport types are not produced at factories; they are created in place by
     * converting an existing land unit (see {@link com.game.engine.PlayableGameSession}).
     */
    public boolean isTransport() {
        return this == Albatross || this == Leviathan;
    }

    /** Whether this land-unit type may be converted into an {@link #Albatross} (sky transport). */
    public boolean canConvertToAlbatross() {
        return movementKind == MovementKind.FOOT;
    }

    /** Whether this land-unit type may be converted into a {@link #Leviathan} (sea transport). */
    public boolean canConvertToLeviathan() {
        return movementKind == MovementKind.FOOT
            || movementKind == MovementKind.WHEELED
            || movementKind == MovementKind.TRACKED;
    }

    public enum MovementKind {
        FOOT,
        WHEELED,
        TRACKED,
        NAVAL,
        AIR
    }
}
