package com.game.ui;

import com.game.model.map.GameMap;
import com.game.model.map.TerrainType;
import com.game.model.map.Tile;
import com.game.model.structures.Structure;
import com.game.model.structures.StructureType;
import com.game.model.units.FacingDirection;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

public class MapBuilderPanel extends JPanel {
    public static final int EDGE_PADDING_TILES = 10;

    private static final int BASE_TILE_SIZE = 32;
    private static final int MIN_TILE_SIZE = 16;
    private static final int MAX_TILE_SIZE = 96;

    private GameMap map;
    private final AssetManager assetManager;
    private final Consumer<String> statusConsumer;
    private final Runnable zoomChangeListener;
    private int tileSize = BASE_TILE_SIZE;
    private Point selectedGridCell;

    private Timer terrainStripAnimTimer;

    private TerrainType selectedTerrain = TerrainType.PLAINS_1;
    private StructureType selectedStructure;
    private String selectedUnitSpriteId;
    private int brushTeamId = 1;
    /** When enabled, LMB toggles ore-deposit overlay without changing terrain. */
    private boolean oreDepositBrushMode;

    public MapBuilderPanel(GameMap map, AssetManager assetManager, Consumer<String> statusConsumer, Runnable zoomChangeListener) {
        this.map = map;
        this.assetManager = assetManager;
        this.statusConsumer = statusConsumer;
        this.zoomChangeListener = zoomChangeListener;

        updatePreferredSize();
        setBackground(new Color(34, 40, 49));

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                applyBrush(event);
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                applyBrush(event);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        addMouseWheelListener(event -> {
            if (event.getWheelRotation() < 0) {
                zoomIn();
            } else {
                zoomOut();
            }
        });
    }

    public void replaceMap(GameMap newMap) {
        this.map = newMap;
        updatePreferredSize();
        revalidate();
        repaint();
    }

    public void setBrushTeamId(int brushTeamId) {
        this.brushTeamId = Math.max(0, Math.min(4, brushTeamId));
    }

    public int getBrushTeamId() {
        return brushTeamId;
    }

    public void setSelectedTerrain(TerrainType selectedTerrain) {
        this.selectedTerrain = selectedTerrain;
    }

    public void setSelectedStructure(StructureType selectedStructure) {
        this.selectedStructure = selectedStructure;
    }

    public void setSelectedUnitSprite(String selectedUnitSpriteId) {
        this.selectedUnitSpriteId = selectedUnitSpriteId;
    }

    public void setOreDepositBrushMode(boolean oreDepositBrushMode) {
        this.oreDepositBrushMode = oreDepositBrushMode;
    }

    public boolean isOreDepositBrushMode() {
        return oreDepositBrushMode;
    }

    public GameMap getMap() {
        return map;
    }

    public void applyGridResize(int newWidth, int newHeight) {
        map.resize(newWidth, newHeight);
        updatePreferredSize();
        revalidate();
        repaint();
        statusConsumer.accept("Map size set to " + newWidth + " x " + newHeight);
    }

    public void zoomIn() {
        setTileSize(tileSize + 4);
    }

    public void zoomOut() {
        setTileSize(tileSize - 4);
    }

    public int getZoomPercent() {
        return Math.round(tileSize * 100f / BASE_TILE_SIZE);
    }

