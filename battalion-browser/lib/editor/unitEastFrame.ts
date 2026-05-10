import { Texture } from "pixi.js";
import { copyEastFrameToCanvas } from "@/lib/game/eastFrameCrop";

const cache = new Map<string, Texture>();

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.decoding = "async";
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error("failed to load image"));
    img.src = src;
  });
}

/** Coloured unit sheet (legacy fallback): EAST frame 0 from a 6×N strip. Use uncoloured assets + team-mask for editor units. */
export async function eastFrameTexture(assetUrl: string): Promise<Texture | null> {
  const hit = cache.get(assetUrl);
  if (hit) return hit;
  try {
    const img = await loadImage(assetUrl);
    const canvas = copyEastFrameToCanvas(img);
    const tex = Texture.from(canvas);
    cache.set(assetUrl, tex);
    return tex;
  } catch {
    return null;
  }
}
