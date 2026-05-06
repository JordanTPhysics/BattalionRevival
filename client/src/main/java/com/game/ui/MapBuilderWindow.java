package com.game.ui;

import com.game.model.map.GameMap;
import com.game.model.map.TerrainType;
import com.game.model.structures.StructureType;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JCheckBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JPanel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;

import com.game.persistence.MapJsonPersistence;
import com.game.persistence.MapsWorkspace;

public class MapBuilderWindow extends JFrame {
    /** Fixed west-column width: combos stay narrow so Clear buttons stay on-screen without horizontal scroll. */
    private static final int TOOLBAR_COLUMN_WIDTH = 288;
    private static final int BRUSH_COMBO_HEIGHT = 34;
    private static final int BRUSH_COMBO_MAX_WIDTH = 168;

    private final JLabel statusLabel = new JLabel(
        "Ready \u2014 edge-pan the map or use arrow keys when the map has focus."
    );
    private final JFileChooser fileChooser = new JFileChooser();
    private final JTextField mapNameField = new JTextField(18);
    private final Runnable onBackToMenu;
    /** When non-null, invoked after a JSON load so the main menu / game session can use the new {@link GameMap}. Second arg is map name stem for the game HUD. */
    private final BiConsumer<GameMap, String> onSessionMapUpdated;
    private final AssetManager assetManager;
    private final JScrollPane mapScroll;
    private final MapBuilderPanel mapBuilderPanel;

    private final JSpinner widthSpinner = new JSpinner(new SpinnerNumberModel(20, GameMap.MIN_GRID, GameMap.MAX_GRID, 1));
    private final JSpinner heightSpinner = new JSpinner(new SpinnerNumberModel(20, GameMap.MIN_GRID, GameMap.MAX_GRID, 1));
    private final JSpinner teamCountSpinner = new JSpinner(new SpinnerNumberModel(2, GameMap.MIN_TEAMS, GameMap.MAX_TEAMS, 1));
    /** Shown in the map corner panel; kept in sync when zoom changes. */
    private JLabel mapBuilderZoomLabel;

    public MapBuilderWindow(GameMap map, Runnable onBackToMenu) {
        this(map, onBackToMenu, null);
    }

