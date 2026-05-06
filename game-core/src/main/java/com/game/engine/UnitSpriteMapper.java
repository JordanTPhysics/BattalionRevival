package com.game.engine;

import com.game.model.units.UnitType;

/**
 * Maps map-editor unit sprite ids to {@link UnitType} for spawning playable {@link com.game.model.units.Unit}s.
 */
public final class UnitSpriteMapper {

    private UnitSpriteMapper() {
    }

    /**
     * Best-effort inference: matches enum name as substring (case-insensitive), then common keywords.
     */
    public static UnitType inferUnitType(String spriteId) {
        if (spriteId == null || spriteId.isBlank()) {
            return UnitType.Raptor;
        }
        String id = spriteId.toLowerCase();
        for (UnitType type : UnitType.values()) {
            if (id.contains(type.name().toLowerCase())) {
                return type;
            }
        }
        if (id.contains("tank") || id.contains("tracked")) {
            return UnitType.Scorpion;
        }
        if (id.contains("ship") || id.contains("boat") || id.contains("naval")) {
            return UnitType.Corvette;
        }
        if (id.contains("infantry") || id.contains("soldier") || id.contains("foot")) {
            return UnitType.Commando;
        }
        if (id.contains("plane") || id.contains("air") || id.contains("jet")) {
            return UnitType.Raptor;
        }
        return UnitType.Commando;
    }
}
