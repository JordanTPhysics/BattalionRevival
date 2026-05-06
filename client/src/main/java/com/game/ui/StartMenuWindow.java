package com.game.ui;

import com.game.model.map.GameMap;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

public class StartMenuWindow extends JFrame {
    private GameMap sessionMap;
    private String mapDisplayName;

    public StartMenuWindow(GameMap map, String mapDisplayName) {
        super("Battalion Revival");
        this.sessionMap = map;
        this.mapDisplayName = mapDisplayName == null || mapDisplayName.isBlank() ? "Skirmish" : mapDisplayName;

        getContentPane().setBackground(Theme.BACKGROUND);
        setLayout(new BorderLayout());
        add(buildContent(), BorderLayout.CENTER);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(560, 460));
        setLocationRelativeTo(null);
    }

    private JComponent buildContent() {
        BackdropPanel root = new BackdropPanel();
        root.setLayout(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder(
            Theme.SPACING_LG, Theme.SPACING_LG, Theme.SPACING_LG, Theme.SPACING_LG
        ));

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildMenuCard(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        return root;
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel kicker = new JLabel("BATTALION");
        kicker.setForeground(Theme.ACCENT);
        kicker.setFont(Theme.fontSectionLabel().deriveFont(14f));
        kicker.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = MilitaryComponents.titleLabel("BATTALION REVIVAL");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(title.getFont().deriveFont(28f));

        JLabel subtitle = MilitaryComponents.mutedLabel("Tactical command — turn-based skirmish");
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        header.add(kicker);
        header.add(Box.createVerticalStrut(Theme.SPACING_XS));
        header.add(title);
        header.add(Box.createVerticalStrut(2));
        header.add(subtitle);
        header.add(Box.createVerticalStrut(Theme.SPACING_LG));
        return header;
    }

    private JComponent buildMenuCard() {
        JPanel cardWrap = new JPanel();
        cardWrap.setOpaque(false);
        cardWrap.setLayout(new BoxLayout(cardWrap, BoxLayout.X_AXIS));

        JPanel card = new JPanel();
        card.setBackground(Theme.PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
            Theme.thinBorder(),
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 2, 0, 0, Theme.ACCENT),
                Theme.padding(Theme.SPACING_LG, Theme.SPACING_LG)
            )
        ));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setMaximumSize(new Dimension(420, Integer.MAX_VALUE));

        JLabel section = MilitaryComponents.sectionLabel("Command Menu");
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(section);
        card.add(Box.createVerticalStrut(Theme.SPACING_MD));

        MilitaryButton playGame = new MilitaryButton("Play Skirmish", MilitaryButton.Style.PRIMARY);
        styleMenuButton(playGame);
        playGame.addActionListener(e -> MapSelectionDialog.open(this, (loadedMap, displayName) -> {
            Set<Integer> aiSeats = chooseAiSeatsForMatch(loadedMap);
            if (aiSeats == null) {
                return;
            }
            GameUiLauncher.prepareMapForPlay(loadedMap);
            GameWindow window = new GameWindow(loadedMap, this::showMenu, displayName, aiSeats);
            window.setVisible(true);
            setVisible(false);
        }));

        MilitaryButton playOnline = new MilitaryButton("Online (beta)", MilitaryButton.Style.DEFAULT);
        styleMenuButton(playOnline);
        playOnline.addActionListener(e -> OnlineLobbyDialog.open(this, this::showMenu));

        MilitaryButton openBuilder = new MilitaryButton("Map Builder");
        styleMenuButton(openBuilder);
        BiConsumer<GameMap, String> onMapReplaced = (loaded, nameStem) -> {
            sessionMap = loaded;
            if (nameStem != null && !nameStem.isBlank()) {
                mapDisplayName = nameStem;
            }
        };
        openBuilder.addActionListener(e -> {
            MapBuilderWindow window = new MapBuilderWindow(sessionMap, this::showMenu, onMapReplaced);
            window.setVisible(true);
            setVisible(false);
        });

        MilitaryButton quit = new MilitaryButton("Exit", MilitaryButton.Style.DANGER);
        styleMenuButton(quit);
        quit.addActionListener(e -> System.exit(0));

        card.add(playGame);
        card.add(Box.createVerticalStrut(Theme.SPACING_SM));
        card.add(playOnline);
        card.add(Box.createVerticalStrut(Theme.SPACING_SM));
        card.add(openBuilder);
        card.add(Box.createVerticalStrut(Theme.SPACING_MD));
        card.add(makeDivider());
        card.add(Box.createVerticalStrut(Theme.SPACING_MD));
        card.add(quit);

        cardWrap.add(Box.createHorizontalGlue());
        cardWrap.add(card);
        cardWrap.add(Box.createHorizontalGlue());
        return cardWrap;
    }

    private static void styleMenuButton(JButton b) {
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        Dimension full = new Dimension(Integer.MAX_VALUE, 38);
        b.setMaximumSize(full);
        b.setPreferredSize(new Dimension(280, 38));
    }

    private static JComponent makeDivider() {
        JPanel d = new JPanel();
        d.setBackground(Theme.BORDER);
        d.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        d.setPreferredSize(new Dimension(0, 1));
        return d;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(Theme.SPACING_LG, 0, 0, 0));
        JLabel build = MilitaryComponents.mutedLabel("v0.1.0 — Tactical Revival Build");
        footer.add(build, BorderLayout.WEST);
        return footer;
    }

    private void showMenu() {
        setVisible(true);
    }

    private Set<Integer> chooseAiSeatsForMatch(GameMap loadedMap) {
        int teamCount = Math.max(GameMap.MIN_TEAMS, loadedMap.getTeamCount());
        String[] teamNames = {"Red", "Blue", "Green", "Yellow"};
        String[] controlModes = {"Human", "AI"};
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(new JLabel("Choose control mode per player:"));
        content.add(Box.createVerticalStrut(Theme.SPACING_SM));

        List<JComboBox<String>> selectors = new ArrayList<>(teamCount);
        for (int i = 0; i < teamCount; i++) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACING_SM, 0));
            row.add(new JLabel(teamNames[i] + ":"));
            JComboBox<String> mode = new JComboBox<>(controlModes);
            mode.setSelectedIndex(i == 0 ? 0 : 1);
            selectors.add(mode);
            row.add(mode);
            content.add(row);
            content.add(Box.createVerticalStrut(Theme.SPACING_XS));
        }

        int choice = JOptionPane.showConfirmDialog(
            this,
            content,
            "Player Control Setup",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        );
        if (choice != JOptionPane.OK_OPTION) {
            return null;
        }
        Set<Integer> aiSeats = new LinkedHashSet<>();
        for (int i = 0; i < teamCount; i++) {
            if ("AI".equals(selectors.get(i).getSelectedItem())) {
                aiSeats.add(i);
            }
        }
        return aiSeats;
    }

    /**
     * Subtle vertical gradient backdrop with a faint diagonal grid — mimics a
     * tactical command brief without any literal imagery.
     */
    private static final class BackdropPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                Color top = new Color(15, 22, 19);
                Color bottom = Theme.BACKGROUND;
                g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
                g2.fillRect(0, 0, w, h);

                g2.setStroke(new BasicStroke(1f));
                g2.setColor(new Color(46, 58, 53, 60));
                int step = 32;
                for (int x = -h; x < w; x += step) {
                    g2.drawLine(x, 0, x + h, h);
                }
                g2.setColor(new Color(76, 175, 80, 35));
                g2.drawLine(0, 56, w, 56);
                g2.drawLine(0, h - 56, w, h - 56);
            } finally {
                g2.dispose();
            }
        }
    }
}
