package com.game.model.map;

import com.game.model.units.Unit;
import com.game.model.units.UnitAbilities;
import com.game.model.units.UnitType;
import com.game.model.units.UnitType.MovementKind;

import java.awt.Color;

/**
 * One constant per terrain texture under {@code /assets/terrain/} or
 * {@code /assets/terrain/animated/} (see client {@code AssetManager} load order).
 * {@link #assetStem()} matches the PNG file name
 * without extension (mixed case, as on disk). Movement and passability follow
 * the previous broad rules, keyed off
 * {@link UnitType#movementKind()}.
 */
public enum TerrainType {
    ARCHIPELAGO_2("Archipelago_2", 0.15, Mobility.ARCHIPELAGO_SHALLOW),
    BRIDGE_DOWN("Bridge_Down", 0.4, Mobility.BRIDGE_LAND),
    BRIDGE_HORIZONTAL("Bridge_Horizontal", 0.4, Mobility.BRIDGE_LAND),
    BRIDGE_LEFT("Bridge_Left", 0.4, Mobility.BRIDGE_LAND),
    BRIDGE_LEFTRIGHT("Bridge_LeftRight", 0.4, Mobility.BRIDGE_LAND),
    BRIDGE_RIGHT("Bridge_Right", 0.4, Mobility.BRIDGE_LAND),
    BRIDGE_UP("Bridge_Up", 0.4, Mobility.BRIDGE_LAND),
    BRIDGE_UPDOWN("Bridge_UpDown", 0.4, Mobility.BRIDGE_LAND),
    BRIDGE_VERTICAL("Bridge_Vertical", 0.4, Mobility.BRIDGE_LAND),
    CANYON_DOUBLE_LEFTDOWN("Canyon_Double_LeftDown", 0.3, Mobility.CANYON_ROUGH),
    CANYON_DOUBLE_LEFTDOWN_ONE_RIGHTUP("Canyon_Double_LeftDown_One_RightUp", 0.3, Mobility.CANYON_ROUGH),
    CANYON_DOUBLE_LEFTRIGHT("Canyon_Double_LeftRight", 0.3, Mobility.CANYON_ROUGH),
    CANYON_DOUBLE_LEFTUP("Canyon_Double_LeftUp", 0.3, Mobility.CANYON_ROUGH),
    CANYON_DOUBLE_LEFTUP_ONE_RIGHTDOWN("Canyon_Double_LeftUp_One_RightDown", 0.3, Mobility.CANYON_ROUGH),
    CANYON_DOUBLE_RIGHTDOWN("Canyon_Double_RightDown", 0.3, Mobility.CANYON_ROUGH),
    CANYON_DOUBLE_RIGHTDOWN_ONE_LEFTUP("Canyon_Double_RightDown_One_LeftUp", 0.3, Mobility.CANYON_ROUGH),
    CANYON_DOUBLE_RIGHTUP("Canyon_Double_RightUp", 0.3, Mobility.CANYON_ROUGH),
    CANYON_DOUBLE_RIGHTUP_ONE_LEFTDOWN("Canyon_Double_RightUp_One_LeftDown", 0.3, Mobility.CANYON_ROUGH),
    CANYON_DOUBLE_UPDOWN("Canyon_Double_UpDown", 0.3, Mobility.CANYON_ROUGH),
    CANYON_MAIN("Canyon_Main", 0.3, Mobility.CANYON_ROUGH),
    CANYON_ONE_LEFTDOWN("Canyon_One_LeftDown", 0.3, Mobility.CANYON_ROUGH),
    CANYON_ONE_LEFTDOWN_ONE_RIGHTDOWN("Canyon_One_LeftDown_One_RightDown", 0.3, Mobility.CANYON_ROUGH),
    CANYON_ONE_LEFTDOWN_ONE_RIGHTUP("Canyon_One_LeftDown_One_RightUp", 0.3, Mobility.CANYON_ROUGH),
    CANYON_ONE_LEFTDOWN_ONE_RIGHTUP_ONE_RIGHTDOWN("Canyon_One_LeftDown_One_RightUp_One_RightDown", 0.3,
            Mobility.CANYON_ROUGH),
    CANYON_ONE_LEFTUP("Canyon_One_LeftUp", 0.3, Mobility.CANYON_ROUGH),
    CANYON_ONE_LEFTUP_ONE_LEFTDOWN("Canyon_One_LeftUp_One_LeftDown", 0.3, Mobility.CANYON_ROUGH),
    CANYON_ONE_LEFTUP_ONE_LEFTDOWN_ONE_RIGHTDOWN("Canyon_One_LeftUp_One_LeftDown_One_RightDown", 0.3,
            Mobility.CANYON_ROUGH),
    CANYON_ONE_LEFTUP_ONE_LEFTDOWN_ONE_RIGHTUP("Canyon_One_LeftUp_One_LeftDown_One_RightUp", 0.3,
            Mobility.CANYON_ROUGH),
    FOREST_1("Forest_1", 0.2, Mobility.FOREST_HILLS),
    FOREST_2("Forest_2", 0.2, Mobility.FOREST_HILLS),
    HIGH_BRIDGE_DOWN("High_Bridge_Down", 0.45, Mobility.HIGH_BRIDGE_LAND),
    HIGH_BRIDGE_HORIZONTAL("High_Bridge_Horizontal", 0.45, Mobility.HIGH_BRIDGE_LAND),
    HILLS_1("Hills_1", 0.2, Mobility.FOREST_HILLS),
    HILLS_2("Hills_2", 0.2, Mobility.FOREST_HILLS),
    MOUNTAINS_1("Mountains_1", 0.35, Mobility.MOUNTAIN_PEAK),
    PLAINS_1("Plains_1", 0.0, Mobility.OPEN_PLAINS),
    REEF_1("Reef_1", 0.0, Mobility.REEF_SURFACE),
    ROCK_FORMATION_1("Rock_Formation_1", 0.35, Mobility.MOUNTAIN_PEAK),
    SEA_MAIN("Sea_Main", 0.0, Mobility.SEA_SURFACE),
    SEA_ONE_LEFTDOWN_ONE_RIGHTUP_ONE_RIGHTDOWN("Sea_One_LeftDown_One_RightUp_One_RightDown", 0.0, Mobility.SEA_SURFACE),
    SEA_ONE_LEFTDOWN_ONE_RIGHTDOWN("Sea_One_LeftDown_One_RightDown", 0.0, Mobility.SEA_SURFACE),
    SEA_ONE_LEFTDOWN_ONE_RIGHTUP("Sea_One_LeftDown_One_RightUp", 0.0, Mobility.SEA_SURFACE),
    SEA_ONE_LEFTDOWN_ONE_LEFTUP("Sea_One_LeftDown_One_LeftUp", 0.0, Mobility.SEA_SURFACE),
    SHORE_DOUBLE_LEFTDOWN("Shore_Double_LeftDown", 0.0, Mobility.SHORE_EDGE),
    SHORE_DOUBLE_LEFTUP("Shore_Double_LeftUp", 0.0, Mobility.SHORE_EDGE),
    SHORE_DOUBLE_RIGHTDOWN("Shore_Double_RightDown", 0.0, Mobility.SHORE_EDGE),
    SHORE_DOUBLE_RIGHTUP("Shore_Double_RightUp", 0.0, Mobility.SHORE_EDGE),
    SHORE_SINGLE_DOWN("Shore_Single_Down", 0.0, Mobility.SHORE_EDGE),
    SHORE_SINGLE_LEFT("Shore_Single_Left", 0.0, Mobility.SHORE_EDGE),
    SHORE_TRIPLE_DOWN("Shore_Triple_Down", 0.0, Mobility.SHORE_EDGE),
    SHORE_TRIPLE_LEFT("Shore_Triple_Left", 0.0, Mobility.SHORE_EDGE),
    SHORE_TRIPLE_RIGHT("Shore_Triple_Right", 0.0, Mobility.SHORE_EDGE),
    SHORE_TRIPLE_UP("Shore_Triple_Up", 0.0, Mobility.SHORE_EDGE),
    ARCHIPELAGO_1("Archipelago_1", 0.15, Mobility.ARCHIPELAGO_SHALLOW),
    ARCHIPELAGO_3("Archipelago_3", 0.15, Mobility.ARCHIPELAGO_SHALLOW),
    ARCHIPELAGO_4("Archipelago_4", 0.15, Mobility.ARCHIPELAGO_SHALLOW),
    ENRICHED_ORE_DEPOSIT_1("Enriched_Ore_Deposit_1", 0.0, Mobility.OPEN_PLAINS),
    ENRICHED_ORE_DEPOSIT_2("Enriched_Ore_Deposit_2", 0.0, Mobility.OPEN_PLAINS),
    ORE_DEPOSIT_1("Ore_Deposit_1", 0.0, Mobility.OPEN_PLAINS),
    ORE_DEPOSIT_2("Ore_Deposit_2", 0.0, Mobility.OPEN_PLAINS),
    DEPLETED_ORE_DEPOSIT_1("Depleted_Ore_Deposit_1", 0.0, Mobility.OPEN_PLAINS),
    DEPLETED_ORE_DEPOSIT_2("Depleted_Ore_Deposit_2", 0.0, Mobility.OPEN_PLAINS),
    REEF_2("Reef_2", 0.0, Mobility.REEF_SURFACE),
    REEF_3("Reef_3", 0.0, Mobility.REEF_SURFACE),
    REEF_4("Reef_4", 0.0, Mobility.REEF_SURFACE),
    ROCK_FORMATION_2("Rock_Formation_2", 0.35, Mobility.MOUNTAIN_PEAK),
    ROCK_FORMATION_3("Rock_Formation_3", 0.35, Mobility.MOUNTAIN_PEAK),
    ROCK_FORMATION_4("Rock_Formation_4", 0.35, Mobility.MOUNTAIN_PEAK),
    SEA_DOUBLE_LEFTDOWN("Sea_Double_LeftDown", 0.0, Mobility.SEA_SURFACE),
    SEA_DOUBLE_LEFTDOWN_ONE_RIGHTUP("Sea_Double_LeftDown_One_RightUp", 0.0, Mobility.SEA_SURFACE),
    SEA_DOUBLE_LEFTRIGHT("Sea_Double_LeftRight", 0.0, Mobility.SEA_SURFACE),
    SEA_DOUBLE_LEFTUP("Sea_Double_LeftUp", 0.0, Mobility.SEA_SURFACE),
    SEA_DOUBLE_LEFTUP_ONE_RIGHTDOWN("Sea_Double_LeftUp_One_RightDown", 0.0, Mobility.SEA_SURFACE),
    SEA_DOUBLE_RIGHTDOWN("Sea_Double_RightDown", 0.0, Mobility.SEA_SURFACE),
    SEA_DOUBLE_RIGHTDOWN_ONE_LEFTUP("Sea_Double_RightDown_One_LeftUp", 0.0, Mobility.SEA_SURFACE),
    SEA_DOUBLE_RIGHTUP("Sea_Double_RightUp", 0.0, Mobility.SEA_SURFACE),
    SEA_DOUBLE_RIGHTUP_ONE_LEFTDOWN("Sea_Double_RightUp_One_LeftDown", 0.0, Mobility.SEA_SURFACE),
    SEA_DOUBLE_UPDOWN("Sea_Double_UpDown", 0.0, Mobility.SEA_SURFACE),
    SEA_ONE_LEFTDOWN("Sea_One_LeftDown", 0.0, Mobility.SEA_SURFACE),
    SEA_ONE_LEFTUP("Sea_One_LeftUp", 0.0, Mobility.SEA_SURFACE),
    SEA_ONE_LEFTUP_ONE_LEFTDOWN("Sea_One_LeftUp_One_LeftDown", 0.0, Mobility.SEA_SURFACE),
    SEA_ONE_LEFTUP_ONE_LEFTDOWN_ONE_RIGHTDOWN("Sea_One_LeftUp_One_LeftDown_One_RightDown", 0.0, Mobility.SEA_SURFACE),
    SEA_ONE_LEFTUP_ONE_LEFTDOWN_ONE_RIGHTUP("Sea_One_LeftUp_One_LeftDown_One_RightUp", 0.0, Mobility.SEA_SURFACE),
    SEA_ONE_LEFTUP_ONE_LEFTDOWN_ONE_RIGHTUP_ONE_RIGHTDOWN(
            "Sea_One_LeftUp_One_LeftDown_One_RightUp_One_RightDown", 0.0, Mobility.SEA_SURFACE),
    SEA_ONE_LEFTUP_ONE_RIGHTDOWN("Sea_One_LeftUp_One_RightDown", 0.0, Mobility.SEA_SURFACE),
    SEA_ONE_LEFTUP_ONE_RIGHTUP("Sea_One_LeftUp_One_RightUp", 0.0, Mobility.SEA_SURFACE),
    SEA_ONE_LEFTUP_ONE_RIGHTUP_ONE_RIGHTDOWN("Sea_One_LeftUp_One_RightUp_One_RightDown", 0.0, Mobility.SEA_SURFACE),
    SEA_ONE_RIGHTDOWN("Sea_One_RightDown", 0.0, Mobility.SEA_SURFACE),
    SEA_ONE_RIGHTUP("Sea_One_RightUp", 0.0, Mobility.SEA_SURFACE),
    SEA_ONE_RIGHTUP_ONE_RIGHTDOWN("Sea_One_RightUp_One_RightDown", 0.0, Mobility.SEA_SURFACE),
    SEA_QUADRUPLE_LEFTRIGHTUPDOWN("Sea_Quadruple_LeftRightUpDown", 0.0, Mobility.SEA_SURFACE),
    SEA_SINGLE_DOWN("Sea_Single_Down", 0.0, Mobility.SEA_SURFACE),
    SEA_SINGLE_DOWN_ONE_LEFTUP("Sea_Single_Down_One_LeftUp", 0.0, Mobility.SEA_SURFACE),
    SEA_SINGLE_DOWN_ONE_LEFTUP_ONE_RIGHTUP("Sea_Single_Down_One_LeftUp_One_RightUp", 0.0, Mobility.SEA_SURFACE),
    SEA_SINGLE_DOWN_ONE_RIGHTUP("Sea_Single_Down_One_RightUp", 0.0, Mobility.SEA_SURFACE),
    SEA_SINGLE_LEFT("Sea_Single_Left", 0.0, Mobility.SEA_SURFACE),
    SEA_SINGLE_LEFT_ONE_RIGHTDOWN("Sea_Single_Left_One_RightDown", 0.0, Mobility.SEA_SURFACE),
    SEA_SINGLE_LEFT_ONE_RIGHTUP("Sea_Single_Left_One_RightUp", 0.0, Mobility.SEA_SURFACE),
    SEA_SINGLE_LEFT_ONE_RIGHTUP_ONE_RIGHTDOWN("Sea_Single_Left_One_RightUp_One_RightDown", 0.0, Mobility.SEA_SURFACE),
    SEA_SINGLE_RIGHT("Sea_Single_Right", 0.0, Mobility.SEA_SURFACE),
    SEA_SINGLE_RIGHT_ONE_LEFTDOWN("Sea_Single_Right_One_LeftDown", 0.0, Mobility.SEA_SURFACE),
    SEA_SINGLE_RIGHT_ONE_LEFTUP("Sea_Single_Right_One_LeftUp", 0.0, Mobility.SEA_SURFACE),
    SEA_SINGLE_RIGHT_ONE_LEFTUP_ONE_LEFTDOWN("Sea_Single_Right_One_LeftUp_One_LeftDown", 0.0, Mobility.SEA_SURFACE),
    SEA_SINGLE_UP("Sea_Single_Up", 0.0, Mobility.SEA_SURFACE),
    SEA_SINGLE_UP_ONE_LEFTDOWN("Sea_Single_Up_One_LeftDown", 0.0, Mobility.SEA_SURFACE),
    SEA_SINGLE_UP_ONE_LEFTDOWN_ONE_RIGHTDOWN("Sea_Single_Up_One_LeftDown_One_RightDown", 0.0, Mobility.SEA_SURFACE),
    SEA_SINGLE_UP_ONE_RIGHTDOWN("Sea_Single_Up_One_RightDown", 0.0, Mobility.SEA_SURFACE),
    SEA_TRIPLE_LEFTRIGHTDOWN("Sea_Triple_LeftRightDown", 0.0, Mobility.SEA_SURFACE),
    SEA_TRIPLE_LEFTRIGHTUP("Sea_Triple_LeftRightUp", 0.0, Mobility.SEA_SURFACE),
    SEA_TRIPLE_LEFTUPDOWN("Sea_Triple_LeftUpDown", 0.0, Mobility.SEA_SURFACE),
    SEA_TRIPLE_RIGHTUPDOWN("Sea_Triple_RightUpDown", 0.0, Mobility.SEA_SURFACE),
    SHORE_SINGLE_RIGHT("Shore_Single_Right", 0.0, Mobility.SHORE_EDGE),
    SHORE_SINGLE_UP("Shore_Single_Up", 0.0, Mobility.SHORE_EDGE),
    VOLCANO("Volcano", 0.35, Mobility.NONE),
    WASTELAND("Wasteland", 0.0, Mobility.OPEN_PLAINS);