    public void fillTerrain(TerrainType terrainType) {
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile tile = map.getTile(x, y);
                if (tile != null) {
                    tile.setTerrainType(terrainType);
                }
            }
        }
        repaint();
        statusConsumer.accept("Filled map with " + terrainType);
    }

    public void resetToPlains() {
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile tile = map.getTile(x, y);
                if (tile != null) {
                    tile.setTerrainType(TerrainType.PLAINS_1);
                    tile.setStructure(null);
                    tile.setStructureTeamId(null);
                    tile.setUnitSpriteId(null);
                    tile.setUnitTeamId(null);
                    tile.setUnitFacing(FacingDirection.EAST);
                    tile.setOreDeposit(false);
                }
            }
        }
        repaint();
        statusConsumer.accept("Reset all tiles to PLAINS");
    }

    public void clearStructures() {
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile tile = map.getTile(x, y);
                if (tile != null) {
                    tile.setStructure(null);
                    tile.setStructureTeamId(null);
                }
            }
        }
        repaint();
        statusConsumer.accept("Cleared all structures");
    }

    public void clearUnits() {
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile tile = map.getTile(x, y);
                if (tile != null) {
                    tile.setUnitSpriteId(null);
                    tile.setUnitTeamId(null);
                    tile.setUnitFacing(FacingDirection.EAST);
                }
            }
        }
        repaint();
        statusConsumer.accept("Cleared all units");
    }

    /** Whether the editor has a map tile selection from a recent paint or erase. */
    public boolean hasSelectedTile() {
        return selectedGridCell != null
            && selectedGridCell.x >= 0
            && selectedGridCell.y >= 0
            && map.getTile(selectedGridCell.x, selectedGridCell.y) != null;
    }

    /**
     * Removes only the structure on the selected tile (if any). The selection comes from the
     * last tile you clicked on the map.
     *
     * @return {@code true} if a structure was removed
     */
    public boolean clearStructureOnSelectedTile() {
        if (!hasSelectedTile()) {
            statusConsumer.accept("Select a map tile first (click to paint or erase).");
            return false;
        }
        Tile tile = map.getTile(selectedGridCell.x, selectedGridCell.y);
        if (tile.getStructure() == null) {
            statusConsumer.accept("No structure on tile (" + selectedGridCell.x + ", " + selectedGridCell.y + ").");
            return false;
        }
        tile.setStructure(null);
        tile.setStructureTeamId(null);
        repaint();
        statusConsumer.accept("Removed structure at (" + selectedGridCell.x + ", " + selectedGridCell.y + ").");
        return true;
    }

    /**
     * Removes only the placed unit on the selected tile (if any).
     *
     * @return {@code true} if a unit was removed
     */
    public boolean clearUnitOnSelectedTile() {
        if (!hasSelectedTile()) {
            statusConsumer.accept("Select a map tile first (click to paint or erase).");
            return false;
        }
        Tile tile = map.getTile(selectedGridCell.x, selectedGridCell.y);
        if (tile.getUnitSpriteId() == null) {
            statusConsumer.accept("No unit on tile (" + selectedGridCell.x + ", " + selectedGridCell.y + ").");
            return false;
        }
        tile.setUnitSpriteId(null);
        tile.setUnitTeamId(null);
        tile.setUnitFacing(FacingDirection.EAST);
        repaint();
        statusConsumer.accept("Removed unit at (" + selectedGridCell.x + ", " + selectedGridCell.y + ").");
        return true;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (terrainStripAnimTimer == null) {
            terrainStripAnimTimer = new Timer(1000, e -> repaint());
        }
        terrainStripAnimTimer.start();
    }

    @Override
    public void removeNotify() {
        if (terrainStripAnimTimer != null) {
            terrainStripAnimTimer.stop();
        }
        super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int origin = EDGE_PADDING_TILES * tileSize;
        int mapPixelW = map.getWidth() * tileSize;
        int mapPixelH = map.getHeight() * tileSize;
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(new Color(28, 34, 42));
        g.fillRect(origin, origin, mapPixelW, mapPixelH);

        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile tile = map.getTile(x, y);
                if (tile == null) {
                    continue;
                }
                int px = origin + x * tileSize;
                int py = origin + y * tileSize;
                drawTerrain(g, tile.getTerrainType(), px, py);
                drawStructure(g, tile, px, py);
                drawUnit(g, tile, px, py);
                if (tile.isOreDeposit()) {
                    int r = Math.max(4, tileSize / 6);
                    g.setColor(new Color(255, 200, 60, 230));
                    g.fillOval(px + tileSize - r - 3, py + tileSize - r - 3, r, r);
                    g.setColor(new Color(90, 60, 10, 200));
                    g.drawOval(px + tileSize - r - 3, py + tileSize - r - 3, r, r);
                }
                g.setColor(new Color(0, 0, 0, 70));
                g.drawRect(px, py, tileSize, tileSize);
            }
        }
        drawSelectionCursor(g, origin);
    }

    private void applyBrush(MouseEvent event) {
        Point gridPoint = toGrid(event.getX(), event.getY());
        if (gridPoint.x < 0 || gridPoint.y < 0) {
            return;
        }
        Tile tile = map.getTile(gridPoint.x, gridPoint.y);
        if (tile == null) {
            return;
        }
        selectedGridCell = gridPoint;

        if (event.getButton() == MouseEvent.BUTTON3 || event.isShiftDown()) {
            tile.setStructure(null);
            tile.setStructureTeamId(null);
            tile.setUnitSpriteId(null);
            tile.setUnitTeamId(null);
            tile.setUnitFacing(FacingDirection.EAST);
            tile.setOreDeposit(false);
            repaint();
            statusConsumer.accept("Removed structure and unit at " + gridPoint.x + "," + gridPoint.y);
            return;
        }

        if (event.getButton() == MouseEvent.BUTTON1 && oreDepositBrushMode) {
            tile.setOreDeposit(!tile.isOreDeposit());
            repaint();
            statusConsumer.accept(
                (tile.isOreDeposit() ? "Marked ore deposit at " : "Cleared ore at ")
                    + gridPoint.x + "," + gridPoint.y
            );
            return;
        }

        int paintUnitTeam = brushTeamId <= 0 ? 1 : Math.min(brushTeamId, map.getTeamCount());

        tile.setTerrainType(selectedTerrain);
        if (selectedStructure != null) {
            if (brushTeamId == 0 && selectedStructure == StructureType.Capital) {
                statusConsumer.accept("Capital must be assigned to a faction; choose a faction (not Neutral) in Brush Faction.");
            } else {
                tile.setStructure(new Structure(selectedStructure, null));
                if (brushTeamId == 0) {
                    tile.setStructureTeamId(null);
                } else {
                    tile.setStructureTeamId(Math.min(brushTeamId, map.getTeamCount()));
                }
            }
        } else {
            tile.setStructure(null);
            tile.setStructureTeamId(null);
        }
        if (selectedUnitSpriteId != null) {
            tile.setUnitSpriteId(selectedUnitSpriteId);
            tile.setUnitTeamId(paintUnitTeam);
            tile.setUnitFacing(FacingDirection.EAST);
        } else {
            tile.setUnitSpriteId(null);
            tile.setUnitTeamId(null);
            tile.setUnitFacing(FacingDirection.EAST);
        }

        repaint();
        statusConsumer.accept("Painted tile at " + gridPoint.x + "," + gridPoint.y);
    }

    private Point toGrid(int pixelX, int pixelY) {
        int origin = EDGE_PADDING_TILES * tileSize;
        int gx = (pixelX - origin) / tileSize;
        int gy = (pixelY - origin) / tileSize;
        if (gx < 0 || gy < 0 || gx >= map.getWidth() || gy >= map.getHeight()) {
            return new Point(-1, -1);
        }
        return new Point(gx, gy);
    }

    private void drawTerrain(Graphics g, TerrainType terrainType, int x, int y) {
        Image terrainImage = assetManager.getTerrainImage(terrainType);
        if (terrainImage != null) {
            AssetManager.drawTerrainImageOnTile(g, terrainImage, x, y, tileSize, fallbackTerrainColor(terrainType));
            return;
        }
        g.setColor(fallbackTerrainColor(terrainType));
        g.fillRect(x, y, tileSize, tileSize);
    }

    private void drawStructure(Graphics g, Tile tile, int x, int y) {
        Structure structure = tile.getStructure();
        if (structure == null) {
            return;
        }
        Color tint = structureTintForEditorStructure(tile.getStructureTeamId());
        Image structureImage = tint == null
            ? assetManager.getStructureImage(structure.getType())
            : assetManager.getStructureImageTinted(structure.getType(), tint);
        if (structureImage != null) {
            int inset = Math.max(2, tileSize / 8);
            g.drawImage(structureImage, x + inset, y + inset, tileSize - (inset * 2), tileSize - (inset * 2), null);
        } else {
            g.setColor(new Color(255, 255, 255, 220));
            int inset = Math.max(4, tileSize / 4);
            g.fillOval(x + inset, y + inset, tileSize - (inset * 2), tileSize - (inset * 2));
            g.setColor(new Color(30, 30, 30));
            g.drawString(shortName(structure.getType().name()), x + 4, y + Math.max(14, tileSize - 8));
        }
        drawStructureFactionBadge(g, x, y, tile.getStructureTeamId());
    }

    /**
     * Editor tint: {@code null} team = neutral gray; faction 1 = default art; 2–4 = same recolor as units.
     */
    private static Color structureTintForEditorStructure(Integer structureTeamId) {
        if (structureTeamId == null) {
            return AssetManager.STRUCTURE_NEUTRAL_RECOLOR;
        }
        if (structureTeamId <= 1) {
            return null;
        }
        return teamColor(structureTeamId);
    }

    private void drawStructureFactionBadge(Graphics g, int x, int y, Integer structureTeamId) {
        int r = Math.max(5, tileSize / 5);
        int bx = x + tileSize - r - 2;
        int by = y + 2;
        if (structureTeamId == null) {
            g.setColor(new Color(110, 110, 118));
            g.fillOval(bx, by, r, r);
            g.setColor(new Color(0, 0, 0, 140));
            g.drawOval(bx, by, r, r);
            return;
        }
        drawTeamBadge(g, x, y, structureTeamId);
    }

    private void drawUnit(Graphics g, Tile tile, int x, int y) {
        String unitSpriteId = tile.getUnitSpriteId();
        if (unitSpriteId == null) {
            return;
        }
        Color tint = spriteTintForTeam(tile.getUnitTeamId());
        Image unitFrame = tint == null
            ? assetManager.getUnitFrame(unitSpriteId, tile.getUnitFacing(), 0)
            : assetManager.getUnitFrameTinted(unitSpriteId, tile.getUnitFacing(), 0, tint);
        if (unitFrame != null) {
            AssetManager.drawUnitFrameOnTile(g, unitFrame, x, y, tileSize);
        }
        drawTeamBadge(g, x, y, tile.getUnitTeamId());
    }

    private static Color spriteTintForTeam(Integer teamId) {
        if (teamId == null || teamId <= 1) {
            return null;
        }
        return teamColor(teamId);
    }

    private void drawTeamBadge(Graphics g, int x, int y, Integer teamId) {
        if (teamId == null) {
            return;
        }
        int r = Math.max(5, tileSize / 5);
        g.setColor(teamColor(teamId));
        g.fillOval(x + tileSize - r - 2, y + 2, r, r);
        g.setColor(new Color(0, 0, 0, 140));
        g.drawOval(x + tileSize - r - 2, y + 2, r, r);
    }

    private static Color teamColor(int teamId) {
        // Matches PlayableGameSession player order: Red, Blue, Green, Yellow → team IDs 1..4.
        return switch (teamId) {
            case 2 -> new Color(70, 140, 235);
            case 3 -> new Color(80, 190, 95);
            case 4 -> new Color(235, 190, 65);
            default -> new Color(220, 55, 55);
        };
    }

    private void drawSelectionCursor(Graphics g, int origin) {
        if (selectedGridCell == null || selectedGridCell.x < 0) {
            return;
        }
        int px = origin + selectedGridCell.x * tileSize;
        int py = origin + selectedGridCell.y * tileSize;
        g.setColor(new Color(255, 245, 110, 220));
        g.drawRect(px + 1, py + 1, tileSize - 2, tileSize - 2);
        g.setColor(new Color(255, 180, 0, 220));
        g.drawRect(px + 2, py + 2, tileSize - 4, tileSize - 4);
    }

    private void setTileSize(int candidate) {
        int clamped = Math.max(MIN_TILE_SIZE, Math.min(MAX_TILE_SIZE, candidate));
        if (clamped == tileSize) {
            return;
        }
        tileSize = clamped;
        updatePreferredSize();
        revalidate();
        repaint();
        statusConsumer.accept("Map zoom: " + getZoomPercent() + "%");
        if (zoomChangeListener != null) {
            zoomChangeListener.run();
        }
    }

    private void updatePreferredSize() {
        int span = (map.getWidth() + 2 * EDGE_PADDING_TILES) * tileSize;
        setPreferredSize(new Dimension(span, span));
    }

    private Color fallbackTerrainColor(TerrainType terrainType) {
        return terrainType.fallbackMapColor();
    }

    private String shortName(String value) {
        String compact = value.replace("_", "");
        return compact.length() <= 3 ? compact : compact.substring(0, 3);
    }
}
