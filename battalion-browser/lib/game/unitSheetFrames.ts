import { Texture } from "pixi.js";
import type { SheetLayout } from "@/lib/game/unitSpriteSheet";
import {
  decodeAttackSheetLayout,
  decodeSheetLayout,
  sheetColumnForAnimation,
  sheetRowForAnimation,
  UNIT_SHEET_CELL_TRIM_INSET,
  UNIT_SHEET_CELL_TRIM_SIZE,
} from "@/lib/game/unitSpriteSheet";
import { applyTeamPaintStyleToImageData } from "@/lib/game/teamMaskRecolor";
import type { TeamPaintStyle } from "@/lib/game/teamPaintStyle";
import { teamPaintStyleCacheKey } from "@/lib/game/teamPaintStyle";
import { uncolouredAttackSheetUrl, uncolouredMovementSheetUrl, uncolouredUnitTextureUrl } from "@/lib/game/renderPaths";

const ATTACK_ROWS_MANIFEST_URL = "/assets/units/attack/attack_rows.json";

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.decoding = "async";
    img.crossOrigin = "anonymous";
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error(`failed to load image: ${src}`));
    img.src = src;
  });
}

const imageByUrl = new Map<string, HTMLImageElement | "err">();
const layoutByUrl = new Map<string, SheetLayout | null>();

const textureCache = new Map<string, Texture>();

let attackRowsManifest: Readonly<Record<string, number>> | null = null;
let attackRowsLoadPromise: Promise<Readonly<Record<string, number>>> | null = null;

async function ensureAttackRowsManifest(): Promise<Readonly<Record<string, number>>> {
  if (attackRowsManifest) {
    return attackRowsManifest;
  }
  if (!attackRowsLoadPromise) {
    attackRowsLoadPromise = fetch(ATTACK_ROWS_MANIFEST_URL)
      .then((r) => (r.ok ? r.json() : {}))
      .catch(() => ({}))
      .then((j: unknown) => {
        const o = j && typeof j === "object" && !Array.isArray(j) ? (j as Record<string, unknown>) : {};
        const out: Record<string, number> = {};
        for (const [k, v] of Object.entries(o)) {
          if (typeof v === "number" && Number.isFinite(v)) {
            out[k] = v;
          }
        }
        attackRowsManifest = out;
        return attackRowsManifest;
      });
  }
  return attackRowsLoadPromise;
}

/** JSON keys are {@code Modern-Red-Attack-UnitType} matching catalog PascalCase {@code UnitType.name()}. */
function attackManifestKeyForUnitType(unitType: string): string {
  return `Modern-Red-Attack-${unitType}`;
}

function isUncolouredAttackSheetUrl(sheetUrl: string): boolean {
  return sheetUrl.includes("/units/attack/uncoloured/");
}

function texKey(sheetUrl: string, row: number, col: number, paint: TeamPaintStyle): string {
  /** Bump when crop / masking changes so {@code textureCache} misses old full-cell bitmaps. */
  const cropRev = `trim${UNIT_SHEET_CELL_TRIM_INSET}_${UNIT_SHEET_CELL_TRIM_SIZE}`;
  return `${sheetUrl}\0${row}\0${col}\0${teamPaintStyleCacheKey(paint)}\0${cropRev}`;
}

async function computeLayoutForImage(
  sheetUrl: string,
  img: HTMLImageElement,
  opts?: PreloadSheetOptions
): Promise<SheetLayout | null> {
  if (
    opts?.decodeAttackRowsForUnitType &&
    isUncolouredAttackSheetUrl(sheetUrl)
  ) {
    const manifest = await ensureAttackRowsManifest();
    const rows = manifest[attackManifestKeyForUnitType(opts.decodeAttackRowsForUnitType)];
    if (typeof rows === "number") {
      const atk = decodeAttackSheetLayout(img.naturalWidth, img.naturalHeight, rows);
      if (atk) {
        return atk;
      }
    }
  }
  return decodeSheetLayout(img.naturalWidth, img.naturalHeight);
}

export type PreloadSheetOptions = {
  readonly decodeAttackRowsForUnitType?: string;
};

/** Loads the sheet if needed; records null layout when decode fails. */
export async function preloadUnitSheet(sheetUrl: string, opts?: PreloadSheetOptions): Promise<void> {
  const hit = imageByUrl.get(sheetUrl);
  if (hit === "err") return;
  if (hit) {
    if (!layoutByUrl.has(sheetUrl)) {
      layoutByUrl.set(sheetUrl, await computeLayoutForImage(sheetUrl, hit, opts));
    }
    return;
  }
  try {
    const img = await loadImage(sheetUrl);
    imageByUrl.set(sheetUrl, img);
    layoutByUrl.set(sheetUrl, await computeLayoutForImage(sheetUrl, img, opts));
  } catch {
    imageByUrl.set(sheetUrl, "err");
    layoutByUrl.set(sheetUrl, null);
  }
}

