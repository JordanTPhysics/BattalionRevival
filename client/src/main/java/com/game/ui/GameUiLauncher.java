package com.game.ui;

import com.game.model.map.GameMap;
import com.game.model.map.TerrainType;
import com.game.model.map.Tile;

import javax.swing.SwingUtilities;

public final class GameUiLauncher {
    private GameUiLauncher() {
    }

    public static void launchApplication(GameMap map) {
        launchApplication(map, "Skirmish");
    }

    /**
     * @param mapDisplayName shown in the game HUD (e.g. JSON file stem).
     */
    public static void launchApplication(GameMap map, String mapDisplayName) {
        prepareMapForPlay(map);
        SwingUtilities.invokeLater(() -> {
            Theme.installGlobalDefaults();
            StartMenuWindow window = new StartMenuWindow(map, mapDisplayName);
            window.setVisible(true);
        });
    }

    /**
     * Ensures every cell has a tile (e.g. after JSON load). Call before opening {@link GameWindow}.
     */
    public static void prepareMapForPlay(GameMap map) {
        ensureTilesInitialized(map);
    }

    private static void ensureTilesInitialized(GameMap map) {
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                if (map.getTile(x, y) == null) {
                    map.setTile(x, y, new Tile(TerrainType.PLAINS_1));
                }
            }
        }
    }
}
