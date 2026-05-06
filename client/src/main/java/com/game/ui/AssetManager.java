package com.game.ui;

import com.game.model.map.TerrainType;
import com.game.model.units.FacingDirection;
import com.game.model.units.UnitType;
import com.game.model.structures.StructureType;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AssetManager {
    static {
        // Ensure SPI plugins (e.g. WebP) from the classpath are visible to ImageIO
        ImageIO.scanForPlugins();
    }

    private static final Logger LOGGER = Logger.getLogger(AssetManager.class.getName());

    /**
     * Solid replacement color when recoloring structure trim for neutral (unowned) buildings —
     * matches the map editor and in-map structure presentation.
     */
    public static final Color STRUCTURE_NEUTRAL_RECOLOR = new Color(140, 140, 148);

    private final Map<TerrainType, Image> terrainImages = new EnumMap<>(TerrainType.class);
    private final Map<StructureType, BufferedImage> structureImages = new EnumMap<>(StructureType.class);
    private final Map<StructureTintKey, BufferedImage> structureTintCache = new HashMap<>();
    private final Map<String, UnitSpriteSheet> unitSheets = new HashMap<>();
    private final Map<UnitTintKey, BufferedImage> unitTintCache = new HashMap<>();
    private final static String TERRAIN_PATH = "/assets/terrain/";
    private final static String STRUCTURE_PATH = "/assets/structures/";
    private final static String UNITS_PATH = "/assets/units/";
    private static final int UNIT_SHEET_COLUMNS = 6;
    /** Always 6×4 when sheet height is a multiple of 4 (including doubled-height PNGs — still four logical rows). */
    private static final int UNIT_SHEET_ROWS = 4;

    public AssetManager() {
        loadTerrainImages();
        loadStructureImages();
        loadUnitImages();
    }

    public Image getTerrainImage(TerrainType terrainType) {
        return terrainImages.get(terrainType);
    }

    public Image getStructureImage(StructureType structureType) {
        return structureImages.get(structureType);
    }

    /**
     * Team-tinted structure art using the same primary-recolor path as units. {@code null} tint
     * returns the default (red-trim) image; neutral structures use {@link #STRUCTURE_NEUTRAL_RECOLOR}.
     */
    public Image getStructureImageTinted(StructureType structureType, Color tintColor) {
        BufferedImage base = structureImages.get(structureType);
        if (base == null || tintColor == null) {
            return base;
        }
        StructureTintKey key = new StructureTintKey(structureType, tintColor.getRGB() & 0x00FFFFFF);
        return structureTintCache.computeIfAbsent(
            key,
            k -> SpritePrimaryRecolor.recolorDefaultRedTo(base, tintColor)
        );
    }

    public List<String> getAvailableUnitSpriteIds() {
        List<String> ids = new ArrayList<>(unitSheets.keySet());
        Collections.sort(ids);
        return ids;
    }

    public Image getUnitPreviewImage(String unitSpriteId) {
        UnitSpriteSheet sheet = sheetFor(unitSpriteId);
        return sheet == null ? null : sheet.getFrame(0, 0);
    }

    /**
     * Returns a preview icon scaled to {@code targetWidth} with proportional height — the same
     * aspect-preserving behavior used by
     * {@link #drawUnitFrameOnTile(Graphics, Image, float, float, int)} for in-map rendering.
     * The {@code targetHeight} argument is treated as a design hint only; height is computed from
     * the source aspect ratio so taller-than-wide sprites are not flattened. Callers should size
     * their containing component to accommodate the proportional height.
     */
    public Icon getUnitPreviewIcon(String unitSpriteId, int targetWidth, int targetHeight) {
        Image image = getUnitPreviewImage(unitSpriteId);
        if (image == null) {
            return null;
        }
        int iw = image.getWidth(null);
        int ih = image.getHeight(null);
        if (iw <= 0 || ih <= 0 || targetWidth <= 0) {
            return new ImageIcon(image);
        }
        int drawW = targetWidth;
        int drawH = Math.max(1, (int) Math.round(ih * (double) targetWidth / (double) iw));
        return new ImageIcon(image.getScaledInstance(drawW, drawH, Image.SCALE_SMOOTH));
    }

    public Image getUnitFrame(String unitSpriteId, FacingDirection direction, int animationIndex) {
        BufferedImage frame = getUnitFrameBuffered(unitSpriteId, direction, animationIndex);
        return frame;
    }

    public Image getUnitFrameTinted(String unitSpriteId, FacingDirection direction, int animationIndex, Color tintColor) {
        BufferedImage base = getUnitFrameBuffered(unitSpriteId, direction, animationIndex);
        if (base == null || tintColor == null) {
            return base;
        }
        String canonKey = canonicalSpriteCacheKey(unitSpriteId);
        int row = frameRowFor(unitSpriteId, animationIndex);
        int col = frameColumnFor(direction, animationIndex);
        if (row < 0 || col < 0) {
            return base;
        }
        UnitTintKey key = new UnitTintKey(canonKey, row, col, tintColor.getRGB() & 0x00FFFFFF);
        return unitTintCache.computeIfAbsent(
            key,
            k -> SpritePrimaryRecolor.recolorDefaultRedTo(base, tintColor)
        );
    }

    private BufferedImage getUnitFrameBuffered(String unitSpriteId, FacingDirection direction, int animationIndex) {
        UnitSpriteSheet sheet = sheetFor(unitSpriteId);
        if (sheet == null) {
            return null;
        }
        int row = frameRowFor(sheet, animationIndex);
        int col = frameColumnFor(direction, animationIndex);
        return sheet.getFrame(row, col);
    }

    private int frameRowFor(String unitSpriteId, int animationIndex) {
        UnitSpriteSheet sheet = sheetFor(unitSpriteId);
        if (sheet == null) {
            return -1;
        }
        return frameRowFor(sheet, animationIndex);
    }

    private UnitSpriteSheet sheetFor(String rawSpriteId) {
        if (rawSpriteId == null || rawSpriteId.isBlank()) {
            return null;
        }
        UnitSpriteSheet hit = unitSheets.get(rawSpriteId);
        if (hit != null) {
            return hit;
        }
        String lower = rawSpriteId.toLowerCase(Locale.ROOT);
        hit = unitSheets.get(lower);
        if (hit != null) {
            return hit;
        }
        for (Map.Entry<String, UnitSpriteSheet> e : unitSheets.entrySet()) {
            if (e.getKey().equalsIgnoreCase(rawSpriteId)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** Stable cache key matching {@link #sheetFor(String)} resolution. */
    private String canonicalSpriteCacheKey(String rawSpriteId) {
        if (rawSpriteId == null || rawSpriteId.isBlank()) {
            return null;
        }
        if (unitSheets.containsKey(rawSpriteId)) {
            return rawSpriteId;
        }
        String lower = rawSpriteId.toLowerCase(Locale.ROOT);
        if (unitSheets.containsKey(lower)) {
            return lower;
        }
        for (String k : unitSheets.keySet()) {
            if (k.equalsIgnoreCase(rawSpriteId)) {
                return k;
            }
        }
        return rawSpriteId;
    }

    private static int frameRowFor(UnitSpriteSheet sheet, int animationIndex) {
        return Math.floorMod(animationIndex, sheet.rows());
    }

    private static int frameColumnFor(FacingDirection direction, int animationIndex) {
        int[] columns = columnsFor(direction);
        return columns[Math.floorMod(animationIndex, columns.length)];
    }

    /**
     * Draws a unit frame at full cell aspect ratio (width scaled to {@code tileSize}). The image is horizontally
     * centered on the tile. Vertically, the <strong>center of the bottom half</strong> of the scaled image is aligned
     * with the <strong>center of the tile</strong>, so extra height sits mostly above the tile and tops are not clipped.
     * <p>
     * Map painting should iterate grid {@code y} ascending so units on larger {@code y} (visually below) are drawn
     * later and appear in front.
     */
    public static void drawUnitFrameOnTile(Graphics g, Image frame, int tilePixelX, int tilePixelY, int tileSize) {
        drawUnitFrameOnTile(g, frame, (float) tilePixelX, (float) tilePixelY, tileSize);
    }

    public static void drawUnitFrameOnTile(Graphics g, Image frame, float tilePixelX, float tilePixelY, int tileSize) {
        if (frame == null || tileSize <= 0) {
            return;
        }
        int iw = frame.getWidth(null);
        int ih = frame.getHeight(null);
        if (iw <= 0 || ih <= 0) {
            return;
        }
        int drawW = tileSize;
        int drawH = Math.max(1, (int) Math.round(ih * (double) tileSize / (double) iw));
        float tileCx = tilePixelX + tileSize * 0.5f;
        float tileCy = tilePixelY + tileSize * 0.5f;
        float drawX = tileCx - drawW * 0.5f;
        float drawY = tileCy - 0.75f * drawH;
        int dx = Math.round(drawX);
        int dy = Math.round(drawY);
        if (g instanceof Graphics2D g2) {
            var hints = g2.getRenderingHints();
            try {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(frame, dx, dy, drawW, drawH, null);
            } finally {
                g2.setRenderingHints(hints);
            }
        } else {
            g.drawImage(frame, dx, dy, drawW, drawH, null);
        }
    }

    /**
     * Draws a terrain sprite with width {@code tileSize} and height from the source aspect ratio. The bottom edge of
     * the scaled image aligns with the bottom of the tile so tall artwork extends upward over the cell row above. Map
     * code should paint rows with ascending {@code y} so lower rows draw on top of overlap from taller tiles behind.
     *
     * @param uncoveredTopFill when non-null and the scaled height is less than {@code tileSize}, this color fills the
     *                         uncovered strip at the top of the tile (wide, squat sprites). Pass {@code null} to skip.
     */
    public static void drawTerrainImageOnTile(
        Graphics g,
        Image terrainImage,
        int tilePixelX,
        int tilePixelY,
        int tileSize,
        Color uncoveredTopFill
    ) {
        if (terrainImage == null || tileSize <= 0) {
            return;
        }
        int iw = terrainImage.getWidth(null);
        int ih = terrainImage.getHeight(null);
        if (iw <= 0 || ih <= 0) {
            return;
        }
        int drawW = tileSize;
        int drawH = Math.max(1, (int) Math.round(ih * (double) tileSize / (double) iw));
        if (uncoveredTopFill != null && drawH < tileSize) {
            int gapH = tileSize - drawH;
            g.setColor(uncoveredTopFill);
            g.fillRect(tilePixelX, tilePixelY, tileSize, gapH);
        }
        int dx = tilePixelX;
        int dy = tilePixelY + tileSize - drawH;
        if (g instanceof Graphics2D g2) {
            var hints = g2.getRenderingHints();
            try {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(terrainImage, dx, dy, drawW, drawH, null);
            } finally {
                g2.setRenderingHints(hints);
            }
        } else {
            g.drawImage(terrainImage, dx, dy, drawW, drawH, null);
        }
    }

    /** @see #drawTerrainImageOnTile(Graphics, Image, int, int, int, Color) */
    public static void drawTerrainImageOnTile(Graphics g, Image terrainImage, int tilePixelX, int tilePixelY, int tileSize) {
        drawTerrainImageOnTile(g, terrainImage, tilePixelX, tilePixelY, tileSize, null);
    }

    private void loadTerrainImages() {
        for (TerrainType terrainType : TerrainType.values()) {
            String resourcePath = TERRAIN_PATH + terrainType.assetStem() + ".png";
            terrainImages.put(terrainType, loadImage(resourcePath));
        }
    }

    private void loadStructureImages() {
        for (StructureType structureType : StructureType.values()) {
            BufferedImage img = loadBufferedImage(STRUCTURE_PATH + toFileStem(structureType.name()) + ".png");
            if (img != null) {
                structureImages.put(structureType, img);
            }
        }
    }

    private BufferedImage loadBufferedImage(String resourcePath) {
        String normalizedPath = resourcePath.toLowerCase();
        try (InputStream stream = getClass().getResourceAsStream(normalizedPath)) {
            if (stream == null) {
                LOGGER.warning(() -> "Resource not found on classpath: " + normalizedPath);
                return null;
            }
            BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                LOGGER.warning(() -> "ImageIO could not decode resource: " + normalizedPath);
            }
            return image;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed reading image resource: " + normalizedPath, e);
            return null;
        }
    }

    private void loadUnitImages() {
        // Primary path: classpath resources (matches terrain/structures — works from Gradle/JAR/repo-root cwd).
        for (UnitType ut : UnitType.values()) {
            registerUnitSheetIfAbsent(ut.name(), ut.name());
        }
        // Extra filenames listed one per line (optional), for sprites not named exactly like UnitType.
        loadSheetsListedIn("/assets/units/extra-sprites.txt");
        // Development fallback: scan typical resource dirs on disk when cwd is repo root or client/.
        discoverDevelopmentUnitSheetsOnFilesystem();
    }

    /**
     * Loads stem names from {@code classpathTxt}, one per line (# comments and blanks skipped).
     */
    private void loadSheetsListedIn(String classpathTxt) {
        try (InputStream listStream = getClass().getResourceAsStream(classpathTxt.toLowerCase(Locale.ROOT))) {
            if (listStream == null) {
                return;
            }
            byte[] raw = listStream.readAllBytes();
            String text = new String(raw, StandardCharsets.UTF_8).replace("\r\n", "\n");
            for (String line : text.split("\n")) {
                String stem = line.strip();
                if (stem.isEmpty() || stem.startsWith("#")) {
                    continue;
                }
                int dot = stem.lastIndexOf('.');
                if (dot > 0) {
                    stem = stem.substring(0, dot);
                }
                registerUnitSheetIfAbsent(stem, stem);
            }
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Optional unit sprite manifest not readable: " + classpathTxt, e);
        }
    }

    /**
     * Registers {@code spriteId} -> sheet when not already present, trying classpath png/webp for {@code fileStem}.
     */
    private void registerUnitSheetIfAbsent(String spriteId, String fileStem) {
        if (sheetFor(spriteId) != null) {
            return;
        }
        UnitSpriteSheet sheet = loadUnitSheetFromClasspath(fileStem);
        if (sheet != null) {
            unitSheets.put(spriteId, sheet);
        }
    }

    private void discoverDevelopmentUnitSheetsOnFilesystem() {
        Path cwd = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path dir = cwd;
        for (int depth = 0; depth < 64 && dir != null; depth++) {
            tryLoadUnitsFromDirectory(dir.resolve("client/src/main/resources/assets/units"));
            tryLoadUnitsFromDirectory(dir.resolve("src/main/resources/assets/units"));
            Path parent = dir.getParent();
            if (parent == null || parent.equals(dir)) {
                break;
            }
            dir = parent;
        }
    }

    private void tryLoadUnitsFromDirectory(Path unitsDirectory) {
        if (!Files.isDirectory(unitsDirectory)) {
            return;
        }
        try (var paths = Files.list(unitsDirectory)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                String name = path.getFileName().toString();
                String lower = name.toLowerCase(Locale.ROOT);
                if (!(lower.endsWith(".png") || lower.endsWith(".webp"))) {
                    return;
                }
                int dot = name.lastIndexOf('.');
                String spriteId = dot > 0 ? name.substring(0, dot) : name;
                if (sheetFor(spriteId) != null) {
                    return;
                }
                UnitSpriteSheet sheet = loadUnitSheetFromPath(path);
                if (sheet != null) {
                    unitSheets.put(spriteId, sheet);
                }
            });
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Skip listing units dir " + unitsDirectory, e);
        }
    }

    private UnitSpriteSheet loadUnitSheetFromClasspath(String fileStem) {
        String normStem = fileStem.toLowerCase(Locale.ROOT);
        for (String ext : List.of("png", "webp")) {
            String resourcePath = (UNITS_PATH + normStem + "." + ext).toLowerCase(Locale.ROOT);
            UnitSpriteSheet sheet = loadUnitSheetFromClasspathExact(resourcePath);
            if (sheet != null) {
                return sheet;
            }
        }
        return null;
    }

    private UnitSpriteSheet loadUnitSheetFromClasspathExact(String classpathResourcePathLower) {
        try (InputStream stream = getClass().getResourceAsStream(classpathResourcePathLower)) {
            if (stream == null) {
                return null;
            }
            BufferedImage source = ImageIO.read(stream);
            return decodeUnitSpriteSheet(source, classpathResourcePathLower);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed loading unit sheet: " + classpathResourcePathLower, e);
            return null;
        }
    }

    private UnitSpriteSheet loadUnitSheetFromPath(Path path) {
        try (InputStream stream = Files.newInputStream(path)) {
            BufferedImage source = ImageIO.read(stream);
            return decodeUnitSpriteSheet(source, path.toString());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed loading unit sheet file: " + path, e);
            return null;
        }
    }

    private UnitSpriteSheet decodeUnitSpriteSheet(BufferedImage source, String debugLabel) {
        if (source == null) {
            LOGGER.warning(() -> "ImageIO could not decode unit sheet: " + debugLabel);
            return null;
        }
        if (source.getWidth() < UNIT_SHEET_COLUMNS || source.getWidth() % UNIT_SHEET_COLUMNS != 0) {
            LOGGER.warning(() -> "Unit sheet width is not divisible by " + UNIT_SHEET_COLUMNS + ": " + debugLabel);
            return null;
        }

        int frameWidth = source.getWidth() / UNIT_SHEET_COLUMNS;
        int rows = (source.getHeight() % UNIT_SHEET_ROWS == 0) ? UNIT_SHEET_ROWS : 1;
        int frameHeight = source.getHeight() / rows;
        BufferedImage[][] frames = new BufferedImage[rows][UNIT_SHEET_COLUMNS];

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < UNIT_SHEET_COLUMNS; col++) {
                frames[row][col] = source.getSubimage(
                    col * frameWidth,
                    row * frameHeight,
                    frameWidth,
                    frameHeight
                );
            }
        }
        if (isDebugUnitSheets()) {
            dumpUnitSheetDebugArtifacts(debugLabel, source, frameWidth, frameHeight, rows, frames);
        }
        return new UnitSpriteSheet(rows, frames);
    }

    /**
     * When true, logs per-sheet layout and writes PNGs under {@code build/debug-unit-sheets/} for inspection.
     * Enable with {@code -Dcom.game.debugUnitSheets=true} on the app JVM, or {@code DEBUG_UNIT_SHEETS=1} in the environment.
     */
    public static boolean isDebugUnitSheets() {
        return Boolean.parseBoolean(System.getProperty("com.game.debugUnitSheets", "false"))
            || "1".equals(System.getenv("DEBUG_UNIT_SHEETS"));
    }

    private static void dumpUnitSheetDebugArtifacts(
        String resourcePath,
        BufferedImage source,
        int frameWidth,
        int frameHeight,
        int rows,
        BufferedImage[][] frames
    ) {
        String stem = resourcePathStem(resourcePath);
        Path dir = Paths.get("build", "debug-unit-sheets");
        try {
            Files.createDirectories(dir);
            Path full = dir.resolve(stem + "_A_full_sheet.png");
            Path cellPng = dir.resolve(stem + "_B_cell_r0_c0.png");
            Path framePng = dir.resolve(stem + "_C_frame_r0_c0.png");
            ImageIO.write(source, "png", full.toFile());
            BufferedImage cell00 = source.getSubimage(0, 0, frameWidth, frameHeight);
            ImageIO.write(cell00, "png", cellPng.toFile());
            ImageIO.write(frames[0][0], "png", framePng.toFile());

        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "[debug-unit-sheets] failed to write artifacts for " + stem, ex);
        }
    }

    private static String resourcePathStem(String resourcePath) {
        int slash = Math.max(resourcePath.lastIndexOf('/'), resourcePath.lastIndexOf('\\'));
        String name = slash >= 0 ? resourcePath.substring(slash + 1) : resourcePath;
        if (name.toLowerCase().endsWith(".png")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }

    private Image loadImage(String resourcePath) {
        String normalizedPath = resourcePath.toLowerCase();
        LOGGER.info(() -> "Attempting image load. Requested path=" + resourcePath + ", normalized path=" + normalizedPath);

        try (InputStream stream = getClass().getResourceAsStream(normalizedPath)) {
            if (stream == null) {
                LOGGER.warning(() -> "Resource not found on classpath: " + normalizedPath);
                return null;
            }
            Image image = ImageIO.read(stream);
            if (image == null) {
                LOGGER.warning(() -> "ImageIO could not decode resource: " + normalizedPath);
            } else {
                LOGGER.info(() -> "Successfully loaded image resource: " + normalizedPath);
            }
            return image;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed reading image resource: " + normalizedPath, e);
            return null;
        }
    }

    private String toFileStem(String enumName) {
        return enumName.toLowerCase();
    }

    private static int[] columnsFor(FacingDirection direction) {
        if (direction == null) {
            return new int[]{0, 4};
        }
        return switch (direction) {
            case EAST -> new int[]{0, 4};
            case SOUTH -> new int[]{1};
            case WEST -> new int[]{2, 5};
            case NORTH -> new int[]{3};
        };
    }

    private record UnitSpriteSheet(int rows, BufferedImage[][] frames) {
        private BufferedImage getFrame(int row, int col) {
            int safeRow = Math.max(0, Math.min(row, rows - 1));
            int safeCol = Math.max(0, Math.min(col, frames[safeRow].length - 1));
            return frames[safeRow][safeCol];
        }
    }

    private record UnitTintKey(String spriteId, int row, int col, int tintRgb) {
    }

    private record StructureTintKey(StructureType type, int tintRgb) {
    }
}
