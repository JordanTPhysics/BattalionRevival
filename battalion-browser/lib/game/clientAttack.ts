/**
 * Client-side attack validity preview (mirrors key rules from
 * {@link com.game.engine.PlayableGameSession#canExecuteAttack},
 * {@link com.game.model.units.EngagementRules}, {@link com.game.systems.CombatTerrain}).
 */
import type { GridPoint, MatchSnapshot, UnitSnapshot } from "@/lib/protocol/types";
import {
  ANTI_AIR,
  ANTI_SUBMARINE,
  MASSIVE_HULL,
  JAMMER,
  CLOAKER,
  TRACKER,
  BLITZKRIEG,
  BARRAGE,
  CONQUEROR,
  SCAVENGER,
  EXPLOSIVE,
  getUnitTypeStats,
  isAircraftStats,
  minAttackRangeForType,
} from "@/lib/game/unitTypeCatalog";
import { isCanyonTerrain, terrainGrantsRangedHillRangeBonus } from "@/lib/game/terrainMovement";

function manhattan(a: GridPoint, b: GridPoint): number {
  return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
}

function isRangedAttacker(u: UnitSnapshot): boolean {
  const s = getUnitTypeStats(u.unitType);
  if (!s) {
    return false;
  }
  if (s.attackRange <= 1) {
    return false;
  }
  if (s.damage <= 0 || s.attackType === "NONE") {
    return false;
  }
  return true;
}

/** Mirrors Swing {@link com.game.engine.PlayableGameSession} attack reach (hills bonus for indirect). */
export function effectiveMaxAttackRange(snapshot: MatchSnapshot, shooter: UnitSnapshot): number {
  const s = getUnitTypeStats(shooter.unitType);
  if (!s) {
    return 0;
  }
  const base = s.attackRange;
  const row = snapshot.tiles[shooter.y];
  const tile = row?.[shooter.x];
  if (!tile || !isRangedAttacker(shooter)) {
    return base;
  }
  if (terrainGrantsRangedHillRangeBonus(tile.terrain)) {
    return base + 1;
  }
  return base;
}

/** Mirrors GameWindow.GameMapPanel#isIndirectFireUnit — range preview band, not identical to ranged damage rules. */
export function isIndirectFireUnit(u: UnitSnapshot): boolean {
  const s = getUnitTypeStats(u.unitType);
  return s != null && s.attackRange > 1;
}

/**
 * Geometric + type gates for whether {@code attacker} could strike {@code defender} from its
 * current tile (no turn-ownership or {@code hasMoved} checks). Used to pair opponent attack visuals
 * with health deltas between snapshots.
 */
export function attackPairingReachable(
  snapshot: MatchSnapshot,
  attacker: UnitSnapshot,
  defender: UnitSnapshot
): boolean {
  if (defender.ownerSeatIndex === attacker.ownerSeatIndex) {
    return false;
  }
  const atkStats = getUnitTypeStats(attacker.unitType);
  if (!atkStats || atkStats.damage <= 0 || atkStats.attackType === "NONE") {
    return false;
  }
  if (defender.cloaked) {
    return false;
  }
  const dist = manhattan(
    { x: attacker.x, y: attacker.y },
    { x: defender.x, y: defender.y }
  );
  const maxReach = effectiveMaxAttackRange(snapshot, attacker);
  const minR = Math.max(0, minAttackRangeForType(atkStats.attackRange));
  if (dist > maxReach || dist < minR) {
    return false;
  }
  const defRow = snapshot.tiles[defender.y];
  const defTile = defRow?.[defender.x];
  if (defTile && isRangedAttacker(attacker) && isCanyonTerrain(defTile.terrain)) {
    return false;
  }
  if (attacker.unitType === "Uboat") {
    if (getUnitTypeStats(defender.unitType)?.movementKind !== "NAVAL") {
      return false;
    }
  }
  if (isAircraftStats(getUnitTypeStats(defender.unitType)) && !hasAbility(attacker, ANTI_AIR)) {
    return false;
  }
  if (defender.unitType === "Uboat" && !hasAbility(attacker, ANTI_SUBMARINE)) {
    return false;
  }
  return true;
}

export function canClientPreviewAttack(
  snapshot: MatchSnapshot,
  activeSeatIndex: number,
  attacker: UnitSnapshot,
  defender: UnitSnapshot
): boolean {
  if (snapshot.activePlayerIndex !== activeSeatIndex) {
    return false;
  }
  if (snapshot.matchFinished) {
    return false;
  }
  if (attacker.ownerSeatIndex !== activeSeatIndex || defender.ownerSeatIndex === attacker.ownerSeatIndex) {
    return false;
  }
  if (attacker.hasMoved) {
    return false;
  }

  return attackPairingReachable(snapshot, attacker, defender);
}

function hasAbility(u: UnitSnapshot, ability: string): boolean {
  const s = getUnitTypeStats(u.unitType);
  return s ? s.abilities.includes(ability) : false;
}
