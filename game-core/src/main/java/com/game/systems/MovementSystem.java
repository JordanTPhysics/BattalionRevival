package com.game.systems;

import com.game.model.Position;
import com.game.model.map.GameMap;
import com.game.model.map.Tile;
import com.game.model.units.FacingDirection;
import com.game.model.units.Unit;

import java.awt.Point;
import java.util.Set;

public class MovementSystem {
    /**
     * Moves the unit and its tile graphics to {@code (tx, ty)} without marking the turn as spent.
     */
    public void relocateUnitWithSprite(GameMap map, Unit unit, int tx, int ty) {
        int sx = unit.getPosition().getX();
        int sy = unit.getPosition().getY();
        Tile sourceTile = map.getTile(sx, sy);
        Tile destTile = map.getTile(tx, ty);
        if (sourceTile == null || destTile == null) {
            return;
        }
        String spriteId = sourceTile.getUnitSpriteId();
        FacingDirection facing = sourceTile.getUnitFacing();
        Integer teamId = sourceTile.getUnitTeamId();
        sourceTile.setUnit(null);
        sourceTile.setUnitSpriteId(null);
        sourceTile.setUnitTeamId(null);
        unit.setPosition(new Position(tx, ty));
        destTile.setUnit(unit);
        destTile.setUnitSpriteId(spriteId);
        destTile.setUnitFacing(facing == null ? FacingDirection.EAST : facing);
        destTile.setUnitTeamId(teamId);
    }

    /**
     * Clears unit reference and tile presentation. Used when temporarily pulling an ally off a tile so another
     * unit can pass through; caller restores with {@link #placeUnitWithSprite}.
     */
    public void clearUnitAndPresentationFromTile(Tile tile) {
        if (tile == null) {
            return;
        }
        tile.setUnit(null);
        tile.setUnitSpriteId(null);
        tile.setUnitTeamId(null);
        tile.setUnitFacing(FacingDirection.EAST);
    }

    /**
     * Places {@code unit} on {@code (tx, ty)} with explicit presentation (no source tile required).
     * The destination tile must not already hold another unit.
     */
    public void placeUnitWithSprite(
        GameMap map,
        Unit unit,
        int tx,
        int ty,
        String spriteId,
        FacingDirection facing,
        Integer teamId
    ) {
        if (map == null || unit == null) {
            return;
        }
        Tile destTile = map.getTile(tx, ty);
        if (destTile == null || destTile.getUnit() != null) {
            return;
        }
        String sid = spriteId != null ? spriteId : unit.getUnitType().name();
        FacingDirection face = facing != null ? facing : FacingDirection.EAST;
        unit.setPosition(new Position(tx, ty));
        destTile.setUnit(unit);
        destTile.setUnitSpriteId(sid);
        destTile.setUnitFacing(face);
        destTile.setUnitTeamId(teamId);
    }

    public boolean moveUnit(GameMap map, Unit unit, Position destination) {
        if (unit.hasMoved()) {
            return false;
        }

        Tile destinationTile = map.getTile(destination.getX(), destination.getY());
        if (destinationTile == null
            || destinationTile.getUnit() != null
            || destinationTile.getUnitSpriteId() != null) {
            return false;
        }

        if (!destinationTile.getTerrainType().canTraverse(unit)) {
            return false;
        }

        Set<Point> reachable = MovementReach.reachableDestinations(map, unit);
        if (!reachable.contains(new Point(destination.getX(), destination.getY()))) {
            return false;
        }

        relocateUnitWithSprite(map, unit, destination.getX(), destination.getY());
        unit.setHasMoved(true);
        return true;
    }
}
