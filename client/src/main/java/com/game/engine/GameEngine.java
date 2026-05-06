package com.game.engine;

import com.game.model.Player;
import com.game.model.map.GameMap;
import com.game.model.map.Tile;
import com.game.model.map.TerrainType;
import com.game.systems.EconomySystem;
import com.game.ui.GameUiLauncher;
import com.game.persistence.MapJsonPersistence;
import com.game.persistence.MapsWorkspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class GameEngine {
    private final GameMap map;
    private final List<Player> players;
    private final TurnManager turnManager;
    private final EconomySystem economySystem;

    public GameEngine(GameMap map, List<Player> players) {
        this.map = map;
        this.players = new ArrayList<>(players);
        this.turnManager = new TurnManager(players);
        this.economySystem = new EconomySystem();
    }

    public void startTurn() {
        Player activePlayer = turnManager.getCurrentPlayer();
        economySystem.applyTurnIncome(activePlayer, turnManager.getRoundNumber());
        activePlayer.resetTurnState();
    }

    public void endTurn() {
        turnManager.nextTurn();
    }

    public Player getActivePlayer() {
        return turnManager.getCurrentPlayer();
    }

    public static void main(String[] args) {
        Path mapPath = resolveMapPath(args);
        String mapLabel;
        GameMap map;
        if (mapPath != null) {
            try {
                map = MapJsonPersistence.load(mapPath);
                mapLabel = displayNameFromPath(mapPath);
            } catch (IOException | IllegalArgumentException ex) {
                System.err.println("Could not load map " + mapPath + ": " + ex.getMessage());
                map = createFallbackPlainsMap();
                mapLabel = "Fallback 20×20 (load error)";
            }
        } else {
            map = createFallbackPlainsMap();
            mapLabel = "Skirmish 20×20";
        }

        List<Player> players = List.of(
            new Player("Red"),
            new Player("Blue")
        );

        GameEngine engine = new GameEngine(map, players);
        engine.startTurn();
        GameUiLauncher.launchApplication(map, mapLabel);
    }

    /**
     * First CLI argument if it is an existing file, otherwise {@code maps/default.json} when present.
     */
    private static Path resolveMapPath(String[] args) {
        if (args.length > 0) {
            Path p = Paths.get(args[0]);
            if (Files.isRegularFile(p)) {
                return p;
            }
            System.err.println("Map file not found: " + p.toAbsolutePath());
        }
        Path def = MapsWorkspace.mapsDirectory().resolve("default.json");
        if (Files.isRegularFile(def)) {
            return def;
        }
        return null;
    }

    private static String displayNameFromPath(Path mapPath) {
        String name = mapPath.getFileName().toString();
        if (name.toLowerCase().endsWith(".json")) {
            return name.substring(0, name.length() - 5);
        }
        return name;
    }

    private static GameMap createFallbackPlainsMap() {
        GameMap map = new GameMap(20, 20);
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                map.setTile(x, y, new Tile(TerrainType.PLAINS_1));
            }
        }
        return map;
    }
}
