package com.game.model.map;

public class GameMap {
    public static final int MIN_GRID = 10;
    public static final int MAX_GRID = 40;
    public static final int MIN_TEAMS = 2;
    public static final int MAX_TEAMS = 4;

    private int width;
    private int height;
    private Tile[][] tiles;
    private int teamCount = MIN_TEAMS;

    public GameMap(int width, int height) {
        if (!isValidGridSize(width) || !isValidGridSize(height)) {
            throw new IllegalArgumentException("Grid size must be between " + MIN_GRID + " and " + MAX_GRID);
        }
        this.width = width;
        this.height = height;
        this.tiles = new Tile[height][width];
    }

    public static boolean isValidGridSize(int n) {
        return n >= MIN_GRID && n <= MAX_GRID;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getTeamCount() {
        return teamCount;
    }

    public void setTeamCount(int teamCount) {
        if (teamCount < MIN_TEAMS) {
            teamCount = MIN_TEAMS;
        }
        if (teamCount > MAX_TEAMS) {
            teamCount = MAX_TEAMS;
        }
        this.teamCount = teamCount;
    }

    /**
     * Resizes the map in place. Existing tiles are preserved where they overlap; new cells are plain grass.
     */
    public void resize(int newWidth, int newHeight) {
        if (!isValidGridSize(newWidth) || !isValidGridSize(newHeight)) {
            throw new IllegalArgumentException("Grid size must be between " + MIN_GRID + " and " + MAX_GRID);
        }
        Tile[][] next = new Tile[newHeight][newWidth];
        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                if (x < width && y < height && tiles[y][x] != null) {
                    next[y][x] = tiles[y][x];
                } else {
                    next[y][x] = new Tile(TerrainType.PLAINS_1);
                }
            }
        }
        this.tiles = next;
        this.width = newWidth;
        this.height = newHeight;
    }

    public Tile getTile(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return null;
        }
        return tiles[y][x];
    }

    public void setTile(int x, int y, Tile tile) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw new IllegalArgumentException("Out of map bounds");
        }
        tiles[y][x] = tile;
    }
}
