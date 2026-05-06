package com.game.model.map;

import com.game.model.structures.Structure;
import com.game.model.units.FacingDirection;
import com.game.model.units.Unit;

public class Tile {
    private TerrainType terrainType;
    private Structure structure;
    private Unit unit;
    private String unitSpriteId;
    private FacingDirection unitFacing = FacingDirection.EAST;
    /** Map editor: owning team for placed structure (1–map team count), or {@code null} if neutral. */
    private Integer structureTeamId;
    /** Map editor: owning team for placed unit sprite (1–4), or null if none. */
    private Integer unitTeamId;
    /** Ore-bearing ground: War Machines standing here may drill for funds. */
    private boolean oreDeposit;

    public Tile(TerrainType terrainType) {
        this.terrainType = terrainType;
    }

    public TerrainType getTerrainType() {
        return terrainType;
    }

    public void setTerrainType(TerrainType terrainType) {
        this.terrainType = terrainType;
    }

    public Structure getStructure() {
        return structure;
    }

    public void setStructure(Structure structure) {
        this.structure = structure;
    }

    public Unit getUnit() {
        return unit;
    }

    public void setUnit(Unit unit) {
        this.unit = unit;
    }

    public String getUnitSpriteId() {
        return unitSpriteId;
    }

    public void setUnitSpriteId(String unitSpriteId) {
        this.unitSpriteId = unitSpriteId;
    }

    public FacingDirection getUnitFacing() {
        return unitFacing;
    }

    public void setUnitFacing(FacingDirection unitFacing) {
        this.unitFacing = unitFacing == null ? FacingDirection.EAST : unitFacing;
    }

    public Integer getStructureTeamId() {
        return structureTeamId;
    }

    public void setStructureTeamId(Integer structureTeamId) {
        this.structureTeamId = structureTeamId;
    }

    public Integer getUnitTeamId() {
        return unitTeamId;
    }

    public void setUnitTeamId(Integer unitTeamId) {
        this.unitTeamId = unitTeamId;
    }

    public boolean isOreDeposit() {
        return oreDeposit;
    }

    public void setOreDeposit(boolean oreDeposit) {
        this.oreDeposit = oreDeposit;
    }
}
