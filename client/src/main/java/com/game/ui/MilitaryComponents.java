package com.game.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;

/**
 * Factory helpers for the {@link Theme} so the rest of the UI does not have to
 * repeat panel/border boilerplate. Builds on a HUD vocabulary: section headers,
 * labelled stat rows, titled blocks, etc.
 */
public final class MilitaryComponents {
    private MilitaryComponents() {
    }

    /** Small uppercase header used inside HUD blocks ("UNIT", "TILE", "GAME"). */
    public static JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text == null ? "" : text.toUpperCase());
        l.setForeground(Theme.TEXT_SECONDARY);
        l.setFont(Theme.fontSectionLabel());
        return l;
    }

    public static JLabel titleLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Theme.TEXT_PRIMARY);
        l.setFont(Theme.fontTitle());
        return l;
    }

    public static JLabel subtitleLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Theme.TEXT_SECONDARY);
        l.setFont(Theme.fontSubtitle());
        return l;
    }

    public static JLabel bodyLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Theme.TEXT_PRIMARY);
        l.setFont(Theme.fontBody());
        return l;
    }

    public static JLabel hudLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Theme.TEXT_PRIMARY);
        l.setFont(Theme.fontHud());
        return l;
    }

    public static JLabel hudDataLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Theme.TEXT_PRIMARY);
        l.setFont(Theme.fontHudBold());
        return l;
    }

    public static JLabel mutedLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Theme.TEXT_SECONDARY);
        l.setFont(Theme.fontMicro());
        return l;
    }

    /**
     * Solid background HUD panel with a thin border and standard padding.
     */
    public static JPanel hudBlock() {
        JPanel p = new JPanel();
        p.setBackground(Theme.PANEL);
        p.setBorder(BorderFactory.createCompoundBorder(
            Theme.thinBorder(),
            Theme.padding(Theme.SPACING_SM, Theme.SPACING_MD)
        ));
        return p;
    }

    /**
     * HUD block with a section header bar at the top and a content area below.
     * Inserts an accent stripe on the left edge for the "active command panel" feel
     * encouraged by the style guide.
     */
    public static JPanel titledHudBlock(String title, JComponent body) {
        JPanel block = new JPanel(new BorderLayout(0, Theme.SPACING_SM));
        block.setBackground(Theme.PANEL);
        block.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createCompoundBorder(
                Theme.thinBorder(),
                BorderFactory.createMatteBorder(0, 2, 0, 0, Theme.ACCENT)
            ),
            Theme.padding(Theme.SPACING_SM, Theme.SPACING_MD)
        ));
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        header.setOpaque(false);
        header.add(sectionLabel(title));
        block.add(header, BorderLayout.NORTH);
        if (body != null) {
            body.setOpaque(false);
            block.add(body, BorderLayout.CENTER);
        }
        return block;
    }

    /** Standard background panel with optional layout. */
    public static JPanel darkPanel() {
        JPanel p = new JPanel();
        p.setBackground(Theme.BACKGROUND);
        return p;
    }

    public static JPanel verticalBox(int gap) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        if (gap > 0) {
            p.setBorder(Theme.padding(0, 0));
        }
        return p;
    }

    public static Component verticalGap(int px) {
        return Box.createVerticalStrut(px);
    }

    public static Component horizontalGap(int px) {
        return Box.createHorizontalStrut(px);
    }

    /**
     * 1-pixel vertical accent rule used to separate top-bar segments.
     */
    public static JComponent verticalRule() {
        JSeparator sep = new JSeparator(JSeparator.VERTICAL) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(1, 24);
            }
        };
        sep.setForeground(Theme.BORDER);
        sep.setBackground(Theme.BORDER);
        return sep;
    }

    /**
     * Compact key/value row for HUD blocks. Renders as two columns (label, value).
     */
    public static JPanel statRow(String label, String value) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 2, 0);

        JLabel k = new JLabel(label);
        k.setFont(Theme.fontMicro());
        k.setForeground(Theme.TEXT_SECONDARY);
        c.gridx = 0;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 2, Theme.SPACING_SM);
        row.add(k, c);

        JLabel v = new JLabel(value);
        v.setFont(Theme.fontHudBold());
        v.setForeground(Theme.TEXT_PRIMARY);
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 2, 0);
        row.add(v, c);
        return row;
    }

    /**
     * Tag pill — a small accent-bordered label used for status badges (e.g. the active player
     * faction or "READY"/"USED" indicators).
     */
    public static JPanel pill(String text, Color accent) {
        return new Pill(text, accent);
    }

    private static final class Pill extends JPanel {
        private final String text;
        private final Color accent;

        private Pill(String text, Color accent) {
            this.text = text == null ? "" : text;
            this.accent = accent == null ? Theme.ACCENT : accent;
            setOpaque(false);
            setFont(Theme.fontMicro().deriveFont(Font.BOLD));
            setBorder(Theme.padding(2, 8));
        }

        @Override
        public Dimension getPreferredSize() {
            var fm = getFontMetrics(getFont());
            int w = fm.stringWidth(text) + 18;
            int h = fm.getHeight() + 6;
            return new Dimension(w, h);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(blend(accent, Theme.PANEL, 0.18f));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.setColor(accent);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.setColor(Theme.TEXT_PRIMARY);
                g2.setFont(getFont());
                var fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(text)) / 2;
                int ty = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(text, tx, ty);
            } finally {
                g2.dispose();
            }
        }

        private static Color blend(Color a, Color b, float ratio) {
            float r = Math.max(0f, Math.min(1f, ratio));
            int rr = Math.round(a.getRed() * r + b.getRed() * (1f - r));
            int gg = Math.round(a.getGreen() * r + b.getGreen() * (1f - r));
            int bb = Math.round(a.getBlue() * r + b.getBlue() * (1f - r));
            return new Color(rr, gg, bb);
        }
    }
}
