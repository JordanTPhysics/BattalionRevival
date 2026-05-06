package com.game.ui;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;

/**
 * Centralized theme tokens for the Battalion Revival client.
 *
 * <p>Values are sourced from {@code docs/STYLE.md} (modern military / tactical aesthetic).
 * Use {@link #installGlobalDefaults()} once during application startup so generic Swing
 * surfaces ({@code JOptionPane}, {@code JFileChooser}, {@code JComboBox}) inherit the
 * dark theme rather than rendering with default platform L&amp;F.
 */
public final class Theme {
    private Theme() {
    }

    public static final Color BACKGROUND = Color.decode("#0F1412");
    public static final Color PANEL = Color.decode("#1A221F");
    public static final Color PANEL_ELEVATED = Color.decode("#222C28");
    public static final Color PANEL_HOVER = Color.decode("#2A3530");
    public static final Color BORDER = Color.decode("#2E3A35");
    public static final Color BORDER_STRONG = Color.decode("#3D4D45");

    public static final Color ACCENT = Color.decode("#4CAF50");
    public static final Color ACCENT_HOVER = Color.decode("#8BC34A");
    public static final Color WARNING = Color.decode("#FFC107");
    public static final Color DANGER = Color.decode("#F44336");
    public static final Color INFO = Color.decode("#03A9F4");

    public static final Color TEXT_PRIMARY = Color.decode("#E0E6E3");
    public static final Color TEXT_SECONDARY = Color.decode("#A5B1AC");
    public static final Color TEXT_DISABLED = Color.decode("#5F6B66");

    /** Translucent panel fill for HUD overlays drawn over the battlefield. */
    public static final Color HUD_PANEL_TRANSLUCENT = new Color(26, 34, 31, 230);

    public static final int SPACING_XS = 4;
    public static final int SPACING_SM = 8;
    public static final int SPACING_MD = 16;
    public static final int SPACING_LG = 24;
    public static final int CORNER_RADIUS = 6;

    public static Font fontTitle() {
        return new Font(Font.SANS_SERIF, Font.BOLD, 22);
    }

    public static Font fontSubtitle() {
        return new Font(Font.SANS_SERIF, Font.BOLD, 15);
    }

    public static Font fontBody() {
        return new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    }

    public static Font fontHud() {
        return new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    }

    public static Font fontHudBold() {
        return new Font(Font.SANS_SERIF, Font.BOLD, 13);
    }

    public static Font fontMicro() {
        return new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    }

    public static Font fontButton() {
        return new Font(Font.SANS_SERIF, Font.BOLD, 12);
    }

    public static Font fontSectionLabel() {
        return new Font(Font.SANS_SERIF, Font.BOLD, 10);
    }

    public static Border thinBorder() {
        return BorderFactory.createLineBorder(BORDER, 1);
    }

    public static Border accentBorder() {
        return BorderFactory.createLineBorder(ACCENT, 1);
    }

    public static Border padding(int v, int h) {
        return new EmptyBorder(v, h, v, h);
    }

    public static Border padding(int t, int l, int b, int r) {
        return new EmptyBorder(t, l, b, r);
    }

