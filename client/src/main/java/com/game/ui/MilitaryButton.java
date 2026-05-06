package com.game.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Themed button matching the modern military style (rectangular, slight rounding,
 * accent border, hover lift, accent on press).
 *
 * <p>States: default / hover / pressed / disabled — with a {@link Style} variant
 * for primary CTA, danger, ghost, etc. The button paints its own background so it
 * works regardless of look-and-feel.
 */
public class MilitaryButton extends JButton {

    public enum Style {
        /** Standard panel-elevated button used for most actions. */
        DEFAULT,
        /** Accent-tinted button reserved for the primary call-to-action in a panel. */
        PRIMARY,
        /** Red-tinted button for destructive actions (e.g. quit / cancel-out). */
        DANGER,
        /** Transparent button used inline with text-heavy panels. */
        GHOST
    }

    private Style style = Style.DEFAULT;

    public MilitaryButton(String text) {
        this(text, Style.DEFAULT);
    }

    public MilitaryButton(String text, Style style) {
        super(text);
        this.style = style;
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setForeground(Theme.TEXT_PRIMARY);
        setFont(Theme.fontButton());
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
    }

    public void setStyle(Style style) {
        this.style = style;
        repaint();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setForeground(enabled ? Theme.TEXT_PRIMARY : Theme.TEXT_DISABLED);
        setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int radius = Theme.CORNER_RADIUS;

            g2.setColor(backgroundFill());
            g2.fillRoundRect(0, 0, w - 1, h - 1, radius, radius);

            g2.setStroke(new BasicStroke(1.4f));
            g2.setColor(borderColor());
            g2.drawRoundRect(0, 0, w - 1, h - 1, radius, radius);
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }

    private Color backgroundFill() {
        if (!isEnabled()) {
            return Theme.PANEL;
        }
        if (getModel().isPressed() || getModel().isArmed() && getModel().isRollover()) {
            return switch (style) {
                case PRIMARY -> Theme.ACCENT;
                case DANGER -> Theme.DANGER;
                case GHOST -> Theme.PANEL_HOVER;
                default -> mix(Theme.ACCENT, Theme.PANEL_ELEVATED, 0.25f);
            };
        }
        if (getModel().isRollover()) {
            return switch (style) {
                case PRIMARY -> mix(Theme.ACCENT, Theme.PANEL_ELEVATED, 0.65f);
                case DANGER -> mix(Theme.DANGER, Theme.PANEL_ELEVATED, 0.55f);
                case GHOST -> Theme.PANEL_ELEVATED;
                default -> Theme.PANEL_HOVER;
            };
        }
        return switch (style) {
            case PRIMARY -> mix(Theme.ACCENT, Theme.PANEL_ELEVATED, 0.4f);
            case DANGER -> mix(Theme.DANGER, Theme.PANEL_ELEVATED, 0.35f);
            case GHOST -> Theme.PANEL;
            default -> Theme.PANEL_ELEVATED;
        };
    }

    private Color borderColor() {
        if (!isEnabled()) {
            return Theme.BORDER;
        }
        if (getModel().isRollover() || getModel().isPressed() || isDefaultButton()) {
            return switch (style) {
                case DANGER -> Theme.DANGER;
                default -> Theme.ACCENT;
            };
        }
        return switch (style) {
            case PRIMARY -> Theme.ACCENT;
            case DANGER -> Theme.DANGER;
            default -> Theme.BORDER_STRONG;
        };
    }

    private static Color mix(Color a, Color b, float ratio) {
        float r = clamp01(ratio);
        int rr = Math.round(a.getRed() * r + b.getRed() * (1f - r));
        int gg = Math.round(a.getGreen() * r + b.getGreen() * (1f - r));
        int bb = Math.round(a.getBlue() * r + b.getBlue() * (1f - r));
        return new Color(rr, gg, bb);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
