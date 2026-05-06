package com.game.server;

import com.game.model.map.GameMap;
import com.game.model.map.TerrainType;
import com.game.model.map.Tile;

final class DemoMaps {
    private DemoMaps() {
    }

    static GameMap plains20() {
        GameMap map = new GameMap(20, 20);
        map.setTeamCount(2);
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                map.setTile(x, y, new Tile(TerrainType.PLAINS_1));
            }
        }
        return map;
    }
}
