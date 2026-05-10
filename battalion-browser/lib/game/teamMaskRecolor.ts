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