export function getSheetLayoutAfterPreload(sheetUrl: string): SheetLayout | null {
  return layoutByUrl.get(sheetUrl) ?? null;
}

/** Choose first URL that decodes as a valid unit sheet; attack tries 4-column + {@code attack_rows.json} first. */
export async function pickUnitSheetUrl(
  unitType: string,
  mode: "move" | "attack"
): Promise<{ url: string; layout: SheetLayout | null }> {
  const attackUncUrl = uncolouredAttackSheetUrl(unitType);
  const tryList =
    mode === "attack"
      ? [attackUncUrl, uncolouredMovementSheetUrl(unitType), uncolouredUnitTextureUrl(unitType)]
      : [uncolouredMovementSheetUrl(unitType), uncolouredUnitTextureUrl(unitType)];
  for (const u of tryList) {
    const attackOpts: PreloadSheetOptions | undefined =
      mode === "attack" && u === attackUncUrl ? { decodeAttackRowsForUnitType: unitType } : undefined;
    await preloadUnitSheet(u, attackOpts);
    const L = layoutByUrl.get(u);
    if (L) {
      return { url: u, layout: L };
    }
  }
  const legacy = uncolouredUnitTextureUrl(unitType);
  await preloadUnitSheet(legacy);
  return { url: legacy, layout: layoutByUrl.get(legacy) ?? null };
}

/** Crops + team-masks one sheet cell to a canvas (no Pixi texture cache). */
function buildMaskedSheetFrameCanvas(
  sheetUrl: string,
  paint: TeamPaintStyle,
  row: number,
  col: number
): HTMLCanvasElement | null {
  const img = imageByUrl.get(sheetUrl);
  if (!img || img === "err") return null;
  let L = layoutByUrl.get(sheetUrl);
  if (L === undefined) {
    L = decodeSheetLayout(img.naturalWidth, img.naturalHeight);
    layoutByUrl.set(sheetUrl, L);
  }
  if (L == null) return null;
  if (row < 0 || row >= L.rows || col < 0 || col >= L.columns) return null;

  const { frameW, frameH } = L;
  const cellLeft = col * frameW;
  const cellTop = row * frameH;
  const inset = UNIT_SHEET_CELL_TRIM_INSET;
  const inner = UNIT_SHEET_CELL_TRIM_SIZE;
  const useInner =
    frameW >= inset + inner &&
    frameH >= inset + inner &&
    cellLeft + inset + inner <= img.naturalWidth &&
    cellTop + inset + inner <= img.naturalHeight;

  let sx = cellLeft;
  let sy = cellTop;
  let sw = frameW;
  let sh = frameH;
  if (useInner) {
    sx = cellLeft + inset;
    sy = cellTop + inset;
    sw = inner;
    sh = inner;
  }

  const canvas = document.createElement("canvas");
  canvas.width = sw;
  canvas.height = sh;
  const ctx = canvas.getContext("2d");
  if (!ctx) return null;
  ctx.drawImage(img, sx, sy, sw, sh, 0, 0, sw, sh);
  const id = ctx.getImageData(0, 0, sw, sh);
  applyTeamPaintStyleToImageData(id, paint);
  ctx.putImageData(id, 0, 0);
  return canvas;
}

/**
 * First movement-sheet frame (row 0, EAST column 0 swap) — for build UI thumbnails.
 */
export async function getMovementSheetFirstFrameDataUrl(
  unitType: string,
  paint: TeamPaintStyle
): Promise<string | null> {
  const { url, layout } = await pickUnitSheetUrl(unitType, "move");
  if (!layout) {
    return null;
  }
  const row = sheetRowForAnimation(0, layout.rows);
  const col = sheetColumnForAnimation("EAST", 0);
  const canvas = buildMaskedSheetFrameCanvas(url, paint, row, col);
  if (!canvas) {
    return null;
  }
  try {
    return canvas.toDataURL("image/png");
  } catch {
    return null;
  }
}

/**
 * Crops one frame, applies team-mask recolour, returns a Pixi texture (cached per url/row/col/rgb).
 */
export function getMaskedSheetFrameTextureSync(
  sheetUrl: string,
  paint: TeamPaintStyle,
  row: number,
  col: number
): Texture | null {
  const key = texKey(sheetUrl, row, col, paint);
  const cached = textureCache.get(key);
  if (cached) return cached;

  const canvas = buildMaskedSheetFrameCanvas(sheetUrl, paint, row, col);
  if (!canvas) return null;
  const tex = Texture.from(canvas);
  textureCache.set(key, tex);
  return tex;
}
