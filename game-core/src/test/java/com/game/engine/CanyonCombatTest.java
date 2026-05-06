package com.game.engine;

import com.game.model.map.GameMap;
import com.game.model.map.TerrainType;
import com.game.model.map.Tile;
import com.game.model.units.Unit;
import com.game.systems.CombatTerrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanyonCombatTest {

    @Test
    void rangedUnitCannotAttackEnemyOnCanyonTile() {
        GameMap map = baseMap();
        map.setTile(1, 0, new Tile(TerrainType.CANYON_MAIN));
        placeUnit(map, 0, 0, "Mortar", 1);
        placeUnit(map, 1, 0, "Scorpion", 2);
        PlayableGameSession session = new PlayableGameSession(map);
        Unit mortar = map.getTile(0, 0).getUnit();
        Unit target = map.getTile(1, 0).getUnit();
        assertTrue(CombatTerrain.isRangedAttacker(mortar));
        assertTrue(CombatTerrain.isUnitProtectedByCanyon(map, target));
        assertFalse(session.canExecuteAttack(mortar, target));
    }

    @Test
    void meleeUnitCanAttackEnemyOnCanyonTile() {
        GameMap map = baseMap();
        map.setTile(1, 0, new Tile(TerrainType.CANYON_MAIN));
        placeUnit(map, 0, 0, "Scorpion", 1);
        placeUnit(map, 1, 0, "Mortar", 2);
        PlayableGameSession session = new PlayableGameSession(map);
        Unit melee = map.getTile(0, 0).getUnit();
        Unit target = map.getTile(1, 0).getUnit();
        assertFalse(CombatTerrain.isRangedAttacker(melee));
        assertTrue(CombatTerrain.isUnitProtectedByCanyon(map, target));
        assertTrue(session.canExecuteAttack(melee, target));
    }

    private static GameMap baseMap() {
        GameMap map = new GameMap(10, 10);
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                map.setTile(x, y, new Tile(TerrainType.PLAINS_1));
            }
        }
        map.setTeamCount(2);
        return map;
    }

    private static void placeUnit(GameMap map, int x, int y, String spriteId, int teamId) {
        Tile t = map.getTile(x, y);
        t.setUnitSpriteId(spriteId);
        t.setUnitTeamId(teamId);
        t.setUnit(null);
    }
}
