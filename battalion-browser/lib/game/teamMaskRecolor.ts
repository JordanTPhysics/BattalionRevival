import type { TeamPaintStyle } from "@/lib/game/teamPaintStyle";

/**
 * "Uncoloured" PNGs mark team-tint pixels with red channel = 1 (byte), from desktop asset prep.
 * Replace those pixels with the target team RGB; alpha is preserved.
 */
export const UNCOLOURED_TEAM_MASK_R = 1;

export function applyTeamColorMaskToImageData(imageData: ImageData, rgb: number): void {
  const tr = (rgb >> 16) & 0xff;
  const tg = (rgb >> 8) & 0xff;
  const tb = rgb & 0xff;
  const d = imageData.data;
  for (let i = 0; i < d.length; i += 4) {
    if (d[i] === UNCOLOURED_TEAM_MASK_R) {
      d[i] = tr;
      d[i + 1] = tg;
      d[i + 2] = tb;
    }
  }
}

function lerpByte(a: number, b: number, t: number): number {
  return Math.round(a + (b - a) * t);
}

/** Apply solid or linear-gradient fill to mask pixels (by image pixel coordinates). */
export function applyTeamPaintStyleToImageData(imageData: ImageData, style: TeamPaintStyle): void {
  if (style.kind === "solid") {
    applyTeamColorMaskToImageData(imageData, style.rgb);
    return;
  }
  const w = imageData.width;
  const h = imageData.height;
  const rad = (style.angleDeg * Math.PI) / 180;
  const ux = Math.cos(rad);
  const uy = Math.sin(rad);
  let tMin = Infinity;
  let tMax = -Infinity;
  const corners: readonly [number, number][] = [
    [0, 0],
    [w, 0],
    [0, h],
    [w, h],
  ];
  for (const [cx, cy] of corners) {
    const t = cx * ux + cy * uy;
    tMin = Math.min(tMin, t);
    tMax = Math.max(tMax, t);
  }
  const span = Math.max(1e-6, tMax - tMin);
  const fr = (style.from >> 16) & 0xff;
  const fg = (style.from >> 8) & 0xff;
  const fb = style.from & 0xff;
  const tr = (style.to >> 16) & 0xff;
  const tg = (style.to >> 8) & 0xff;
  const tb = style.to & 0xff;
  const d = imageData.data;
  for (let py = 0; py < h; py++) {
    for (let px = 0; px < w; px++) {
      const i = (py * w + px) * 4;
      if (d[i] !== UNCOLOURED_TEAM_MASK_R) continue;
      let tn = ((px + 0.5) * ux + (py + 0.5) * uy - tMin) / span;
      tn = Math.min(1, Math.max(0, tn));
      d[i] = lerpByte(fr, tr, tn);
      d[i + 1] = lerpByte(fg, tg, tn);
      d[i + 2] = lerpByte(fb, tb, tn);
    }
  }
}
