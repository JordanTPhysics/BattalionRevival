/** Tooltips ported from Swing {@link com.game.ui.AbilityPresentation#tooltipText}. */
export function abilityTooltipText(abilityId: string): string {
  const id = abilityId ?? "";
  switch (id) {
    case "AntiAir":
      return "Anti-air: this unit may engage aircraft.";
    case "Blitzkrieg":
      return "Blitzkrieg: +20% damage when you attack first in the exchange.";
    case "Tracker":
      return "Tracker: discovers cloaked enemies stepped on during movement; +20% damage when attacking them.";
    case "Cloaker":
      return "Cloaker: after moving, hidden from enemies until revealed; double damage if you attack while cloaked. Orthogonal adjacency to an enemy exposes you when that unit's action ends (move-only or after combat).";
    case "Jammer":
      return "Jammer: projects a jam zone (Manhattan distance 2) that blocks aircraft movement and uncloaks stealth units.";
    case "RapidFire":
      return "Rapid fire: double damage vs light armor.";
    case "Piercing":
      return "Piercing: deals a second hit for 60% damage to the unit behind the primary target.";
    case "AntiSubmarine":
      return "Anti-submarine: may attack U-boats.";
    case "Conqueror":
      return "Conqueror: captures neutral or enemy structures by holding the tile for two full turns.";
    case "Explosive":
      return "Explosive: double damage vs units on structures and vs heavy armor.";
    case "Scavenger":
      return "Scavenger: when your attack destroys the main target, you keep your turn action (you may move and/or attack again).";
    case "Stalwart":
      return "Stalwart: 20% chance to survive a lethal hit with 1 HP.";
    case "Kingpin":
      return "Kingpin: for teams that field a Kingpin unit, losing all Kingpins causes immediate defeat. War Machines can spend an onboard fabrication budget instead of HQ funds (see context menu).";
    case "Aimless":
      return "Aimless: this unit does not count as a surviving combat unit for defeat checks.";
    case "Maintenance":
      return "Maintenance: heals 10% max HP at round start.";
    case "Resupply":
      return "Resupply: at round start, heals nearby allied units (Manhattan distance 2) for 10% max HP.";
    case "Stunning":
      return "Stunning: targets struck by this unit cannot counterattack.";
    case "MassiveHull":
      return "Massive hull: cannot enter shore tiles (coastal shallows).";
    case "Behemoth":
      return "Behemoth: deals 15% less damage when counterattacking.";
    default:
      return id.length === 0 ? "Special trait." : `Unit trait: ${id}.`;
  }
}

/** Swing {@link com.game.ui.AbilityPresentation} abbrev heuristic. */
export function abilityAbbrev(raw: string | null | undefined): string {
  if (raw == null || raw === "") return "?";
  if (raw.length <= 3) return raw.toUpperCase();
  return raw.substring(0, 2).toUpperCase();
}

export function hslBadgeStyle(abilityId: string): { h: number; s: number; l: number } {
  let h = 0;
  for (let i = 0; i < abilityId.length; i++) {
    h = ((h << 5) - h + abilityId.charCodeAt(i)) | 0;
  }
  const hue = Math.abs(h % 360) / 360;
  return {
    h: hue * 360,
    s: 35 + (Math.abs(h) % 20),
    l: 52 + ((Math.abs(h >> 8) % 15) / 100) * 35,
  };
}
