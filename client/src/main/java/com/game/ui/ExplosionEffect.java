package com.game.ui;

import com.game.model.units.Unit;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.Random;

/**
 * Short-lived procedural shockwave drawn over a destroyed unit's tile. Pure Java2D, no asset
 * dependencies. Scales by {@link Unit.ArmorType} so heavier kills feel weightier.
 *
 * <p>Visual layers (radius/alpha blend by {@code progress = elapsedMs / durationMs}):
 * <ol>
 *   <li>Bright core flash (filled disc) — fades out by ~25% progress.</li>
 *   <li>Expanding shockwave ring (stroked) — color blends yellow → orange → red, alpha fades to 0.</li>
 *   <li>Radial shrapnel sparks — line segments shooting outward, alpha fades by ~70% progress.</li>
 * </ol>
 *
 * <p>Effects are inert after {@link #isAlive(long)} returns {@code false}; the panel removes them.
 */
final class ExplosionEffect {

    private final long startNanos;
    private final int gridX;
    private final int gridY;
    private final int durationMs;
    private final float baseRadiusTiles;
    private final float[] sparkAngles;
    private final float[] sparkLengthsTiles;

    private ExplosionEffect(
        long startNanos,
        int gridX,
        int gridY,
        int durationMs,
        float baseRadiusTiles,
        float[] sparkAngles,
        float[] sparkLengthsTiles
    ) {
        this.startNanos = startNanos;
        this.gridX = gridX;
        this.gridY = gridY;
        this.durationMs = durationMs;
        this.baseRadiusTiles = baseRadiusTiles;
        this.sparkAngles = sparkAngles;
        this.sparkLengthsTiles = sparkLengthsTiles;
    }

    /**
     * Builds an explosion sized for the destroyed {@code unit}'s armor class. Uses {@code unit}'s
     * current grid position as the origin; safe to call after the unit has been cleared from its
     * tile because the position is captured immediately.
     */
    static ExplosionEffect forUnit(Unit unit, long nowNanos) {
        Unit.ArmorType armor = unit.getArmorType();
        int duration;
        float radiusTiles;
        int sparkCount;
        switch (armor) {
            case HEAVY -> {
                duration = 500;
                radiusTiles = 1.25f;
                sparkCount = 16;
            }
            case MEDIUM -> {
                duration = 420;
                radiusTiles = 0.95f;
                sparkCount = 12;
            }
            case LIGHT -> {
                duration = 350;
                radiusTiles = 0.7f;
                sparkCount = 8;
            }
            default -> {
                duration = 350;
                radiusTiles = 0.7f;
                sparkCount = 8;
            }
        }
        Random rnd = new Random();
        float[] angles = new float[sparkCount];
        float[] lengths = new float[sparkCount];
        float twoPi = (float) (Math.PI * 2.0);
        for (int i = 0; i < sparkCount; i++) {
            float baseAngle = (twoPi * i) / sparkCount;
            float jitter = (rnd.nextFloat() - 0.5f) * (twoPi / sparkCount) * 0.6f;
            angles[i] = baseAngle + jitter;
            lengths[i] = radiusTiles * (0.6f + rnd.nextFloat() * 0.4f);
        }
        return new ExplosionEffect(
            nowNanos,
            unit.getPosition().getX(),
            unit.getPosition().getY(),
            duration,
            radiusTiles,
            angles,
            lengths
        );
    }

    boolean isAlive(long nowNanos) {
        return elapsedMs(nowNanos) < durationMs;
    }

    private float progress(long nowNanos) {
        float p = (float) elapsedMs(nowNanos) / durationMs;
        if (p < 0f) {
            return 0f;
        }
        if (p > 1f) {
            return 1f;
        }
        return p;
    }

    private long elapsedMs(long nowNanos) {
        return (nowNanos - startNanos) / 1_000_000L;
    }

    /**
     * Renders this effect on top of the destroyed unit's tile. Caller is responsible for AA hints
     * and clip; this method saves/restores stroke and composite locally.
     */
    void paint(Graphics2D g2, int tileSize, long nowNanos) {
        float t = progress(nowNanos);
        float cx = gridX * tileSize + tileSize * 0.5f;
        float cy = gridY * tileSize + tileSize * 0.5f;
        Stroke prevStroke = g2.getStroke();
        Composite prevComposite = g2.getComposite();
        Object prevAa = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        try {
            paintShockwaveRing(g2, cx, cy, tileSize, t);
            paintSparks(g2, cx, cy, tileSize, t);
            paintCoreFlash(g2, cx, cy, tileSize, t);
        } finally {
            g2.setStroke(prevStroke);
            g2.setComposite(prevComposite);
            if (prevAa != null) {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, prevAa);
            }
        }
    }

    private void paintShockwaveRing(Graphics2D g2, float cx, float cy, int tileSize, float t) {
        float radius = baseRadiusTiles * tileSize * easeOutCubic(t);
        if (radius < 0.5f) {
            return;
        }
        float alpha = clamp01(1.0f - t) * 0.9f;
        float strokeW = Math.max(1.5f, tileSize * (0.18f - 0.12f * t));
        Color color = blendColor(
            new Color(255, 235, 140),
            new Color(220, 60, 30),
            t
        );
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2.setStroke(new BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(color);
        Ellipse2D.Float ring = new Ellipse2D.Float(cx - radius, cy - radius, radius * 2f, radius * 2f);
        g2.draw(ring);
    }

    private void paintSparks(Graphics2D g2, float cx, float cy, int tileSize, float t) {
        if (t > 0.7f) {
            return;
        }
        float sparkT = t / 0.7f;
        float alpha = clamp01(1.0f - sparkT) * 0.95f;
        float headRadius = sparkT * baseRadiusTiles * tileSize;
        float tailFraction = 0.32f;
        Color sparkColor = new Color(255, 180, 70);
        float strokeW = Math.max(1.4f, tileSize * 0.07f);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2.setColor(sparkColor);
        g2.setStroke(new BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < sparkAngles.length; i++) {
            float angle = sparkAngles[i];
            float lenScale = sparkLengthsTiles[i] / Math.max(0.0001f, baseRadiusTiles);
            float headDist = headRadius * lenScale;
            float tailDist = headDist * (1f - tailFraction);
            float hx = cx + (float) Math.cos(angle) * headDist;
            float hy = cy + (float) Math.sin(angle) * headDist;
            float tx = cx + (float) Math.cos(angle) * tailDist;
            float ty = cy + (float) Math.sin(angle) * tailDist;
            g2.draw(new Line2D.Float(tx, ty, hx, hy));
        }
    }

    private void paintCoreFlash(Graphics2D g2, float cx, float cy, int tileSize, float t) {
        if (t > 0.28f) {
            return;
        }
        float coreT = t / 0.28f;
        float radius = (0.5f - 0.4f * coreT) * tileSize;
        float alpha = clamp01(1.0f - coreT);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2.setColor(new Color(255, 245, 210));
        g2.fill(new Ellipse2D.Float(cx - radius, cy - radius, radius * 2f, radius * 2f));
    }

    private static float easeOutCubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        if (v > 1f) {
            return 1f;
        }
        return v;
    }

    private static Color blendColor(Color a, Color b, float t) {
        float u = clamp01(t);
        int r = Math.round(a.getRed() + (b.getRed() - a.getRed()) * u);
        int gr = Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * u);
        int bl = Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * u);
        return new Color(r, gr, bl);
    }
}