    private final String assetStem;
    private final double defenseModifier;
    private final Mobility mobility;

    TerrainType(String assetStem, double defenseModifier, Mobility mobility) {
        this.assetStem = assetStem;
        this.defenseModifier = defenseModifier;
        this.mobility = mobility;
    }

    /**
     * PNG file name stem under {@code /assets/terrain/} or {@code /assets/terrain/animated/}
     * (matches on-disk casing).
     */
    public String assetStem() {
        return assetStem;
    }

    public double getDefenseModifier() {
        return defenseModifier;
    }

    public int movementCost(UnitType unitType) {
        if (unitType.movementKind() == MovementKind.AIR) {
            return 1;
        }
        return mobility.movementCost(unitType.movementKind());
    }

    public boolean canTraverse(Unit unit) {
        if (unit.getUnitType().movementKind() == MovementKind.AIR) {
            return true;
        }
        if (unit.hasAbility(UnitAbilities.MASSIVE_HULL) && isCoastalShoreForSeaFactory()) {
            return false;
        }
        return mobility.canTraverse(unit.getUnitType().movementKind());
    }

    public boolean canTraverseKind(MovementKind kind) {
        if (kind == MovementKind.AIR) {
            return true;
        }
        return mobility.canTraverse(kind);
    }

    /**
     * Per-tile movement cost for pathfinding (same rules as
     * {@link #movementCost(UnitType)} for air).
     */
    public int movementCostForKind(MovementKind kind) {
        if (kind == MovementKind.AIR) {
            return 1;
        }
        return mobility.movementCost(kind);
    }

