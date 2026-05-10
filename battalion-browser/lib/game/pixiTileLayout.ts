import type { Texture } from "pixi.js";
import { Sprite } from "pixi.js";

/** Must match Swing default cell scale intent (see {@code GameWindow.BASE_TILE_SIZE} range). */
export const TILE_PX = 48;

function texSize(tex: Texture): { iw: number; ih: number } {
  const w = tex.width;
  const h = tex.height;
  return { iw: Math.max(1, w), ih: Math.max(1, h) };
}

/** Scaled height for terrain when width is {@link TILE_PX} (matches {@link layoutTerrainSprite}). */
export function terrainScaledDrawHeight(tex: Texture): number {
  const { iw, ih } = texSize(tex);
  return Math.max(1, Math.round((ih * TILE_PX) / iw));
}

/**
 * {@link com.game.ui.AssetManager#drawTerrainImageOnTile} — width = tile, height from aspect; bottom aligned.
 */
export function layoutTerrainSprite(sprite: Sprite, tex: Texture, gx: number, gy: number): void {
  sprite.texture = tex;
  const { iw, ih } = texSize(tex);
  const drawW = TILE_PX;
  const drawH = Math.max(1, Math.round((ih * TILE_PX) / iw));
  sprite.width = drawW;
  sprite.height = drawH;
  sprite.x = gx * TILE_PX;
  sprite.y = gy * TILE_PX + TILE_PX - drawH;
}

/**
 * Same placement as {@link layoutUnitSprite} but coordinates relative to tile origin {@code (0,0)-(TILE,TILE)}
 * inside a container already positioned at {@code gx*TILE}, {@code gy*TILE}.
 *
 * Resets scale before sizing: Pixi v8 reapplies cached {@code _width}/{@code _height} on each texture swap;
 * when movement vs attack frames have different aspect ratios, that can squash the sprite until layout runs.
 */
export function layoutUnitSpriteInTileCell(sprite: Sprite, tex: Texture): void {
  sprite.texture = tex;
  sprite.scale.set(1);
  const { iw, ih } = texSize(tex);
  const drawW = TILE_PX;
  const drawH = Math.max(1, Math.round((ih * TILE_PX) / iw));
  const tileCx = TILE_PX * 0.5;
  const tileCy = TILE_PX * 0.5;
  sprite.setSize(drawW, drawH);
  sprite.x = tileCx - drawW * 0.5;
  sprite.y = tileCy - 0.75 * drawH;
}

/**
 * {@link com.game.ui.AssetManager#drawUnitFrameOnTile} — centered horizontally; vertical anchor -0.75 * h vs tile center.
 */
export function layoutUnitSprite(sprite: Sprite, tex: Texture, gx: number, gy: number): void {
  sprite.texture = tex;
  const { iw, ih } = texSize(tex);
  const drawW = TILE_PX;
  const drawH = Math.max(1, Math.round((ih * TILE_PX) / iw));
  const tileCx = gx * TILE_PX + TILE_PX * 0.5;
  const tileCy = gy * TILE_PX + TILE_PX * 0.5;
  sprite.width = drawW;
  sprite.height = drawH;
  sprite.x = tileCx - drawW * 0.5;
  sprite.y = tileCy - 0.75 * drawH;
}

/**
 * {@link GameWindow#drawStructure} — inset square (image stretched to inner cell like Swing {@code drawImage}).
 */
export function layoutStructureSprite(sprite: Sprite, tex: Texture, gx: number, gy: number): void {
  sprite.texture = tex;
  const inset = Math.max(2, Math.floor(TILE_PX / 8));
  const inner = TILE_PX - inset * 2;
  sprite.width = inner;
  sprite.height = inner;
  sprite.x = gx * TILE_PX + inset;
  sprite.y = gy * TILE_PX + inset;
}
