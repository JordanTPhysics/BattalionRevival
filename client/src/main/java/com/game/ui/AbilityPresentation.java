package com.game.ui;

import com.game.model.units.UnitAbilities;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.Objects;

/**
 * HUD icons and tooltip copy for {@link com.game.model.units.Unit#getAbilities()} strings.
 */
public final class AbilityPresentation {
    private AbilityPresentation() {
    }

    public static Icon abilityIcon(String abilityId, int px) {
        return new AbilityGlyphIcon(abilityId, px);
    }

    public static String tooltipText(String abilityId) {
        String id = abilityId == null ? "" : abilityId;
        return switch (id) {
            case UnitAbilities.ANTI_AIR ->
                "Anti-air: this unit may engage aircraft.";
            case UnitAbilities.BLITZKRIEG ->
                "Blitzkrieg: +20% damage when you attack first in the exchange.";
            case UnitAbilities.TRACKER ->
                "Tracker: discovers cloaked enemies stepped on during movement; +20% damage when attacking them.";
            case UnitAbilities.CLOAKER ->
                "Cloaker: after moving, hidden from enemies until revealed; double damage if you attack while cloaked. "
                    + "Orthogonal adjacency to an enemy exposes you when that unit's action ends (move-only or after combat).";
            case UnitAbilities.JAMMER ->
                "Jammer: projects a jam zone (Manhattan distance 2) that blocks aircraft movement and uncloaks stealth units.";
            case UnitAbilities.RAPIDFIRE ->
                "Rapid fire: double damage vs light armor.";
            case UnitAbilities.PIERCING ->
                "Piercing: deals a second hit for 60% damage to the unit behind the primary target.";
            case UnitAbilities.ANTI_SUBMARINE ->
                "Anti-submarine: may attack U-boats.";
            case UnitAbilities.CONQUEROR ->
                "Conqueror: captures neutral or enemy structures by holding the tile for two full turns.";
            case UnitAbilities.EXPLOSIVE ->
                "Explosive: double damage vs units on structures and vs heavy armor.";
            case UnitAbilities.SCAVENGER ->
                "Scavenger: when your attack destroys the main target, you keep your turn action "
                    + "(you may move and/or attack again).";
            case UnitAbilities.STALWART ->
                "Stalwart: 20% chance to survive a lethal hit with 1 HP.";
            case UnitAbilities.KINGPIN ->
                "Kingpin: for teams that field a Kingpin unit, losing all Kingpins causes immediate defeat. "
                    + "War Machines can spend an onboard fabrication budget instead of HQ funds (see context menu).";
            case UnitAbilities.AIMLESS ->
                "Aimless: this unit does not count as a surviving combat unit for defeat checks.";
            case UnitAbilities.MAINTENANCE ->
                "Maintenance: heals 10% max HP at round start.";
            case UnitAbilities.RESUPPLY ->
                "Resupply: at round start, heals nearby allied units (Manhattan distance 2) for 10% max HP.";
            case UnitAbilities.STUNNING ->
                "Stunning: targets struck by this unit cannot counterattack.";
            case UnitAbilities.MASSIVE_HULL ->
                "Massive hull: cannot enter shore tiles (coastal shallows).";
            case UnitAbilities.BEHEMOTH ->
                "Behemoth: deals 15% less damage when counterattacking.";
            default -> id.isEmpty() ? "Special trait." : "Unit trait: " + id + ".";
        };
    }

    private static final class AbilityGlyphIcon implements Icon {
        private final String abilityId;
        private final int size;

        AbilityGlyphIcon(String abilityId, int size) {
            this.abilityId = abilityId;
            this.size = Math.max(12, size);
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = badgeColor(abilityId);
                g2.setColor(bg);
                g2.fill(new RoundRectangle2D.Float(x + 1, y + 1, size - 2, size - 2, 5f, 5f));
                g2.setColor(new Color(0, 0, 0, 90));
                g2.draw(new RoundRectangle2D.Float(x + 1, y + 1, size - 2, size - 2, 5f, 5f));
                g2.setColor(brightness(bg) > 0.55f ? new Color(30, 32, 36) : Color.WHITE);
                String glyph = abbrev(abilityId);
                g2.setFont(Theme.fontMicro().deriveFont(Font.BOLD, Math.max(9f, size * 0.42f)));
                var fm = g2.getFontMetrics();
                int tx = x + (size - fm.stringWidth(glyph)) / 2;
                int ty = y + (size - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(glyph, tx, ty);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    private static float brightness(Color c) {
        return (0.299f * c.getRed() + 0.587f * c.getGreen() + 0.114f * c.getBlue()) / 255f;
    }

    private static Color badgeColor(String raw) {
        String s = raw == null ? "" : raw;
        int h = Objects.hash(s);
        float hue = (h & 0xffff) / 65536f;
        return Color.getHSBColor(hue, 0.35f + (Math.abs(h) % 20) / 100f, 0.72f + (Math.abs(h >> 8) % 15) / 100f);
    }

    private static String abbrev(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "?";
        }
        if (raw.length() <= 3) {
            return raw.toUpperCase();
        }
        return raw.substring(0, 2).toUpperCase();
    }
}