    /**
     * Solid color when the texture asset is missing (editor / game fallback).
     */
    public Color fallbackMapColor() {
        return mobility.fallbackColor();
    }

    /**
     * Shore tiles where land meets sea — factories here may build naval units per
     * game rules.
     */
    public boolean isCoastalShoreForSeaFactory() {
        return name().startsWith("SHORE_");
    }

    /**
     * {@code Hills_*} tiles: ranged units ({@code attackRange > 1}) gain +1 max Manhattan attack range
     * when striking or countering from this tile (see {@link com.game.systems.CombatTerrain}).
     */
    public boolean grantsRangedHillRangeBonus() {
        return this == HILLS_1 || this == HILLS_2;
    }

    public boolean isCanyon() {
        return name().contains("CANYON");
    }

    /** Rich ore: two drill payouts (to regular ore, then to depleted) before exhausted. */
    public boolean isEnrichedOreDepositTerrain() {
        return this == ENRICHED_ORE_DEPOSIT_1 || this == ENRICHED_ORE_DEPOSIT_2;
    }

    /** Standard ore: one drill payout before depleted. */
    public boolean isRegularOreDepositTerrain() {
        return this == ORE_DEPOSIT_1 || this == ORE_DEPOSIT_2;
    }

    public boolean isDepletedOreDepositTerrain() {
        return this == DEPLETED_ORE_DEPOSIT_1 || this == DEPLETED_ORE_DEPOSIT_2;
    }

