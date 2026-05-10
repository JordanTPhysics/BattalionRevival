/**
 * Swing {@link com.game.ui.AssetManager} unit sheets — EAST frame 0 is row 0, col 0 of a 6×N strip.
 * Returns a canvas copy (first frame, or full image if not a 6-column strip).
 */
export function copyEastFrameToCanvas(img: HTMLImageElement): HTMLCanvasElement {
  const sw = img.naturalWidth;
  const sh = img.naturalHeight;
  const cols = 6;
  const canvas = document.createElement("canvas");
  const ctx = canvas.getContext("2d");
  if (!ctx) {
    canvas.width = sw;
    canvas.height = sh;
    return canvas;
  }
  if (sw < cols || sw % cols !== 0) {
    canvas.width = sw;
    canvas.height = sh;
    ctx.drawImage(img, 0, 0);
    return canvas;
  }
  const frameW = Math.floor(sw / cols);
  const rows = sh % 4 === 0 ? 4 : 1;
  const frameH = Math.floor(sh / rows);
  canvas.width = frameW;
  canvas.height = frameH;
  ctx.drawImage(img, 0, 0, frameW, frameH, 0, 0, frameW, frameH);
  return canvas;
}
