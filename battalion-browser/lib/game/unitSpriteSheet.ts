/**
 * Mirrors {@link com.game.ui.AssetManager} unit sheet layout: 6 columns, 4 bands of frames (when height permits).
 * @see docs/GAME_WINDOW.md — facings use the same column assignment as the desktop client.
 */
export const UNIT_SHEET_COLUMNS = 6;
export const UNIT_SHEET_ROW_BANDS = 4;
/** Attack sheets in {@code /assets/units/attack/uncoloured/} — one column per cardinal facing. */
export const UNIT_ATTACK_SHEET_COLUMNS = 4;

/**
 * Each atlas cell is 144×144; Swing paints the drawable from an inner 72×72 at (+37,+37).
 * Applied when cropping sheet frames so movement/attack swaps keep consistent on-map scale.
 */
export const UNIT_SHEET_CELL_TRIM_INSET = 37;
export const UNIT_SHEET_CELL_TRIM_SIZE = 72;
/** Nominal stride when art is authored on the 144px grid (decode still uses measured frameW/frameH). */
export const UNIT_SHEET_CELL_STRIDE_PX = 144;

export type CardinalFacing = "EAST" | "SOUTH" | "WEST" | "NORTH";

/** Java {@link AssetManager#columnsFor} — animation index alternates mirrored east/west columns. */
export function columnsForDirection(direction: CardinalFacing): readonly number[] {
  switch (direction) {
    case "EAST":
      return [0, 4];
    case "SOUTH":
      return [1];
    case "WEST":
      return [2, 5];
    case "NORTH":
      return [3];
  }
}

/** Java {@link AssetManager#frameColumnFor} */
export function sheetColumnForAnimation(direction: CardinalFacing, animationIndex: number): number {
  const cols = columnsForDirection(direction);
  const i = ((animationIndex % cols.length) + cols.length) % cols.length;
  return cols[i]!;
}

/** 4-column attack strip: EAST, SOUTH, WEST, NORTH respectively. */
export function sheetColumnForAttackAnimation(direction: CardinalFacing): number {
  switch (direction) {
    case "EAST":
      return 0;
    case "SOUTH":
      return 1;
    case "WEST":
      return 2;
    case "NORTH":
      return 3;
  }
}

/** Java {@link AssetManager#frameRowFor} — {@code animationIndex % sheet.rows()} */
export function sheetRowForAnimation(animationIndex: number, rowCount: number): number {
  const n = Math.max(1, rowCount);
  return ((animationIndex % n) + n) % n;
}

export function parseFacing(s: string | null | undefined): CardinalFacing {
  switch (s) {
    case "SOUTH":
    case "WEST":
    case "NORTH":
      return s;
    default:
      return "EAST";
  }
}

/** Step from grid cell {@code from} to {@code to} (single orthogonal step or diagonal resolved to dominant axis). */
export function facingFromGridStep(from: { x: number; y: number }, to: { x: number; y: number }): CardinalFacing {
  const dx = to.x - from.x;
  const dy = to.y - from.y;
  if (dx === 0 && dy === 0) return "EAST";
  if (Math.abs(dx) >= Math.abs(dy)) {
    return dx > 0 ? "EAST" : "WEST";
  }
  return dy > 0 ? "SOUTH" : "NORTH";
}

export interface SheetLayout {
  readonly frameW: number;
  readonly frameH: number;
  readonly rows: number;
  readonly columns: number;
}

export function decodeSheetLayout(naturalWidth: number, naturalHeight: number): SheetLayout | null {
  if (naturalWidth < UNIT_SHEET_COLUMNS || naturalWidth % UNIT_SHEET_COLUMNS !== 0) {
    return null;
  }
  const frameW = naturalWidth / UNIT_SHEET_COLUMNS;
  const rows = naturalHeight % UNIT_SHEET_ROW_BANDS === 0 ? UNIT_SHEET_ROW_BANDS : 1;
  const frameH = naturalHeight / rows;
  if (frameH < 1) return null;
  return { frameW, frameH, rows, columns: UNIT_SHEET_COLUMNS };
}

/**
 * Attack strip: fixed 4 columns, row count from {@code attack_rows.json} (per unit).
 */
export function decodeAttackSheetLayout(
  naturalWidth: number,
  naturalHeight: number,
  explicitRows: number
): SheetLayout | null {
  const cols = UNIT_ATTACK_SHEET_COLUMNS;
  if (
    explicitRows < 1 ||
    naturalWidth < cols ||
    naturalWidth % cols !== 0 ||
    naturalHeight % explicitRows !== 0
  ) {
    return null;
  }
  const frameW = naturalWidth / cols;
  const frameH = naturalHeight / explicitRows;
  if (frameH < 1) return null;
  return { frameW, frameH, rows: explicitRows, columns: cols };
}
