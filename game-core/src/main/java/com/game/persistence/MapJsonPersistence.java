package com.game.persistence;

import com.game.model.map.GameMap;
import com.game.model.map.TerrainType;
import com.game.model.map.Tile;
import com.game.model.structures.Structure;
import com.game.model.structures.StructureType;
import com.game.model.units.FacingDirection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MapJsonPersistence {
    private static final Pattern WIDTH_PATTERN = Pattern.compile("\"width\"\\s*:\\s*(\\d+)");
    private static final Pattern HEIGHT_PATTERN = Pattern.compile("\"height\"\\s*:\\s*(\\d+)");
    private static final Pattern TEAMCOUNT_PATTERN = Pattern.compile("\"teamCount\"\\s*:\\s*(\\d+)");

    /**
     * Terrain names are UPPER_SNAKE; {@link StructureType} names are PascalCase, so structure capture allows a–z.
     */
    private static final Pattern TILE_PATTERN = Pattern.compile(
        "\\{\\s*\"terrain\"\\s*:\\s*\"([A-Z0-9_]+)\"\\s*,\\s*\"structure\"\\s*:\\s*(null|\"([A-Za-z0-9_]+)\")\\s*,\\s*\"structureTeam\"\\s*:\\s*(null|\\d+)\\s*,"
            + "\\s*\"unitSprite\"\\s*:\\s*(null|\"([^\"]*)\")\\s*,\\s*\"unitTeam\"\\s*:\\s*(null|\\d+)\\s*,\\s*\"unitFacing\"\\s*:\\s*(null|\"([A-Z_]+)\")"
            + "(?:\\s*,\\s*\"oreDeposit\"\\s*:\\s*(true|false))?"
            + "\\s*\\}"
    );

    private MapJsonPersistence() {
    }

    public static void save(Path path, GameMap map) throws IOException {
        String json = serialize(map);
        Files.createDirectories(path.getParent());
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    /**
     * Same JSON as {@link #save(Path, GameMap)} would write, for uploads and tests.
     */
    public static String serialize(GameMap map) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"width\": ").append(map.getWidth()).append(",\n");
        json.append("  \"height\": ").append(map.getHeight()).append(",\n");
        json.append("  \"teamCount\": ").append(map.getTeamCount()).append(",\n");
        json.append("  \"tiles\": [\n");

        for (int y = 0; y < map.getHeight(); y++) {
            json.append("    [");
            for (int x = 0; x < map.getWidth(); x++) {
                Tile tile = map.getTile(x, y);
                TerrainType terrain = tile == null ? TerrainType.PLAINS_1 : tile.getTerrainType();
                StructureType structureType = (tile == null || tile.getStructure() == null) ? null : tile.getStructure().getType();
                Integer structureTeam = tile == null ? null : tile.getStructureTeamId();
                String unitSpriteId = tile == null ? null : tile.getUnitSpriteId();
                Integer unitTeam = tile == null ? null : tile.getUnitTeamId();
                FacingDirection unitFacing = tile == null ? FacingDirection.EAST : tile.getUnitFacing();

                json.append("{\"terrain\":\"").append(terrain.name()).append("\",\"structure\":");
                if (structureType == null) {
                    json.append("null");
                } else {
                    json.append("\"").append(structureType.name()).append("\"");
                }
                json.append(",\"structureTeam\":");
                if (structureTeam == null) {
                    json.append("null");
                } else {
                    json.append(structureTeam);
                }
                json.append(",\"unitSprite\":");
                if (unitSpriteId == null) {
                    json.append("null");
                } else {
                    json.append("\"").append(escapeJson(unitSpriteId)).append("\"");
                }
                json.append(",\"unitTeam\":");
                if (unitTeam == null) {
                    json.append("null");
                } else {
                    json.append(unitTeam);
                }
                json.append(",\"unitFacing\":");
                if (unitFacing == null) {
                    json.append("null");
                } else {
                    json.append("\"").append(unitFacing.name()).append("\"");
                }
                json.append(",\"oreDeposit\":").append(tile != null && tile.isOreDeposit());
                json.append("}");

                if (x < map.getWidth() - 1) {
                    json.append(",");
                }
            }
            json.append("]");
            if (y < map.getHeight() - 1) {
                json.append(",");
            }
            json.append("\n");
        }

        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    /**
     * Reads a map file and returns a new {@link GameMap} with dimensions and data from the file.
     */
    public static GameMap load(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        return parse(content);
    }

    /**
     * Parses map JSON from memory (server-side validation, uploads).
     */
    public static GameMap parse(String content) {
        int width = readInt(content, WIDTH_PATTERN, "width");
        int height = readInt(content, HEIGHT_PATTERN, "height");
        if (!GameMap.isValidGridSize(width) || !GameMap.isValidGridSize(height)) {
            throw new IllegalArgumentException("Map width/height must be between " + GameMap.MIN_GRID + " and " + GameMap.MAX_GRID);
        }

        int teamCount = GameMap.MIN_TEAMS;
        Matcher teamMatcher = TEAMCOUNT_PATTERN.matcher(content);
        if (teamMatcher.find()) {
            teamCount = Integer.parseInt(teamMatcher.group(1));
        }
        if (teamCount < GameMap.MIN_TEAMS || teamCount > GameMap.MAX_TEAMS) {
            throw new IllegalArgumentException("teamCount must be between " + GameMap.MIN_TEAMS + " and " + GameMap.MAX_TEAMS);
        }

        GameMap map = new GameMap(width, height);
        map.setTeamCount(teamCount);

        Matcher tileMatcher = TILE_PATTERN.matcher(content);
        int expectedTiles = width * height;
        int index = 0;
        while (tileMatcher.find()) {
            if (index >= expectedTiles) {
                break;
            }

            String terrainName = tileMatcher.group(1);
            String structureName = tileMatcher.group(3);
            String structureTeamStr = tileMatcher.group(4);
            String unitSpriteOuter = tileMatcher.group(5);
            String unitSpriteId = (unitSpriteOuter == null || "null".equals(unitSpriteOuter)) ? null : tileMatcher.group(6);
            String unitTeamStr = tileMatcher.group(7);
            String facingOuter = tileMatcher.group(8);
            String oreStr = tileMatcher.group(10);

            int x = index % width;
            int y = index / width;
            Tile tile = new Tile(TerrainType.valueOf(terrainName));
            map.setTile(x, y, tile);

            if (structureName == null) {
                tile.setStructure(null);
                tile.setStructureTeamId(null);
            } else {
                tile.setStructure(new Structure(StructureType.valueOf(structureName), null));
                tile.setStructureTeamId(parseTeamId(structureTeamStr, teamCount));
            }
            tile.setUnitSpriteId(unitSpriteId);
            tile.setUnitTeamId(parseTeamId(unitTeamStr, teamCount));
            if (facingOuter == null || "null".equals(facingOuter)) {
                tile.setUnitFacing(FacingDirection.EAST);
            } else {
                tile.setUnitFacing(FacingDirection.valueOf(tileMatcher.group(9)));
            }
            tile.setOreDeposit("true".equals(oreStr));

            index++;
        }

        if (index != expectedTiles) {
            throw new IllegalArgumentException("Invalid map JSON: expected " + expectedTiles + " tiles but found " + index);
        }
        return map;
    }

    private static Integer parseTeamId(String raw, int teamCount) {
        if (raw == null || "null".equalsIgnoreCase(raw)) {
            return null;
        }
        int v = Integer.parseInt(raw);
        if (v < 1 || v > teamCount) {
            throw new IllegalArgumentException("Team id out of range for this map: " + v);
        }
        return v;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static int readInt(String content, Pattern pattern, String fieldName) {
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing field: " + fieldName);
        }
        return Integer.parseInt(matcher.group(1));
    }
}
