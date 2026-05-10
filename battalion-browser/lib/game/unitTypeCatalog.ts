/**
 * Mirrors {@link com.game.model.units.UnitType} base stats and ability flags for client UX
 * (path preview, attack eligibility, factory pricing).
 */
export type MovementKind = "FOOT" | "WHEELED" | "TRACKED" | "NAVAL" | "AIR";

export type ArmorType = "LIGHT" | "MEDIUM" | "HEAVY";
export type AttackType = "LIGHT" | "MEDIUM" | "HEAVY" | "NONE";

export const ANTI_AIR = "AntiAir";
export const ANTI_SUBMARINE = "AntiSubmarine";
export const MASSIVE_HULL = "MassiveHull";
export const JAMMER = "Jammer";
export const CLOAKER = "Cloaker";
export const TRACKER = "Tracker";
export const BLITZKRIEG = "Blitzkrieg";
export const BARRAGE = "Barrage";
export const CONQUEROR = "Conqueror";
export const SCAVENGER = "Scavenger";
export const EXPLOSIVE = "Explosive";
export const MAINTENANCE = "Maintenance";
export const AIMLESS = "Aimless";
export const STUNNING = "Stunning";
export const PIERCING = "Piercing";
export const RESUPPLY = "Resupply";
export const BEHEMOTH = "Behemoth";
export const RAPIDFIRE = "RapidFire";
export const STALWART = "Stalwart";
export const KINGPIN = "Kingpin";


export interface UnitTypeStats {
  unitType: string;
  startingHealth: number;
  damage: number;
  movementKind: MovementKind;
  movementSpeed: number;
  attackRange: number;
  armorType: ArmorType;
  attackType: AttackType;
  abilities: readonly string[];
  /** {@link com.game.model.units.UnitType#isTransport} */
  isTransport: boolean;
  factoryBuildCategory: "LAND" | "SEA" | "AIR";
}

function u(
  unitType: string,
  startingHealth: number,
  damage: number,
  movementKind: MovementKind,
  movementSpeed: number,
  attackRange: number,
  armorType: ArmorType,
  attackType: AttackType,
  abilities: readonly string[],
  isTransport: boolean,
  factoryBuildCategory: "LAND" | "SEA" | "AIR"
): UnitTypeStats {
  return {
    unitType,
    startingHealth,
    damage,
    movementKind,
    movementSpeed,
    attackRange,
    armorType,
    attackType,
    abilities,
    isTransport,
    factoryBuildCategory,
  };
}

/** Keyed by {@link com.game.network.protocol.UnitSnapshot#unitType} / {@code UnitType.name()}. */
export const UNIT_TYPE_STATS: Readonly<Record<string, UnitTypeStats>> = {
  Albatross: u("Albatross", 50, 0, "AIR", 6, 1, "LIGHT", "NONE", [], true, "AIR"),
  Battlecruiser: u("Battlecruiser", 140, 55, "NAVAL", 4, 6, "HEAVY", "HEAVY", [MASSIVE_HULL], false, "SEA"),
  Blockade: u("Blockade", 100, 0, "TRACKED", 0, 0, "HEAVY", "NONE", [MAINTENANCE, AIMLESS], false, "LAND"),
  Commando: u("Commando", 50, 25, "FOOT", 4, 1, "LIGHT", "LIGHT", [TRACKER, CONQUEROR], false, "LAND"),
  Condor: u("Condor", 55, 60, "AIR", 5, 1, "LIGHT", "HEAVY", [ANTI_SUBMARINE, EXPLOSIVE], false, "AIR"),
  Corvette: u("Corvette", 90, 45, "NAVAL", 6, 1, "HEAVY", "MEDIUM", [MASSIVE_HULL, BLITZKRIEG], false, "SEA"),
  Hunter: u("Hunter", 90, 35, "NAVAL", 5, 1, "MEDIUM", "LIGHT", [ANTI_AIR, ANTI_SUBMARINE, BARRAGE], false, "SEA"),
  Raptor: u("Raptor", 50, 20, "AIR", 7, 1, "LIGHT", "LIGHT", [ANTI_AIR, RAPIDFIRE], false, "AIR"),
  Scorpion: u("Scorpion", 70, 35, "TRACKED", 6, 1, "MEDIUM", "MEDIUM", [BLITZKRIEG], false, "LAND"),
  Leviathan: u("Leviathan", 100, 0, "NAVAL", 6, 2, "HEAVY", "NONE", [], true, "SEA"),
  Spider: u("Spider", 50, 30, "FOOT", 4, 1, "LIGHT", "LIGHT", [STUNNING], false, "LAND"),
  Mortar: u("Mortar", 55, 40, "WHEELED", 4, 3, "LIGHT", "LIGHT", [], false, "LAND"),
  Lancer: u("Lancer", 70, 35, "TRACKED", 6, 1, "MEDIUM", "MEDIUM", [PIERCING], false, "LAND"),
  Jammer: u("Jammer", 40, 0, "WHEELED", 4, 1, "LIGHT", "NONE", [JAMMER], false, "LAND"),
  Flak: u("Flak", 70, 20, "TRACKED", 5, 1, "MEDIUM", "LIGHT", [ANTI_AIR, RAPIDFIRE], false, "LAND"),
  Intrepid: u("Intrepid", 50, 25, "NAVAL", 5, 1, "LIGHT", "LIGHT", [SCAVENGER, CONQUEROR], false, "SEA"),
  Hcommando: u("Hcommando", 50, 40, "FOOT", 4, 1, "LIGHT", "HEAVY", [CONQUEROR, EXPLOSIVE], false, "LAND"),
  Rocket: u("Rocket", 40, 50, "WHEELED", 4, 5, "LIGHT", "HEAVY", [ANTI_AIR, ANTI_SUBMARINE, EXPLOSIVE], false, "LAND"),
  Turret: u("Turret", 100, 45, "TRACKED", 0, 5, "HEAVY", "MEDIUM", [ANTI_AIR, MAINTENANCE, AIMLESS], false, "LAND"),
  Uboat: u("Uboat", 40, 40, "NAVAL", 4, 1, "LIGHT", "HEAVY", [ANTI_SUBMARINE, CLOAKER], false, "SEA"),
  Vulture: u("Vulture", 60, 35, "AIR", 6, 1, "LIGHT", "MEDIUM", [SCAVENGER], false, "AIR"),
  Warmachine: u("Warmachine", 160, 48, "TRACKED", 3, 3, "HEAVY", "HEAVY", [KINGPIN, ANTI_AIR, RESUPPLY, BEHEMOTH], false, "LAND"),
  Stealth: u("Stealth", 40, 35, "TRACKED", 5, 1, "LIGHT", "MEDIUM", [CLOAKER], false, "LAND"),
  Annihilator: u("Annihilator", 140, 70, "TRACKED", 4, 1, "HEAVY", "HEAVY", [BEHEMOTH], false, "LAND"),
};