    public static Border topDivider() {
        return BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER);
    }

    public static Border bottomDivider() {
        return BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER);
    }

    public static Border panelBorder() {
        return BorderFactory.createCompoundBorder(thinBorder(), padding(SPACING_SM, SPACING_MD));
    }

    /**
     * Installs theme-aware defaults on the Swing {@code UIManager}. Should be invoked once before
     * any UI is constructed so {@code JOptionPane} / {@code JFileChooser} dialogs inherit the look.
     */
    public static void installGlobalDefaults() {
        UIManager.put("Panel.background", PANEL);

        UIManager.put("Label.foreground", TEXT_PRIMARY);
        UIManager.put("Label.font", fontBody());
        UIManager.put("Label.disabledForeground", TEXT_DISABLED);

        UIManager.put("Button.background", PANEL_ELEVATED);
        UIManager.put("Button.foreground", TEXT_PRIMARY);
        UIManager.put("Button.font", fontButton());
        UIManager.put("Button.focus", ACCENT);
        UIManager.put("Button.disabledText", TEXT_DISABLED);
        UIManager.put("Button.select", PANEL_HOVER);

        UIManager.put("ToggleButton.background", PANEL_ELEVATED);
        UIManager.put("ToggleButton.foreground", TEXT_PRIMARY);
        UIManager.put("ToggleButton.font", fontButton());
        UIManager.put("ToggleButton.select", ACCENT);

        UIManager.put("ComboBox.background", PANEL_ELEVATED);
        UIManager.put("ComboBox.foreground", TEXT_PRIMARY);
        UIManager.put("ComboBox.selectionBackground", BORDER_STRONG);
        UIManager.put("ComboBox.selectionForeground", TEXT_PRIMARY);
        UIManager.put("ComboBox.buttonBackground", PANEL_ELEVATED);
        UIManager.put("ComboBox.font", fontBody());

        UIManager.put("List.background", PANEL);
        UIManager.put("List.foreground", TEXT_PRIMARY);
        UIManager.put("List.selectionBackground", BORDER_STRONG);
        UIManager.put("List.selectionForeground", TEXT_PRIMARY);
        UIManager.put("List.font", fontBody());

        UIManager.put("TextField.background", PANEL_ELEVATED);
        UIManager.put("TextField.foreground", TEXT_PRIMARY);
        UIManager.put("TextField.caretForeground", ACCENT);
        UIManager.put("TextField.selectionBackground", ACCENT);
        UIManager.put("TextField.selectionForeground", BACKGROUND);
        UIManager.put("TextField.font", fontBody());
        UIManager.put("TextField.border", BorderFactory.createCompoundBorder(thinBorder(), padding(4, 6)));

        UIManager.put("Spinner.background", PANEL_ELEVATED);
        UIManager.put("Spinner.foreground", TEXT_PRIMARY);
        UIManager.put("Spinner.font", fontBody());

        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("OptionPane.foreground", TEXT_PRIMARY);
        UIManager.put("OptionPane.messageForeground", TEXT_PRIMARY);
        UIManager.put("OptionPane.messageFont", fontBody());
        UIManager.put("OptionPane.buttonFont", fontButton());

        UIManager.put("ScrollPane.background", BACKGROUND);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("Viewport.background", BACKGROUND);

        UIManager.put("ScrollBar.background", PANEL);
        UIManager.put("ScrollBar.thumb", BORDER_STRONG);
        UIManager.put("ScrollBar.thumbDarkShadow", BORDER);
        UIManager.put("ScrollBar.thumbHighlight", BORDER_STRONG);
        UIManager.put("ScrollBar.thumbShadow", BORDER);
        UIManager.put("ScrollBar.track", PANEL);

        UIManager.put("ToolTip.background", PANEL_ELEVATED);
        UIManager.put("ToolTip.foreground", TEXT_PRIMARY);
        UIManager.put("ToolTip.font", fontMicro());
        UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(thinBorder(), padding(4, 6)));

        UIManager.put("MenuBar.background", PANEL);
        UIManager.put("MenuBar.foreground", TEXT_PRIMARY);
        UIManager.put("Menu.background", PANEL);
        UIManager.put("Menu.foreground", TEXT_PRIMARY);
        UIManager.put("MenuItem.background", PANEL);
        UIManager.put("MenuItem.foreground", TEXT_PRIMARY);

        UIManager.put("FileChooser.background", PANEL);
        UIManager.put("FileChooser.foreground", TEXT_PRIMARY);

        UIManager.put("CheckBox.background", PANEL);
        UIManager.put("CheckBox.foreground", TEXT_PRIMARY);
        UIManager.put("CheckBox.font", fontBody());

        UIManager.put("TitledBorder.titleColor", TEXT_SECONDARY);
        UIManager.put("TitledBorder.font", fontSubtitle());
    }

    /** Recursively applies the dark background to every {@code JPanel} below {@code root}. */
    public static void cascadeDarkBackground(Component root) {
        if (root instanceof javax.swing.JPanel panel) {
            panel.setBackground(BACKGROUND);
        }
        if (root instanceof Container c) {
            for (Component child : c.getComponents()) {
                cascadeDarkBackground(child);
            }
        }
    }
}
