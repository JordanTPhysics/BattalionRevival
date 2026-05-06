package com.game.ui;

import com.game.engine.PlayableGameSession;
import com.game.model.Player;
import com.game.model.units.Unit;
import com.game.model.units.UnitType;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Fabrication picker for {@link UnitType#Warmachine} — spends the unit's purse and ignores control-structure locks.
 */
public final class WarmachineBuildDialog {

    private WarmachineBuildDialog() {
    }

    public static void show(
        Frame owner,
        PlayableGameSession session,
        Unit warmachine,
        AssetManager assetManager,
        Runnable onBuilt
    ) {
        Player active = session.getActivePlayer();
        int mx = warmachine.getPosition().getX();
        int my = warmachine.getPosition().getY();
        JDialog dlg = new JDialog(owner, "War Machine \u2014 fabricate units", true);
        dlg.setUndecorated(true);
        dlg.setLayout(new BorderLayout());

        JPanel root = new JPanel(new BorderLayout(0, Theme.SPACING_MD));
        root.setBackground(Theme.BACKGROUND);
        root.setBorder(Theme.padding(Theme.SPACING_MD, Theme.SPACING_LG));

        root.add(buildHeader(active, warmachine, mx, my, dlg), BorderLayout.NORTH);

        JPanel groups = new JPanel();
        groups.setBackground(Theme.BACKGROUND);
        groups.setLayout(new BoxLayout(groups, BoxLayout.Y_AXIS));

        Map<UnitType.FactoryBuildCategory, List<UnitType>> byCat = new EnumMap<>(UnitType.FactoryBuildCategory.class);
        for (UnitType.FactoryBuildCategory c : UnitType.FactoryBuildCategory.values()) {
            byCat.put(c, new ArrayList<>());
        }
        for (UnitType t : UnitType.values()) {
            if (!session.isWarmachineProducibleUnitType(t)) {
                continue;
            }
            byCat.get(t.factoryBuildCategory()).add(t);
        }
        for (List<UnitType> list : byCat.values()) {
            list.sort(Comparator.comparing(Enum::name));
        }

        groups.add(buildCategoryPanel(
            "Land Battalion",
            "No Ground Control requirement. Unit appears on an empty adjacent tile the type can traverse.",
            byCat.get(UnitType.FactoryBuildCategory.LAND),
            session,
            warmachine,
            assetManager,
            dlg,
            onBuilt
        ));
        groups.add(Box.createVerticalStrut(Theme.SPACING_MD));
        groups.add(buildCategoryPanel(
            "Naval Forces",
            "No Sea Control requirement. Naval units need a neighbouring sea lane with a legal spawn.",
            byCat.get(UnitType.FactoryBuildCategory.SEA),
            session,
            warmachine,
            assetManager,
            dlg,
            onBuilt
        ));
        groups.add(Box.createVerticalStrut(Theme.SPACING_MD));
        groups.add(buildCategoryPanel(
            "Air Wing",
            "No Air Control requirement.",
            byCat.get(UnitType.FactoryBuildCategory.AIR),
            session,
            warmachine,
            assetManager,
            dlg,
            onBuilt
        ));

        JScrollPane sc = new JScrollPane(groups);
        sc.setBorder(BorderFactory.createEmptyBorder());
        sc.getViewport().setBackground(Theme.BACKGROUND);
        ViewportEdgePanSupport.install(sc);
        root.add(sc, BorderLayout.CENTER);

        installEscToClose(root, dlg);

        dlg.setContentPane(root);

        Rectangle bounds = ownerBounds(owner);
        dlg.setBounds(bounds);
        dlg.setVisible(true);
    }

    private static Rectangle ownerBounds(Frame owner) {
        if (owner != null && owner.isShowing()) {
            return owner.getBounds();
        }
        return new Rectangle(80, 80, 1024, 768);
    }

    private static void installEscToClose(JComponent root, JDialog dlg) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "wmClose");
        root.getActionMap().put("wmClose", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dlg.dispose();
            }
        });
    }

    private static JPanel buildHeader(Player active, Unit warmachine, int mx, int my, JDialog dlg) {
        JPanel header = new JPanel(new BorderLayout(Theme.SPACING_MD, 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createCompoundBorder(
            Theme.bottomDivider(),
            Theme.padding(0, 0, Theme.SPACING_MD, 0)
        ));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JLabel kicker = new JLabel("WAR FORGE");
        kicker.setFont(Theme.fontSectionLabel().deriveFont(11f));
        kicker.setForeground(Theme.ACCENT);

        JLabel title = new JLabel("Onboard Fabricator");
        title.setFont(Theme.fontTitle().deriveFont(20f));
        title.setForeground(Theme.TEXT_PRIMARY);

        JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACING_SM, 0));
        meta.setOpaque(false);
        JLabel coordLabel = new JLabel("At (" + mx + ", " + my + ")");
        coordLabel.setFont(Theme.fontMicro());
        coordLabel.setForeground(Theme.TEXT_SECONDARY);
        meta.add(coordLabel);
        meta.add(MilitaryComponents.pill(active.getName().toUpperCase(), Theme.ACCENT));
        JLabel purse = new JLabel("\u00B7 War purse $" + warmachine.getWarmachineFunds());
        purse.setFont(Theme.fontMicro());
        purse.setForeground(Theme.TEXT_SECONDARY);
        meta.add(purse);

        left.add(kicker);
        left.add(Box.createVerticalStrut(2));
        left.add(title);
        left.add(Box.createVerticalStrut(Theme.SPACING_XS));
        left.add(meta);

        header.add(left, BorderLayout.WEST);

        MilitaryButton close = new MilitaryButton("Close [ESC]", MilitaryButton.Style.GHOST);
        close.addActionListener(e -> dlg.dispose());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(close);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private static JPanel buildCategoryPanel(
        String title,
        String caption,
        List<UnitType> types,
        PlayableGameSession session,
        Unit warmachine,
        AssetManager assetManager,
        JDialog dlg,
        Runnable onBuilt
    ) {
        JPanel wrap = new JPanel(new BorderLayout(0, Theme.SPACING_SM));
        wrap.setBackground(Theme.PANEL);
        wrap.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createCompoundBorder(
                Theme.thinBorder(),
                BorderFactory.createMatteBorder(0, 2, 0, 0, Theme.ACCENT)
            ),
            Theme.padding(Theme.SPACING_MD, Theme.SPACING_MD)
        ));

        JPanel north = new JPanel();
        north.setOpaque(false);
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACING_SM, 0));
        titleRow.setOpaque(false);
        JLabel t = new JLabel(title);
        t.setForeground(Theme.TEXT_PRIMARY);
        t.setFont(Theme.fontSubtitle());
        titleRow.add(t);

        JLabel req = new JLabel("<html><div style='width:480px;color:#A5B1AC;font-size:11px'>"
            + caption
            + "</div></html>");

        north.add(titleRow);
        north.add(Box.createVerticalStrut(Theme.SPACING_XS));
        north.add(req);
        wrap.add(north, BorderLayout.NORTH);

        int cols = 8;
        JPanel grid = new JPanel(new GridLayout(0, cols, 0, Theme.SPACING_SM));
        grid.setOpaque(false);
        for (UnitType type : types) {
            boolean rulesOk = session.canWarmachineProduceUnit(warmachine, type);
            int price = session.factoryBuildPrice(type);
            boolean affordable = warmachine.getWarmachineFunds() >= price;

            MilitaryButton b = new MilitaryButton(
                "<html><center><b>" + nice(type.name()) + "</b><br/>"
                    + "<span style='color:" + (affordable ? "#8BC34A" : "#F44336") + ";font-size:10px'>$"
                    + price + "</span></center></html>",
                MilitaryButton.Style.DEFAULT
            );
            b.setVerticalTextPosition(SwingConstants.BOTTOM);
            b.setHorizontalTextPosition(SwingConstants.CENTER);
            b.setIcon(assetManager.getUnitPreviewIcon(type.name(), 40, 28));
            b.setFont(Theme.fontMicro());
            b.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
            b.setEnabled(rulesOk && affordable);
            if (!rulesOk) {
                b.setToolTipText("Cannot fabricate here (blocked or no adjacent spawn).");
            } else if (!affordable) {
                b.setToolTipText("Not enough onboard funds ($" + price + " required).");
            } else {
                b.setToolTipText("Fabricate " + nice(type.name()) + " for $" + price);
            }
            b.addActionListener(e -> {
                if (session.tryWarmachineBuildUnit(warmachine, type)) {
                    dlg.dispose();
                    onBuilt.run();
                }
            });
            grid.add(b);
        }
        wrap.add(grid, BorderLayout.CENTER);
        return wrap;
    }

    private static String nice(String enumName) {
        return enumName.toLowerCase().replace("_", " ");
    }
}