export function getUnitTypeStats(unitType: string): UnitTypeStats | null {
  return UNIT_TYPE_STATS[unitType] ?? null;
}

export function isWarmachineProducibleType(unitType: string): boolean {
  const s = getUnitTypeStats(unitType);
  if (!s || s.isTransport || unitType === "Warmachine" || s.movementSpeed <= 0) {
    return false;
  }
  return true;
}

export function factoryRosterTypes(): string[] {
  return Object.keys(UNIT_TYPE_STATS).filter((t) => !UNIT_TYPE_STATS[t].isTransport);
}

/** Mirrors Swing {@link com.game.ui.FactoryBuildDialog}: transports and Warmachine excluded from factory roster. */
export function factoryPurchasableUnitTypes(): string[] {
  return Object.keys(UNIT_TYPE_STATS).filter((t) => {
    const s = UNIT_TYPE_STATS[t];
    return s && !s.isTransport && t !== "Warmachine" && s.movementSpeed > 0;
  });
}

/** Mirrors {@link Unit#getMinAttackRange}. */
export function minAttackRangeForType(attackRange: number): number {
  return Math.ceil(attackRange / 2 - 1);
}

/** Mirrors {@link Unit#getAttackRangeDisplayString}. */
export function attackRangeDisplayString(attackRange: number): string {
  const minR = Math.max(0, minAttackRangeForType(attackRange));
  if (minR > 0 && minR < attackRange) {
    return `${minR}\u2013${attackRange}`;
  }
  return String(attackRange);
}

/** Mirrors {@link com.game.engine.PlayableGameSession#factoryBuildPrice}. */
export function factoryBuildPrice(unitType: string): number | null {
  const t = getUnitTypeStats(unitType);
  if (!t) {
    return null;
  }
  let priceMultiplier = 1.0;
  if (t.armorType === "HEAVY") {
    priceMultiplier *= 1.5;
  }
  if (t.armorType === "MEDIUM") {
    priceMultiplier *= 1.2;
  }
  if (t.attackType === "HEAVY") {
    priceMultiplier *= 1.5;
  }
  if (t.movementKind === "AIR") {
    priceMultiplier *= (t.movementSpeed * t.movementSpeed) / 12;
  }
  if (t.movementKind === "NAVAL") {
    priceMultiplier *= 1.3;
  }
  return Math.round(Math.max(50, (t.startingHealth / 2 + t.damage * 5) * priceMultiplier));
}

export function hasAbility(stats: UnitTypeStats | null, ability: string): boolean {
  if (!stats) {
    return false;
  }
  return stats.abilities.includes(ability);
}

export function isAircraftStats(stats: UnitTypeStats | null): boolean {
  return stats?.movementKind === "AIR";
}