    public MapBuilderWindow(GameMap map, Runnable onBackToMenu, BiConsumer<GameMap, String> onSessionMapUpdated) {
        super("Battalion Revival - Map Builder");
        this.onBackToMenu = onBackToMenu;
        this.onSessionMapUpdated = onSessionMapUpdated;
        this.assetManager = new AssetManager();
        configureFileChooser();

        this.mapBuilderPanel = new MapBuilderPanel(map, assetManager, this::setStatus, this::refreshMapZoomLabel);
        this.mapScroll = new JScrollPane(mapBuilderPanel);
        mapScroll.setBorder(BorderFactory.createEmptyBorder());
        mapScroll.getViewport().setBackground(Theme.BACKGROUND);
        ViewportEdgePanSupport.install(mapScroll);

        getContentPane().setBackground(Theme.BACKGROUND);
        setLayout(new BorderLayout());

        JPanel mapViewport = new JPanel(new BorderLayout());
        mapViewport.setBackground(Theme.BACKGROUND);
        JPanel mapCornerStrip = new JPanel(new BorderLayout());
        mapCornerStrip.setOpaque(false);
        mapCornerStrip.add(buildMapCornerPanel(), BorderLayout.EAST);
        mapViewport.add(mapCornerStrip, BorderLayout.NORTH);
        mapViewport.add(mapScroll, BorderLayout.CENTER);
        add(mapViewport, BorderLayout.CENTER);

        add(buildToolbar(), BorderLayout.WEST);
        add(buildStatusBar(), BorderLayout.SOUTH);

        syncSpinnersFromMap(map);

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1080, 800));
        setLocationRelativeTo(null);
    }

    private void configureFileChooser() {
        Path mapsDir = MapsWorkspace.mapsDirectory();
        try {
            Files.createDirectories(mapsDir);
        } catch (IOException ignored) {
            // Falls back to default chooser location if directory creation fails.
        }
        fileChooser.setCurrentDirectory(mapsDir.toFile());
    }

    /**
     * Compact grid + zoom controls, anchored to the top-right above the map scroll area.
     */
    private JComponent buildMapCornerPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACING_SM, Theme.SPACING_XS));
        panel.setBackground(Theme.PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
            Theme.padding(Theme.SPACING_XS, Theme.SPACING_MD)
        ));

        JLabel gridKicker = new JLabel("GRID");
        gridKicker.setFont(Theme.fontSectionLabel().deriveFont(10f));
        gridKicker.setForeground(Theme.TEXT_SECONDARY);
        panel.add(gridKicker);
        panel.add(widthSpinner);
        JLabel times = new JLabel("\u00D7");
        times.setForeground(Theme.TEXT_PRIMARY);
        panel.add(times);
        panel.add(heightSpinner);
        MilitaryButton applySize = new MilitaryButton("Apply", MilitaryButton.Style.GHOST);
        applySize.addActionListener(e -> {
            int w = (Integer) widthSpinner.getValue();
            int h = (Integer) heightSpinner.getValue();
            mapBuilderPanel.applyGridResize(w, h);
        });
        panel.add(applySize);

        JLabel zoomKicker = new JLabel("ZOOM");
        zoomKicker.setFont(Theme.fontSectionLabel().deriveFont(10f));
        zoomKicker.setForeground(Theme.TEXT_SECONDARY);
        zoomKicker.setBorder(BorderFactory.createEmptyBorder(0, Theme.SPACING_SM, 0, 0));
        panel.add(zoomKicker);

        mapBuilderZoomLabel = new JLabel(mapBuilderPanel.getZoomPercent() + "%");
        mapBuilderZoomLabel.setFont(Theme.fontHud());
        mapBuilderZoomLabel.setForeground(Theme.TEXT_PRIMARY);
        mapBuilderZoomLabel.setPreferredSize(new Dimension(40, mapBuilderZoomLabel.getPreferredSize().height));
        mapBuilderZoomLabel.setHorizontalAlignment(SwingConstants.CENTER);
        MilitaryButton zoomOut = new MilitaryButton("\u2212", MilitaryButton.Style.GHOST);
        zoomOut.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
        zoomOut.setFont(zoomOut.getFont().deriveFont(Font.BOLD, 13f));
        MilitaryButton zoomIn = new MilitaryButton("+", MilitaryButton.Style.GHOST);
        zoomIn.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
        zoomIn.setFont(zoomIn.getFont().deriveFont(Font.BOLD, 13f));
        zoomOut.addActionListener(e -> {
            mapBuilderPanel.zoomOut();
            mapBuilderZoomLabel.setText(mapBuilderPanel.getZoomPercent() + "%");
        });
        zoomIn.addActionListener(e -> {
            mapBuilderPanel.zoomIn();
            mapBuilderZoomLabel.setText(mapBuilderPanel.getZoomPercent() + "%");
        });
        panel.add(zoomOut);
        panel.add(mapBuilderZoomLabel);
        panel.add(zoomIn);
        return panel;
    }

    private JComponent buildToolbar() {
        JPanel toolbar = new JPanel();
        toolbar.setBackground(Theme.PANEL);
        toolbar.setBorder(BorderFactory.createEmptyBorder());
        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.Y_AXIS));

        JLabel header = new JLabel("MAP BUILDER");
        header.setFont(Theme.fontSectionLabel().deriveFont(12f));
        header.setForeground(Theme.ACCENT);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        toolbar.add(header);
        toolbar.add(Box.createVerticalStrut(2));

        JLabel sub = new JLabel("Tactical Field Editor");
        sub.setFont(Theme.fontSubtitle());
        sub.setForeground(Theme.TEXT_PRIMARY);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        toolbar.add(sub);
        toolbar.add(Box.createVerticalStrut(Theme.SPACING_MD));
        toolbar.add(divider());
        toolbar.add(Box.createVerticalStrut(Theme.SPACING_MD));

        JComboBox<TextureOption<TerrainType>> terrainCombo = buildTerrainCombo();
        applyBrushComboSizing(terrainCombo);
        terrainCombo.addActionListener(e -> {
            TextureOption<TerrainType> option = (TextureOption<TerrainType>) terrainCombo.getSelectedItem();
            TerrainType selected = option == null ? TerrainType.PLAINS_1 : option.value();
            mapBuilderPanel.setSelectedTerrain(selected);
            setStatus("Selected terrain: " + selected);
        });
        MilitaryButton clearTerrain = new MilitaryButton("Reset", MilitaryButton.Style.GHOST);
        clearTerrain.addActionListener(e -> selectTerrainDefault(terrainCombo, mapBuilderPanel));
        toolbar.add(brushHeader("Terrain Brush"));
        toolbar.add(brushRow(terrainCombo, clearTerrain));
        toolbar.add(Box.createVerticalStrut(Theme.SPACING_SM));

        JComboBox<TextureOption<StructureType>> structureCombo = buildStructureCombo();
        applyBrushComboSizing(structureCombo);
        structureCombo.setToolTipText("Pick None to erase. Use Brush Faction 'Neutral' for unowned structures (not Capital).");
        structureCombo.addActionListener(e -> {
            TextureOption<StructureType> option = (TextureOption<StructureType>) structureCombo.getSelectedItem();
            StructureType structureType = option == null ? null : option.value();
            mapBuilderPanel.setSelectedStructure(structureType);
            setStatus("Selected structure: " + (structureType == null ? "None" : structureType));
        });
        MilitaryButton clearStructure = new MilitaryButton("Clear", MilitaryButton.Style.GHOST);
        clearStructure.addActionListener(e -> {
            structureCombo.setSelectedIndex(0);
            mapBuilderPanel.setSelectedStructure(null);
            setStatus("Structure brush cleared");
        });
        toolbar.add(brushHeader("Structure Brush"));
        toolbar.add(brushRow(structureCombo, clearStructure));
        toolbar.add(Box.createVerticalStrut(Theme.SPACING_SM));

        JComboBox<TextureOption<String>> unitCombo = buildUnitCombo();
        applyBrushComboSizing(unitCombo);
        unitCombo.setToolTipText("Pick None, then paint tiles to remove units.");
        unitCombo.addActionListener(e -> {
            TextureOption<String> option = (TextureOption<String>) unitCombo.getSelectedItem();
            String unitSpriteId = option == null ? null : option.value();
            mapBuilderPanel.setSelectedUnitSprite(unitSpriteId);
            setStatus("Selected unit: " + (unitSpriteId == null ? "None" : option));
        });
        MilitaryButton clearUnit = new MilitaryButton("Clear", MilitaryButton.Style.GHOST);
        clearUnit.addActionListener(e -> {
            unitCombo.setSelectedIndex(0);
            mapBuilderPanel.setSelectedUnitSprite(null);
            setStatus("Unit brush cleared");
        });
        toolbar.add(brushHeader("Unit Brush"));
        toolbar.add(brushRow(unitCombo, clearUnit));
        toolbar.add(Box.createVerticalStrut(Theme.SPACING_SM));

        toolbar.add(brushHeader("Resources"));
        JCheckBox oreBrush = new JCheckBox("Ore deposit brush (LMB toggles on tile)");
        oreBrush.setOpaque(false);
        oreBrush.setForeground(Theme.TEXT_PRIMARY);
        oreBrush.setAlignmentX(Component.LEFT_ALIGNMENT);
        oreBrush.addActionListener(e -> {
            mapBuilderPanel.setOreDepositBrushMode(oreBrush.isSelected());
            setStatus(oreBrush.isSelected()
                ? "Ore brush: click tiles to toggle ore deposits (War Machine drill sites)."
                : "Ore brush off");
        });
        toolbar.add(oreBrush);
        toolbar.add(Box.createVerticalStrut(Theme.SPACING_SM));

        toolbar.add(brushHeader("Brush Faction"));
        JComboBox<TextureOption<Integer>> teamCombo = buildTeamCombo();
        applyBrushComboSizing(teamCombo);
        teamCombo.addActionListener(e -> {
            TextureOption<Integer> option = (TextureOption<Integer>) teamCombo.getSelectedItem();
            int team = option == null ? 1 : option.value();
            mapBuilderPanel.setBrushTeamId(team);
            setStatus("Brush faction: " + option);
        });
        toolbar.add(teamCombo);
        toolbar.add(Box.createVerticalStrut(Theme.SPACING_SM));

        toolbar.add(brushHeader("Factions On Map"));
        teamCountSpinner.addChangeListener(e -> {
            int n = (Integer) teamCountSpinner.getValue();
            mapBuilderPanel.getMap().setTeamCount(n);
            int brush = mapBuilderPanel.getBrushTeamId();
            if (brush > n) {
                mapBuilderPanel.setBrushTeamId(n);
                teamCombo.setSelectedIndex(n);
            }
            mapBuilderPanel.repaint();
            setStatus("Map team count: " + n);
        });
        JPanel teamCountRow = leftRow(teamCountSpinner);
        toolbar.add(teamCountRow);
        toolbar.add(Box.createVerticalStrut(Theme.SPACING_SM));

        toolbar.add(divider());
        toolbar.add(Box.createVerticalStrut(Theme.SPACING_SM));

        toolbar.add(brushHeader("Bulk"));
        MilitaryButton bulkMenuButton = new MilitaryButton("Bulk actions \u25BE", MilitaryButton.Style.GHOST);
        bulkMenuButton.setToolTipText("Mass-edit the map (fill terrain, clear layers, reset).");
        bulkMenuButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        fillFullWidth(bulkMenuButton);
        JPopupMenu bulkMenu = new JPopupMenu();
        JMenuItem fillTerrainItem = new JMenuItem("Fill map with current terrain");
        fillTerrainItem.addActionListener(e -> {
            TextureOption<TerrainType> option = (TextureOption<TerrainType>) terrainCombo.getSelectedItem();
            TerrainType terrainType = option == null ? TerrainType.PLAINS_1 : option.value();
            mapBuilderPanel.fillTerrain(terrainType);
        });
        bulkMenu.add(fillTerrainItem);
        JMenuItem clearStructuresItem = new JMenuItem("Clear all structures");
        clearStructuresItem.addActionListener(e -> mapBuilderPanel.clearStructures());
        bulkMenu.add(clearStructuresItem);
        JMenuItem clearUnitsItem = new JMenuItem("Clear all units");
        clearUnitsItem.addActionListener(e -> mapBuilderPanel.clearUnits());
        bulkMenu.add(clearUnitsItem);
        bulkMenu.addSeparator();
        JMenuItem resetPlainsItem = new JMenuItem("Reset entire map to plains (destructive)");
        resetPlainsItem.addActionListener(e -> mapBuilderPanel.resetToPlains());
        bulkMenu.add(resetPlainsItem);
        bulkMenuButton.addActionListener(e ->
            bulkMenu.show(bulkMenuButton, 0, bulkMenuButton.getHeight()));
        toolbar.add(bulkMenuButton);
        toolbar.add(Box.createVerticalStrut(Theme.SPACING_MD));

        toolbar.add(divider());
        toolbar.add(Box.createVerticalStrut(Theme.SPACING_MD));

        toolbar.add(brushHeader("Map Name"));
        mapNameField.setToolTipText("Saved into the workspace maps folder as <name>.json");
        mapNameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        mapNameField.setFont(Theme.fontBody());
        int nameFieldW = TOOLBAR_COLUMN_WIDTH - 2 * Theme.SPACING_MD - 4;
        Dimension nameFieldSize = new Dimension(nameFieldW, 28);
        mapNameField.setMaximumSize(nameFieldSize);
        mapNameField.setPreferredSize(nameFieldSize);
        toolbar.add(mapNameField);
        toolbar.add(Box.createVerticalStrut(Theme.SPACING_SM));

        MilitaryButton saveMap = new MilitaryButton("Save Map", MilitaryButton.Style.PRIMARY);
        fillFullWidth(saveMap);
        saveMap.addActionListener(e -> saveMapJson());
        toolbar.add(saveMap);
        toolbar.add(Box.createVerticalStrut(Theme.SPACING_XS));

        MilitaryButton uploadServer = new MilitaryButton("Upload to server…");
        fillFullWidth(uploadServer);
        uploadServer.addActionListener(e ->
            ServerMapUploadDialog.open(this, mapBuilderPanel.getMap(), mapNameField.getText()));
        toolbar.add(uploadServer);
        toolbar.add(Box.createVerticalStrut(Theme.SPACING_XS));

        MilitaryButton loadMap = new MilitaryButton("Load Map");
        fillFullWidth(loadMap);
        loadMap.addActionListener(e -> loadMapJson(terrainCombo, structureCombo, unitCombo, teamCombo));
        toolbar.add(loadMap);
        toolbar.add(Box.createVerticalStrut(Theme.SPACING_SM));

        MilitaryButton backToMenu = new MilitaryButton("Exit Builder", MilitaryButton.Style.GHOST);
        fillFullWidth(backToMenu);
        backToMenu.addActionListener(e -> {
            dispose();
            if (onBackToMenu != null) {
                onBackToMenu.run();
            }
        });
        toolbar.add(backToMenu);

        JPanel westColumn = new JPanel(new BorderLayout());
        westColumn.setBackground(Theme.PANEL);
        westColumn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER),
            Theme.padding(Theme.SPACING_MD, Theme.SPACING_MD)
        ));
        westColumn.add(toolbar, BorderLayout.NORTH);
        westColumn.setPreferredSize(new Dimension(TOOLBAR_COLUMN_WIDTH, 0));
        return westColumn;
    }

    private static JComponent divider() {
        JPanel d = new JPanel();
        d.setBackground(Theme.BORDER);
        d.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        d.setPreferredSize(new Dimension(0, 1));
        return d;
    }

    private static JLabel brushHeader(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(Theme.fontSectionLabel());
        l.setForeground(Theme.TEXT_SECONDARY);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, Theme.SPACING_XS, 0));
        return l;
    }

    private static JPanel leftRow(Component... children) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACING_XS, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (Component c : children) {
            if (c instanceof JLabel l) {
                l.setForeground(Theme.TEXT_PRIMARY);
                l.setFont(Theme.fontBody());
            }
            row.add(c);
        }
        return row;
    }

    private static void fillFullWidth(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
    }

    private static JPanel brushRow(JComponent main, JButton clearButton) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(main);
        row.add(Box.createHorizontalStrut(Theme.SPACING_XS));
        row.add(Box.createHorizontalGlue());
        row.add(clearButton);
        int h = Math.max(main.getPreferredSize().height, clearButton.getPreferredSize().height);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        return row;
    }

    private void selectTerrainDefault(JComboBox<TextureOption<TerrainType>> terrainCombo, MapBuilderPanel panel) {
        for (int i = 0; i < terrainCombo.getItemCount(); i++) {
            TextureOption<TerrainType> opt = terrainCombo.getItemAt(i);
            if (opt != null && opt.value() == TerrainType.PLAINS_1) {
                terrainCombo.setSelectedIndex(i);
                panel.setSelectedTerrain(TerrainType.PLAINS_1);
                setStatus("Terrain brush reset to plains");
                return;
            }
        }
    }

    private JComboBox<TextureOption<Integer>> buildTeamCombo() {
        JComboBox<TextureOption<Integer>> combo = new JComboBox<>();
        combo.addItem(new TextureOption<>(0, "Neutral (structures only)", null));
        combo.addItem(new TextureOption<>(1, "Faction 1 (Red)", null));
        combo.addItem(new TextureOption<>(2, "Faction 2 (Blue)", null));
        combo.addItem(new TextureOption<>(3, "Faction 3 (Green)", null));
        combo.addItem(new TextureOption<>(4, "Faction 4 (Yellow)", null));
        combo.setRenderer(new TextureComboRenderer<>());
        combo.setSelectedIndex(1);
        return combo;
    }

    private void syncSpinnersFromMap(GameMap map) {
        widthSpinner.setValue(map.getWidth());
        heightSpinner.setValue(map.getHeight());
        teamCountSpinner.setValue(map.getTeamCount());
    }

    /**
     * Brush combos use tall icons in the renderer — keep width capped so each row leaves room for
     * the adjacent Clear/Reset control inside the fixed west column (no horizontal clipping).
     */
    private void applyBrushComboSizing(JComboBox<?> combo) {
        combo.setAlignmentX(Component.LEFT_ALIGNMENT);
        combo.setBackground(Theme.PANEL_ELEVATED);
        combo.setForeground(Theme.TEXT_PRIMARY);
        Dimension size = new Dimension(BRUSH_COMBO_MAX_WIDTH, BRUSH_COMBO_HEIGHT);
        combo.setMinimumSize(new Dimension(72, BRUSH_COMBO_HEIGHT));
        combo.setPreferredSize(size);
        combo.setMaximumSize(size);
    }

    private JComboBox<TextureOption<TerrainType>> buildTerrainCombo() {
        JComboBox<TextureOption<TerrainType>> combo = new JComboBox<>();
        for (TerrainType terrainType : TerrainType.values()) {
            combo.addItem(new TextureOption<>(terrainType, labelFor(terrainType.name()), createIcon(assetManager.getTerrainImage(terrainType))));
        }
        combo.setRenderer(new TextureComboRenderer<>());
        return combo;
    }

    private JComboBox<TextureOption<StructureType>> buildStructureCombo() {
        JComboBox<TextureOption<StructureType>> combo = new JComboBox<>();
        combo.addItem(new TextureOption<>(null, "None", null));
        for (StructureType structureType : StructureType.values()) {
            combo.addItem(new TextureOption<>(
                structureType,
                labelFor(structureType.name()),
                createIcon(assetManager.getStructureImage(structureType))
            ));
        }
        combo.setRenderer(new TextureComboRenderer<>());
        return combo;
    }

    private JComboBox<TextureOption<String>> buildUnitCombo() {
        JComboBox<TextureOption<String>> combo = new JComboBox<>();
        combo.addItem(new TextureOption<>(null, "None", null));
        for (String spriteId : assetManager.getAvailableUnitSpriteIds()) {
            combo.addItem(new TextureOption<>(
                spriteId,
                prettyUnitLabel(spriteId),
                assetManager.getUnitPreviewIcon(spriteId, 20, 20)
            ));
        }
        combo.setRenderer(new TextureComboRenderer<>());
        return combo;
    }

    private Icon createIcon(Image source) {
        if (source == null) {
            return null;
        }
        Image scaled = source.getScaledInstance(20, 20, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    private String labelFor(String enumName) {
        return enumName.toLowerCase().replace("_", " ");
    }

    private String prettyUnitLabel(String spriteId) {
        String label = spriteId;
        if (label.startsWith("Modern-Red-Movement-")) {
            label = label.substring("Modern-Red-Movement-".length());
        }
        return label.replace("-", " ");
    }

    private JComponent buildStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(Theme.PANEL);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            Theme.topDivider(),
            Theme.padding(6, Theme.SPACING_MD)
        ));
        JLabel kicker = new JLabel("STATUS");
        kicker.setFont(Theme.fontSectionLabel());
        kicker.setForeground(Theme.ACCENT);
        kicker.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, Theme.SPACING_MD));
        JPanel west = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        west.setOpaque(false);
        west.add(kicker);
        statusLabel.setForeground(Theme.TEXT_PRIMARY);
        statusLabel.setFont(Theme.fontBody());
        west.add(statusLabel);
        statusBar.add(west, BorderLayout.WEST);
        return statusBar;
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    private void refreshMapZoomLabel() {
        if (mapBuilderZoomLabel != null) {
            mapBuilderZoomLabel.setText(mapBuilderPanel.getZoomPercent() + "%");
        }
    }

    private void saveMapJson() {
        String entered = mapNameField.getText();
        if (entered == null || entered.trim().isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Please enter a map name before saving.",
                "Map name required",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        String stem = sanitizeMapFileStem(entered.trim());
        if (stem.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Map name is not usable as a file name. Use letters, numbers, or spaces.",
                "Invalid map name",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        Path mapsDir = MapsWorkspace.mapsDirectory();
        try {
            Files.createDirectories(mapsDir);
        } catch (IOException ex) {
            showPersistenceError("Failed to create maps folder", ex);
            return;
        }

        Path path = mapsDir.resolve(stem + ".json");
        try {
            MapJsonPersistence.save(path, mapBuilderPanel.getMap());
            setStatus("Saved map to maps/" + path.getFileName());
        } catch (IOException | IllegalArgumentException ex) {
            showPersistenceError("Failed to save map", ex);
        }
    }

    private static String sanitizeMapFileStem(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '<' || c == '>' || c == ':' || c == '"' || c == '/' || c == '\\' || c == '|' || c == '?' || c == '*') {
                out.append('_');
                continue;
            }
            if (c == '.' && (i == 0 || i == raw.length() - 1)) {
                continue;
            }
            out.append(c);
        }
        String trimmed = out.toString().strip();
        while (trimmed.endsWith(".")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).strip();
        }
        return trimmed;
    }

    private void loadMapJson(
        JComboBox<TextureOption<TerrainType>> terrainCombo,
        JComboBox<TextureOption<StructureType>> structureCombo,
        JComboBox<TextureOption<String>> unitCombo,
        JComboBox<TextureOption<Integer>> teamCombo
    ) {
        fileChooser.setDialogTitle("Load map JSON");
        int result = fileChooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path path = fileChooser.getSelectedFile().toPath();
        try {
            GameMap loaded = MapJsonPersistence.load(path);
            mapBuilderPanel.replaceMap(loaded);
            syncSpinnersFromMap(loaded);
            int brush = mapBuilderPanel.getBrushTeamId();
            int idx = Math.min(brush, loaded.getTeamCount());
            teamCombo.setSelectedIndex(idx);
            @SuppressWarnings("unchecked")
            TextureOption<Integer> teamSel = (TextureOption<Integer>) teamCombo.getSelectedItem();
            mapBuilderPanel.setBrushTeamId(teamSel != null ? teamSel.value() : 1);
            mapBuilderPanel.repaint();
            String fileName = path.getFileName().toString();
            if (fileName.toLowerCase().endsWith(".json")) {
                mapNameField.setText(fileName.substring(0, fileName.length() - 5));
            } else {
                mapNameField.setText(fileName);
            }
            setStatus("Loaded map from " + path.getFileName());
            if (onSessionMapUpdated != null) {
                String stem = mapNameField.getText().trim();
                onSessionMapUpdated.accept(loaded, stem.isEmpty() ? displayStem(path) : stem);
            }
        } catch (IOException | IllegalArgumentException ex) {
            showPersistenceError("Failed to load map", ex);
        }
    }

    private void showPersistenceError(String title, Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), title, JOptionPane.ERROR_MESSAGE);
        setStatus(title + ": " + ex.getMessage());
    }

    public static void launch(GameMap map) {
        SwingUtilities.invokeLater(() -> {
            Theme.installGlobalDefaults();
            MapBuilderWindow window = new MapBuilderWindow(map, null, null);
            window.setVisible(true);
        });
    }

    private static String displayStem(Path path) {
        String fileName = path.getFileName().toString();
        if (fileName.toLowerCase().endsWith(".json")) {
            return fileName.substring(0, fileName.length() - 5);
        }
        return fileName;
    }

    private static final class TextureComboRenderer<T> extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
            JList<?> list,
            Object value,
            int index,
            boolean isSelected,
            boolean cellHasFocus
        ) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            label.setForeground(Theme.TEXT_PRIMARY);
            label.setBackground(isSelected ? Theme.BORDER_STRONG : Theme.PANEL_ELEVATED);
            label.setOpaque(true);
            if (value instanceof TextureOption<?> option) {
                label.setText(option.toString());
                label.setIcon(option.icon());
            }
            return label;
        }
    }
}
