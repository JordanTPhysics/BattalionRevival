import { Assets, Rectangle, Texture } from "pixi.js";

/** Matches Swing {@link com.game.ui.AssetManager#MIN_TERRAIN_STRIP_TOTAL_WIDTH}. */
export const MIN_TERRAIN_STRIP_TOTAL_WIDTH = 64;
export const TERRAIN_STRIP_FRAME_COUNT = 4;
export const TERRAIN_STRIP_FRAME_MS = 1000;

const frameTexturesByUrl = new Map<string, Texture[]>();

export function isAnimatedTerrainSheetUrl(url: string): boolean {
  return url.includes("/terrain/animated/") && url.toLowerCase().endsWith(".png");
}

export function isFourFrameHorizontalStripPixelSize(iw: number, ih: number): boolean {
  return (
    iw > 0 &&
    ih > 0 &&
    iw % TERRAIN_STRIP_FRAME_COUNT === 0 &&
    iw >= MIN_TERRAIN_STRIP_TOTAL_WIDTH
  );
}

export function terrainStripFrameIndex(nowMs: number): number {
  return Math.floor(nowMs / TERRAIN_STRIP_FRAME_MS) % TERRAIN_STRIP_FRAME_COUNT;
}

function solidTextureRgb(hex: number, w: number, h: number): Texture {
  const canvas = document.createElement("canvas");
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext("2d");
  if (ctx) {
    ctx.fillStyle = `#${hex.toString(16).padStart(6, "0")}`;
    ctx.fillRect(0, 0, w, h);
  }
  return Texture.from(canvas);
}

/**
 * Four {@link Texture}s sharing one GPU source, each showing one horizontal cell of the strip.
 * Cached per URL. Falls back to four solid tiles when load fails or dimensions are not a 4-wide strip.
 */
export async function loadTerrainStripFrameTextures(
  url: string,
  fallRgb: number
): Promise<Texture[]> {
  if (!isAnimatedTerrainSheetUrl(url)) {
    const t = solidTextureRgb(fallRgb, 32, 32);
    return [t, t, t, t];
  }

  const cached = frameTexturesByUrl.get(url);
  if (cached && cached.length === TERRAIN_STRIP_FRAME_COUNT) {
    return cached;
  }

  let sheet: Texture;
  try {
    sheet = await Assets.load<Texture>(url);
  } catch {
    const t = solidTextureRgb(fallRgb, 32, 32);
    return [t, t, t, t];
  }

  const iw = Math.max(1, Math.round(sheet.source.width));
  const ih = Math.max(1, Math.round(sheet.source.height));
  if (!isFourFrameHorizontalStripPixelSize(iw, ih)) {
    const t = solidTextureRgb(fallRgb, 32, 32);
    return [t, t, t, t];
  }

  const frameW = iw / TERRAIN_STRIP_FRAME_COUNT;
  const src = sheet.source;
  const frames: Texture[] = [];
  for (let i = 0; i < TERRAIN_STRIP_FRAME_COUNT; i++) {
    frames.push(
      new Texture({
        source: src,
        frame: new Rectangle(i * frameW, 0, frameW, ih),
      })
    );
  }
  frameTexturesByUrl.set(url, frames);
  return frames;
}