    /** Ore terrain that still pays out when a Warmachine drills here. */
    public boolean isWarmachineDrillableOreTerrain() {
        return isEnrichedOreDepositTerrain() || isRegularOreDepositTerrain();
    }

    private enum Mobility {
        NONE {
            @Override
            int movementCost(MovementKind k) {
                return Integer.MAX_VALUE;
            }
            @Override
            boolean canTraverse(MovementKind k) {
                return false;
            }
            @Override
            Color fallbackColor() {
                return new Color(230, 0, 0);
            }
        },
        OPEN_PLAINS {
            @Override
            int movementCost(MovementKind k) {
                if (k == MovementKind.NAVAL) {
                    return Integer.MAX_VALUE;
                }
                return 1;
            }

            @Override
            boolean canTraverse(MovementKind k) {
                return k != MovementKind.NAVAL;
            }

            @Override
            Color fallbackColor() {
                return new Color(116, 176, 88);
            }
        },
        FOREST_HILLS {
            @Override
            int movementCost(MovementKind k) {
                if (k == MovementKind.NAVAL) {
                    return Integer.MAX_VALUE;
                }
                if (k == MovementKind.WHEELED || k == MovementKind.TRACKED) {
                    return 2;
                }
                return 1;
            }

            @Override
            boolean canTraverse(MovementKind k) {
                return k != MovementKind.NAVAL;
            }

            @Override
            Color fallbackColor() {
                return new Color(57, 122, 55);
            }
        },
        CANYON_ROUGH {
            @Override
            int movementCost(MovementKind k) {
                if (k == MovementKind.NAVAL) {
                    return Integer.MAX_VALUE;
                }

                return 1;
            }

            @Override
            boolean canTraverse(MovementKind k) {
                return k != MovementKind.NAVAL;
            }

            @Override
            Color fallbackColor() {
                return new Color(130, 130, 130);
            }
        },
        BRIDGE_LAND {
            @Override
            int movementCost(MovementKind k) {
                if (k == MovementKind.NAVAL) {
                    return Integer.MAX_VALUE;
                }

                return 1;
            }

            @Override
            boolean canTraverse(MovementKind k) {
                return k != MovementKind.NAVAL;
            }

            @Override
            Color fallbackColor() {
                return new Color(65, 134, 201);
            }
        },
        HIGH_BRIDGE_LAND {
            @Override
            int movementCost(MovementKind k) {
                return 1;
            }

            @Override
            boolean canTraverse(MovementKind k) {
                return true;
            }

            @Override
            Color fallbackColor() {
                return new Color(88, 168, 198);
            }
        },
        MOUNTAIN_PEAK {
            @Override
            int movementCost(MovementKind k) {
                if (k == MovementKind.AIR) {
                    return 1;
                }
                if (k == MovementKind.FOOT) {
                    return 3;
                }
                return Integer.MAX_VALUE;
            }

            @Override
            boolean canTraverse(MovementKind k) {
                return k == MovementKind.FOOT || k == MovementKind.AIR;
            }

            @Override
            Color fallbackColor() {
                return new Color(110, 110, 120);
            }
        },
        SEA_SURFACE {
            @Override
            int movementCost(MovementKind k) {
                if (k == MovementKind.NAVAL || k == MovementKind.AIR) {
                    return 1;
                }
                return Integer.MAX_VALUE;
            }

            @Override
            boolean canTraverse(MovementKind k) {
                return k == MovementKind.NAVAL || k == MovementKind.AIR;
            }

            @Override
            Color fallbackColor() {
                return new Color(65, 134, 201);
            }
        },
        REEF_SURFACE {
            @Override
            int movementCost(MovementKind k) {
                if (k == MovementKind.NAVAL) {
                    return 2;
                }
                return 1;
            }

            @Override
            boolean canTraverse(MovementKind k) {
                return k == MovementKind.NAVAL || k == MovementKind.AIR;
            }

            @Override
            Color fallbackColor() {
                return new Color(88, 168, 198);
            }
        },
        SHORE_EDGE {
            @Override
            int movementCost(MovementKind k) {
                return 1;
            }

            @Override
            boolean canTraverse(MovementKind k) {
                return true;
            }

            @Override
            Color fallbackColor() {
                return new Color(130, 130, 130);
            }
        },
        ARCHIPELAGO_SHALLOW {
            @Override
            int movementCost(MovementKind k) {
                if (k == MovementKind.NAVAL) {
                    return 2;
                }
                return 1;
            }

            @Override
            boolean canTraverse(MovementKind k) {
                return k == MovementKind.AIR || k == MovementKind.NAVAL;
            }

            @Override
            Color fallbackColor() {
                return new Color(88, 168, 198);
            }
        };

        abstract int movementCost(MovementKind k);

        abstract boolean canTraverse(MovementKind k);

        abstract Color fallbackColor();
    }
}
