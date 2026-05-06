package com.game.ui;

import com.game.model.map.GameMap;
import com.game.persistence.MapJsonPersistence;
import com.game.persistence.MapsWorkspace;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.BorderFactory;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Modal chooser for map JSON files under {@code maps/}. Invokes the callback with a loaded {@link GameMap}
 * and a display name (file stem) when the user confirms.
 */
public final class MapSelectionDialog extends JDialog {

    private final JList<Path> list;
    private final BiConsumer<GameMap, String> onChosen;

    private MapSelectionDialog(Frame owner, List<Path> paths, BiConsumer<GameMap, String> onChosen) {
        super(owner, "Select Mission", true);
        this.onChosen = onChosen;

        list = new JList<>(paths.toArray(new Path[0]));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.setBackground(Theme.PANEL);
        list.setForeground(Theme.TEXT_PRIMARY);
        list.setSelectionBackground(Theme.BORDER_STRONG);
        list.setSelectionForeground(Theme.TEXT_PRIMARY);
        list.setFixedCellHeight(28);
        list.setFont(Theme.fontBody());
        list.setBorder(BorderFactory.createEmptyBorder(Theme.SPACING_XS, Theme.SPACING_SM, Theme.SPACING_XS, Theme.SPACING_SM));
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                JList<?> jList,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
            ) {
                JLabel label = (JLabel) super.getListCellRendererComponent(jList, value, index, isSelected, cellHasFocus);
                label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                label.setForeground(Theme.TEXT_PRIMARY);
                label.setBackground(isSelected ? Theme.BORDER_STRONG : Theme.PANEL);
                if (value instanceof Path p) {
                    label.setText(displayStem(p));
                }
                return label;
            }
        });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    confirmSelection();
                }
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(Theme.thinBorder());
        scroll.getViewport().setBackground(Theme.PANEL);
        scroll.setPreferredSize(new Dimension(420, 360));

        MilitaryButton play = new MilitaryButton("Deploy", MilitaryButton.Style.PRIMARY);
        play.addActionListener(e -> confirmSelection());
        getRootPane().setDefaultButton(play);

        MilitaryButton cancel = new MilitaryButton("Cancel", MilitaryButton.Style.GHOST);
        cancel.addActionListener(e -> dispose());

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACING_SM, 0));
        south.setBackground(Theme.BACKGROUND);
        south.add(cancel);
        south.add(play);

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new javax.swing.BoxLayout(header, javax.swing.BoxLayout.Y_AXIS));
        JLabel kicker = new JLabel("MISSION BRIEFING");
        kicker.setFont(Theme.fontSectionLabel().deriveFont(11f));
        kicker.setForeground(Theme.ACCENT);
        kicker.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel title = MilitaryComponents.titleLabel("Select a Mission");
        title.setFont(title.getFont().deriveFont(20f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = MilitaryComponents.mutedLabel(
            "Maps folder: " + MapsWorkspace.mapsDirectory() + " \u2014 double-click to deploy."
        );
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(kicker);
        header.add(javax.swing.Box.createVerticalStrut(2));
        header.add(title);
        header.add(javax.swing.Box.createVerticalStrut(2));
        header.add(subtitle);

        JPanel content = new JPanel(new BorderLayout(0, Theme.SPACING_MD));
        content.setBackground(Theme.BACKGROUND);
        content.setBorder(Theme.padding(Theme.SPACING_LG, Theme.SPACING_LG));
        content.add(header, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        getContentPane().setBackground(Theme.BACKGROUND);
        add(content, BorderLayout.CENTER);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * If the maps folder has no JSON files, shows an info message and does not open a dialog.
     */
    public static void open(Frame owner, BiConsumer<GameMap, String> onChosen) {
        List<Path> paths = listMapJsonFiles();
        if (paths.isEmpty()) {
            JOptionPane.showMessageDialog(
                owner,
                "No .json maps were found in the \"" + MapsWorkspace.mapsDirectory() + "\" folder.\n"
                    + "Save a map from Map Builder or copy map files there.",
                "No maps available",
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }
        MapSelectionDialog dialog = new MapSelectionDialog(owner, paths, onChosen);
        dialog.setVisible(true);
    }

    private static List<Path> listMapJsonFiles() {
        List<Path> paths = new ArrayList<>();
        Path mapsDir = MapsWorkspace.mapsDirectory();
        try {
            Files.createDirectories(mapsDir);
            try (var stream = Files.list(mapsDir)) {
                stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .forEach(paths::add);
            }
        } catch (IOException ignored) {
            // Leave list empty; caller treats as no maps.
        }
        return paths;
    }

    private void confirmSelection() {
        Path path = list.getSelectedValue();
        if (path == null) {
            JOptionPane.showMessageDialog(this, "Select a map from the list.", "No selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            GameMap loaded = MapJsonPersistence.load(path);
            String stem = displayStem(path);
            dispose();
            onChosen.accept(loaded, stem);
        } catch (IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(
                this,
                ex.getMessage(),
                "Failed to load map",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private static String displayStem(Path path) {
        String fileName = path.getFileName().toString();
        if (fileName.toLowerCase().endsWith(".json")) {
            return fileName.substring(0, fileName.length() - 5);
        }
        return fileName;
    }
}
