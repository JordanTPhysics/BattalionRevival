package com.game.ui;

import com.game.engine.PlayableGameSession;
import com.game.model.map.GameMap;
import com.game.model.map.TerrainType;
import com.game.model.map.Tile;
import com.game.network.client.MapCatalogClient;
import com.game.network.client.OnlineMatchCoordinator;
import com.game.persistence.MapJsonPersistence;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

final class OnlineLobbyDialog {

    private static final String DEMO_MAP_LABEL = "Server default skirmish (match id: demo)";

    private OnlineLobbyDialog() {
    }

    static void open(JFrame parent, Runnable onBackToMenu) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;

        gc.gridx = 0;
        gc.gridy = 0;
        panel.add(new JLabel("Server URL"), gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        JTextField serverField = new JTextField("http://localhost:8080", 28);
        panel.add(serverField, gc);

        gc.gridy++;
        gc.gridx = 0;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        JButton refreshMaps = new JButton("Refresh map list");
        panel.add(refreshMaps, gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        JComboBox<String> mapCombo = new JComboBox<>();
        mapCombo.addItem(DEMO_MAP_LABEL);
        panel.add(mapCombo, gc);

        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Match id"), gc);
        gc.gridx = 1;
        JTextField matchField = new JTextField("demo", 16);
        panel.add(matchField, gc);

        gc.gridy++;
        gc.gridx = 0;
        panel.add(new JLabel("Your seat"), gc);
        gc.gridx = 1;
        JSpinner seatSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 3, 1));
        panel.add(seatSpinner, gc);

        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 2;
        JLabel hint = MilitaryComponents.mutedLabel(
            "Shared maps: pick a catalog entry, use a unique match id (not \"demo\"), and click OK. "
                + "All players must use the same match id and map."
        );
        panel.add(hint, gc);

        Runnable reloadCatalog = () -> {
            try {
                List<MapCatalogClient.MapSummary> maps = MapCatalogClient.listMaps(serverField.getText().trim());
                mapCombo.removeAllItems();
                mapCombo.addItem(DEMO_MAP_LABEL);
                for (MapCatalogClient.MapSummary m : maps) {
                    mapCombo.addItem(m.slug());
                }
            } catch (IOException ex) {
                mapCombo.removeAllItems();
                mapCombo.addItem(DEMO_MAP_LABEL);
                JOptionPane.showMessageDialog(
                    parent,
                    "Could not load map list: " + ex.getMessage(),
                    "Online",
                    JOptionPane.WARNING_MESSAGE
                );
            }
        };
        refreshMaps.addActionListener(e -> reloadCatalog.run());
        reloadCatalog.run();

        int result = JOptionPane.showConfirmDialog(
            parent,
            panel,
            "Online (beta)",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        String matchId = matchField.getText().trim();
        if (matchId.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Match id is required.", "Online", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String selectedMap = (String) mapCombo.getSelectedItem();
        boolean useSharedMap = selectedMap != null && !DEMO_MAP_LABEL.equals(selectedMap);

        if (useSharedMap && "demo".equalsIgnoreCase(matchId)) {
            JOptionPane.showMessageDialog(
                parent,
                "The match id \"demo\" is reserved for the server's default skirmish.\n"
                    + "Choose another match id when playing on a shared map (e.g. room1).",
                "Online",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        String serverUrl = serverField.getText().trim();
        int seat = (Integer) seatSpinner.getValue();

        GameMap bootstrap;
        if (useSharedMap) {
            try {
                MapCatalogClient.ensureMatch(serverUrl, matchId, selectedMap);
                String json = MapCatalogClient.downloadMapJson(serverUrl, selectedMap);
                bootstrap = MapJsonPersistence.parse(json);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(
                    parent,
                    "Could not start match with shared map: " + ex.getMessage(),
                    "Online",
                    JOptionPane.ERROR_MESSAGE
                );
                return;
            }
        } else {
            bootstrap = plainsBootstrap();
        }

        GameUiLauncher.prepareMapForPlay(bootstrap);
        PlayableGameSession session = new PlayableGameSession(bootstrap);

        String wsUrl;
        try {
            wsUrl = buildWsMatchUrl(serverUrl, matchId, seat);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(parent, ex.getMessage(), "Online", JOptionPane.WARNING_MESSAGE);
            return;
        }

        AtomicReference<GameWindow> winRef = new AtomicReference<>();
        OnlineMatchCoordinator coord = new OnlineMatchCoordinator(
            matchId,
            snap -> {
                GameWindow w = winRef.get();
                if (w != null) {
                    w.bindAuthoritativeSession(snap);
                }
            },
            msg -> SwingUtilities.invokeLater(() -> {
                GameWindow w = winRef.get();
                Component c = w != null ? w : parent;
                JOptionPane.showMessageDialog(c, msg, "Online", JOptionPane.WARNING_MESSAGE);
            })
        );

        String levelTitle = useSharedMap ? ("Online — " + selectedMap + " — seat " + seat) : ("Online — seat " + seat);
        GameWindow window = new GameWindow(session, onBackToMenu, levelTitle, coord);
        winRef.set(window);
        coord.connect(wsUrl);
        window.setVisible(true);
        parent.setVisible(false);
    }

    private static String buildWsMatchUrl(String root, String matchId, int seat) {
        if (root.isEmpty()) {
            throw new IllegalArgumentException("Server URL is required.");
        }
        String base = root.trim();
        if (!base.startsWith("ws://") && !base.startsWith("wss://")) {
            if (base.startsWith("http://")) {
                base = "ws://" + base.substring("http://".length());
            } else if (base.startsWith("https://")) {
                base = "wss://" + base.substring("https://".length());
            } else {
                base = "ws://" + base;
            }
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!base.endsWith("/ws/match")) {
            base = base + "/ws/match";
        }
        String enc = URLEncoder.encode(matchId, StandardCharsets.UTF_8);
        return base + "?matchId=" + enc + "&seat=" + seat;
    }

    private static GameMap plainsBootstrap() {
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
