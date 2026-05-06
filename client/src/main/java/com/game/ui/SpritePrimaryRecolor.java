package com.game.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Re-tints sprite pixels that match a configurable "primary" paint (defaults to red) so the same art can
 * represent multiple teams while preserving line-art detail.
 */
public final class SpritePrimaryRecolor {

    private SpritePrimaryRecolor() {
    }

    /**
     * Describes which source pixels count as primary paint.
     *
     * @param referenceRgb opaque RGB ({@code 0x00RRGGBB}) of the primary to detect (for example pure red)
     * @param maxRgbDistance inclusive upper bound on Euclidean RGB distance for recolor participation
     * @param minAlpha source pixels with alpha strictly below this are left unchanged
     */
    public record PrimaryColorSpec(int referenceRgb, float maxRgbDistance, int minAlpha) {
        public PrimaryColorSpec {
            if (maxRgbDistance <= 0) {
                throw new IllegalArgumentException("maxRgbDistance must be positive");
            }
            if (minAlpha < 0 || minAlpha > 255) {
                throw new IllegalArgumentException("minAlpha must be 0..255");
            }
        }

        /** Defaults tuned for red team trim with anti-aliased edges. */
        public static PrimaryColorSpec defaultRedTeamPrimary() {
            return new PrimaryColorSpec(0x00FF0000, 190f, 8);
        }
    }

    /** Supplies replacement opaque RGB ({@code 0x00RRGGBB}) for each sprite pixel. */
    @FunctionalInterface
    public interface ReplacementProvider {
        int replacementRgb(int x, int y, int spriteWidth, int spriteHeight);
    }

    public static ReplacementProvider solidColor(Color color) {
        Objects.requireNonNull(color, "color");
        int rgb = color.getRGB() & 0x00FFFFFF;
        return (x, y, w, h) -> rgb;
    }

    /**
     * Tiles {@code pattern} over the sprite; {@code pattern} may be any {@link BufferedImage} type readable via
     * {@link BufferedImage#getRGB(int, int)}.
     */
    public static ReplacementProvider tiledPattern(BufferedImage pattern) {
        Objects.requireNonNull(pattern, "pattern");
        int pw = pattern.getWidth();
        int ph = pattern.getHeight();
        if (pw <= 0 || ph <= 0) {
            throw new IllegalArgumentException("pattern must have positive width and height");
        }
        return (x, y, w, h) -> pattern.getRGB(Math.floorMod(x, pw), Math.floorMod(y, ph)) & 0x00FFFFFF;
    }

    /**
     * Copies {@code source} into a new ARGB image, recolors red-primary pixels using a hybrid strategy, and returns
     * that copy. Strong primary pixels are replaced more aggressively; fringe pixels are softly blended.
     * {@code source} is not modified (safe for {@link BufferedImage#getSubimage} views).
     */
    public static BufferedImage recolorPrimaryPixels(
        BufferedImage source,
        PrimaryColorSpec spec,
        ReplacementProvider replacement
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(replacement, "replacement");

        int w = source.getWidth();
        int h = source.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }

        int refR = (spec.referenceRgb >> 16) & 0xFF;
        int refG = (spec.referenceRgb >> 8) & 0xFF;
        int refB = spec.referenceRgb & 0xFF;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = out.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a < spec.minAlpha) {
                    continue;
                }
                int sr = (argb >> 16) & 0xFF;
                int sg = (argb >> 8) & 0xFF;
                int sb = argb & 0xFF;

                int maxChannel = Math.max(sr, Math.max(sg, sb));
                int minChannel = Math.min(sr, Math.min(sg, sb));
                int chroma = maxChannel - minChannel;
                if (chroma < 18 && maxChannel < 90) {
                    continue; // Protect dark neutral detail (black/grey outlines and shadows).
                }
                if (sr <= sg + 8 || sr <= sb + 8) {
                    continue; // Require red dominance to avoid repainting non-primary details.
                }

                float dr = sr - refR;
                float dg = sg - refG;
                float db = sb - refB;
                float dist = (float) Math.sqrt(dr * dr + dg * dg + db * db);
                if (dist > spec.maxRgbDistance) {
                    continue;
                }

                int rep = replacement.replacementRgb(x, y, w, h);
                int rr = (rep >> 16) & 0xFF;
                int rg = (rep >> 8) & 0xFF;
                int rb = rep & 0xFF;

                float hardRadius = spec.maxRgbDistance * 0.42f;
                float mix = dist <= hardRadius
                    ? 1f
                    : 0.35f + 0.65f * ((spec.maxRgbDistance - dist) / Math.max(1f, spec.maxRgbDistance - hardRadius));
                mix = clamp01(mix);

                int outR = blendChannel(sr, rr, mix);
                int outG = blendChannel(sg, rg, mix);
                int outB = blendChannel(sb, rb, mix);
                out.setRGB(x, y, (a << 24) | (outR << 16) | (outG << 8) | outB);
            }
        }
        return out;
    }

    /** Convenience: {@link PrimaryColorSpec#defaultRedTeamPrimary()} and a solid replacement colour. */
    public static BufferedImage recolorDefaultRedTo(BufferedImage source, Color teamColor) {
        return recolorPrimaryPixels(source, PrimaryColorSpec.defaultRedTeamPrimary(), solidColor(teamColor));
    }

    private static int blendChannel(int src, int dst, float mix) {
        return Math.min(255, Math.max(0, Math.round(src * (1f - mix) + dst * mix)));
    }

    private static float clamp01(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }
}
