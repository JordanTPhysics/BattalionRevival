import type { TeamPaintStyle } from "@/lib/game/teamPaintStyle";

/**
 * Team tint RGB aligned with {@code GameWindow#spriteTintForOwner} / {@code teamColorForPlacedSprite}
 * in the Swing client (hex 0xRRGGBB).
 */
const SWING_BLUE = 0x4682d2;
const SWING_GREEN = 0x50aa5a;
const SWING_YELLOW = 0xd2be46;
const SWING_RED = 0xc84646;
const SWING_DEFAULT = 0x968cdc;
/** {@link AssetManager#STRUCTURE_NEUTRAL_RECOLOR} */
export const SWING_STRUCTURE_NEUTRAL = 0x8c8c94;

/**
 * {@code structureTeam} / unit team id from snapshots (1 = red, 2 = blue, …).
 */
export function teamRgbFromTeamId(teamId: number | null): number {
  if (teamId == null || teamId <= 0) {
    return SWING_STRUCTURE_NEUTRAL;
  }
  switch (teamId) {
    case 1:
      return SWING_RED;
    case 2:
      return SWING_BLUE;
    case 3:
      return SWING_GREEN;
    case 4:
      return SWING_YELLOW;
    default:
      return SWING_DEFAULT;
  }
}

/**
 * Unit owner is {@code ownerSeatIndex}; engine teams are 1-based from seat + 1.
 */
export function unitRgbFromOwnerSeat(ownerSeatIndex: number): number {
  return teamRgbFromTeamId(ownerSeatIndex + 1);
}

/**
 * When {@code ownerSeatIndex} matches the local player's seat, use {@code clientPaint} for masked sprites;
 * otherwise use the authoritative Swing palette for that seat.
 */
export function resolveUnitTeamPaintStyle(
  ownerSeatIndex: number,
  yourSeatIndex: number | null | undefined,
  clientPaint: TeamPaintStyle
): TeamPaintStyle {
  if (yourSeatIndex != null && ownerSeatIndex === yourSeatIndex) {
    return clientPaint;
  }
  return { kind: "solid", rgb: unitRgbFromOwnerSeat(ownerSeatIndex) };
}
