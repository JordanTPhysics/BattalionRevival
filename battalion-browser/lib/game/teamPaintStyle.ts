/**
 * Client-side paint for team-masked unit sprites (uncoloured assets, R=1 mask).
 * Only {@link resolveUnitTeamPaintStyle} should choose when to apply the player's override.
 */
export type TeamPaintStyle =
  | { kind: "solid"; rgb: number }
  | { kind: "linear"; angleDeg: number; from: number; to: number };

/** Default matches Swing team-1 red — used until the player picks another look. */
export const DEFAULT_PLAYER_UNIT_RGB = 0xc84646;

export function teamPaintStyleCacheKey(style: TeamPaintStyle): string {
  if (style.kind === "solid") {
    return `s:${style.rgb}`;
  }
  return `g:${style.angleDeg}:${style.from}:${style.to}`;
}

/** Pixi {@code tint} is a single multiply — use midpoint for gradients on legacy tinted sprites. */
export function approximateTintFromPaintStyle(style: TeamPaintStyle): number {
  if (style.kind === "solid") {
    return style.rgb;
  }
  const fr = (style.from >> 16) & 0xff;
  const fg = (style.from >> 8) & 0xff;
  const fb = style.from & 0xff;
  const tr = (style.to >> 16) & 0xff;
  const tg = (style.to >> 8) & 0xff;
  const tb = style.to & 0xff;
  const r = Math.round((fr + tr) / 2);
  const g = Math.round((fg + tg) / 2);
  const b = Math.round((fb + tb) / 2);
  return (r << 16) | (g << 8) | b;
}
