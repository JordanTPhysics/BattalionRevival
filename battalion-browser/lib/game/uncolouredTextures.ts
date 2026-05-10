import { Texture } from "pixi.js";
import { copyEastFrameToCanvas } from "@/lib/game/eastFrameCrop";
import { applyTeamColorMaskToImageData } from "@/lib/game/teamMaskRecolor";

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.decoding = "async";
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error(`failed to load image: ${src}`));
    img.src = src;
  });
}

function canvasToTeamTexture(source: HTMLCanvasElement, teamRgb: number): Texture {
  const w = source.width;
  const h = source.height;
  const out = document.createElement("canvas");
  out.width = w;
  out.height = h;
  const ctx = out.getContext("2d");
  if (!ctx) return Texture.from(source);
  ctx.drawImage(source, 0, 0);
  const id = ctx.getImageData(0, 0, w, h);
  applyTeamColorMaskToImageData(id, teamRgb);
  ctx.putImageData(id, 0, 0);
  return Texture.from(out);
}

/** Cached first-frame canvas per asset URL (uncoloured unit sheet after crop). */
const eastFrameBaseCanvas = new Map<string, HTMLCanvasElement>();

async function getEastFrameBaseCanvas(assetUrl: string): Promise<HTMLCanvasElement | null> {
  const hit = eastFrameBaseCanvas.get(assetUrl);
  if (hit) return hit;
  try {
    const img = await loadImage(assetUrl);
    const canvas = copyEastFrameToCanvas(img);
    eastFrameBaseCanvas.set(assetUrl, canvas);
    return canvas;
  } catch {
    return null;
  }
}

const eastFrameTeamTextureCache = new Map<string, Texture>();

/** Uncoloured unit PNG (6×N strip or single image); team mask applied after east-frame crop. */
export async function getUncolouredEastFrameTeamTexture(
  uncolouredUnitUrl: string,
  teamRgb: number
): Promise<Texture | null> {
  const cacheKey = `${uncolouredUnitUrl}\0${teamRgb}`;
  const cached = eastFrameTeamTextureCache.get(cacheKey);
  if (cached) return cached;
  const base = await getEastFrameBaseCanvas(uncolouredUnitUrl);
  if (!base) return null;
  const tex = canvasToTeamTexture(base, teamRgb);
  eastFrameTeamTextureCache.set(cacheKey, tex);
  return tex;
}

const fullImageTeamTextureCache = new Map<string, Texture>();

/** Full-image uncoloured structure (or unit) PNG; team mask applied to whole bitmap. */
export async function getUncolouredFullImageTeamTexture(
  uncolouredUrl: string,
  teamRgb: number
): Promise<Texture | null> {
  const cacheKey = `${uncolouredUrl}\0${teamRgb}`;
  const cached = fullImageTeamTextureCache.get(cacheKey);
  if (cached) return cached;
  try {
    const img = await loadImage(uncolouredUrl);
    const canvas = document.createElement("canvas");
    canvas.width = img.naturalWidth;
    canvas.height = img.naturalHeight;
    const ctx = canvas.getContext("2d");
    if (!ctx) return null;
    ctx.drawImage(img, 0, 0);
    const tex = canvasToTeamTexture(canvas, teamRgb);
    fullImageTeamTextureCache.set(cacheKey, tex);
    return tex;
  } catch {
    return null;
  }
}
