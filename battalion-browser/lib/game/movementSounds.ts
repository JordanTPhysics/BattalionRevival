
const EXPLOSION_WAV = "/assets/sounds/217_ExplosionSound_ExplosionSound.wav";
const UBOAT_CLOAKING_WAV = "/assets/sounds/279_Cloak_Cloak.wav";
const TANK_CLOAKING_WAV = "/assets/sounds/179_Decloak_Decloak.wav";
const SCAVENGER_WAV = "/assets/sounds/168_Scavenge_Scavenge.wav";
const CAPTURE_WAV = "/assets/sounds/145_Capture_Capture.wav";
const BUILD_WAV = "/assets/sounds/454_ConstructUnit_ConstructUnit.wav";
const EXTRACT_RESOURCES_WAV = "/assets/sounds/303_ExtractResources_ExtractResources.wav";

const START_TURN_WAV = "/assets/sounds/295_StartTurn_StartTurn.wav";


const movementPlayers = new Map<string, HTMLAudioElement>();

/**
 * Lightweight SFX when a unit glides (one shot per authored move server snapshot). Uses one
 * pooled {@link Audio} per clip so rapid replays restart instead of stacking several instances.
 */
export function playMovementSfx(unitType: string): void {
  let src: string | null = null;
  switch (unitType) {
    case "Commando":
    case "Hcommando":
      src = "/assets/sounds/352_CommandoMove3_CommandoMove3.wav";
      break;
    case "Mortar":
    case "Rocket":
    case "Jammer":
      src = "/assets/sounds/125_VehicleMove_VehicleMove.wav";
      break;
    case "Scorpion":
    case "Lancer":
    case "Stealth":
    case "Flak":
      src = "/assets/sounds/163_TankMove1_TankMove1.wav";
      break;
    case "Battlecruiser":
    case "Corvette":
    case "Hunter":
    case "Intrepid":
    case "Leviathan":
      src = "/assets/sounds/453_ShipMove_ShipMove.wav";
      break;
    case "Uboat":
      src = "/assets/sounds/399_SubmarineMove_SubmarineMove.wav";
      break;
    case "Albatross":
    case "Condor":
      src = "/assets/sounds/344_BlimpMove_BlimpMove.wav";
      break;
    case "Raptor":
    case "Vulture":
      src = "/assets/sounds/516_AircraftMove_AircraftMove.wav";
      break;
    default:
      src = "/assets/sounds/353_CommandoMove2_CommandoMove2.wav";
  }
  if (!src || typeof Audio === "undefined") {
    return;
  }
  try {
    let a = movementPlayers.get(src);
    if (!a) {
      a = new Audio(src);
      a.volume = 0.45;
      movementPlayers.set(src, a);
    }
    a.pause();
    a.currentTime = 0;
    void a.play();
  } catch {
    /* ignore */
  }
}

/** One-shot when a unit is removed from the authoritative snapshot (destroyed). */
export function playExplosionSfx(): void {
  if (typeof Audio === "undefined") {
    return;
  }
  try {
    const a = new Audio(EXPLOSION_WAV);
    a.volume = 0.52;
    void a.play();
  } catch {
    /* ignore */
  }
}

export function playAttackSfx(unitType: string): void {
  // Play unit-specific attack SFX based on unitType.unitType if available, else fall back to generic logic.
  // This mapping should ideally cover each actual type string in UNIT_TYPE_STATS.
  let src: string;
  switch (unitType) {
    case "Commando":
    case "Spider":
      src = "/assets/sounds/444_FireStrikeCommando_FireStrikeCommando.wav"; break;
    case "Hcommando":
      src = "/assets/sounds/297_FireHeavyCommando_FireHeavyCommando.wav"; break;
    case "Scorpion":
    case "Lancer":
      src = "/assets/sounds/479_FireScorpionTank_FireScorpionTank.wav"; break;
    case "Flak":
      src = "/assets/sounds/190_FireFlakTank_FireFlakTank.wav"; break;
    case "Rocket":
      src = "/assets/sounds/233_FireRocketTruck_FireRocketTruck.wav"; break;
    case "Mortar":
      src = "/assets/sounds/559_FireMortarTruck_FireMortarTruck.wav"; break;
    case "Turret":
      src = "/assets/sounds/302_FireTurret_FireTurret.wav"; break;
    case "Annihilator":
      src = "/assets/sounds/3_FireAnnihilatorTank_FireAnnihilatorTank.wav"; break;
    case "Warmachine":
      src = "/assets/sounds/233_FireRocketTruck_FireRocketTruck.wav"; break;
    case "Blockade":
      src = "/assets/sounds/301_FireBlockade_FireBlockade.wav"; break;
    case "Battlecruiser":
      src = "/assets/sounds/338_FireBattlecruiser_FireBattlecruiser.wav"; break;
    case "Corvette":
      src = "/assets/sounds/335_FireCorvetteFighter_FireCorvetteFighter.wav"; break;
    case "Hunter":
      src = "/assets/sounds/304_FireHunterSupport_FireHunterSupport.wav"; break;
    case "Intrepid":
      src = "/assets/sounds/341_FireIntrepid_FireIntrepid.wav"; break;
    case "Uboat":
      src = "/assets/sounds/145_FireUboat_FireUboat.wav"; break;
    case "Vulture":
      src = "/assets/sounds/227_FireRaptorFighter_FireRaptorFighter.wav"; break;
    case "Raptor":
      src = "/assets/sounds/227_FireRaptorFighter_FireRaptorFighter.wav"; break;
    case "Condor":
      src = "/assets/sounds/529_FireCondorBomber_FireCondorBomber.wav"; break;
    case "Stealth":
      src = "/assets/sounds/575_FireStealthTank_FireStealthTank.wav"; break;
    default:
      // fallback sound
      src = "/assets/sounds/297_FireHeavyCommando_FireHeavyCommando.wav";
      break;
  }
  try {
    const a = new Audio(src);
    a.volume = 0.4;
    void a.play();
  } catch {
    /* ignore */
  }
}


export function playStartTurnSfx(): void {
  if (typeof Audio === "undefined") {
    return;
  }
  try {
    const a = new Audio(START_TURN_WAV);
    a.volume = 0.4;
    void a.play();
  } catch {
    /* ignore */
  }
}

export function playBuildSfx(): void {
  if (typeof Audio === "undefined") {
    return;
  }
  try {
    const a = new Audio(BUILD_WAV);
    a.volume = 0.4;
    void a.play();
  } catch {
    /* ignore */
  }
}

export function playExtractResourcesSfx(): void {
  if (typeof Audio === "undefined") {
    return;
  }
  try {
    const a = new Audio(EXTRACT_RESOURCES_WAV);
    a.volume = 0.4;
    void a.play();
  } catch {
    /* ignore */
  }
}

export function playCloakingSfx(): void {
  if (typeof Audio === "undefined") {
    return;
  }
  try {
    const a = new Audio(UBOAT_CLOAKING_WAV);
    a.volume = 0.4;
    void a.play();
  } catch {
    /* ignore */
  }
}

export function playScavengeSfx(): void {
  if (typeof Audio === "undefined") {
    return;
  }
  try {
    const a = new Audio(SCAVENGER_WAV);
    a.volume = 0.4;
    void a.play();
  } catch {
    /* ignore */
  }
}

export function playCaptureSfx(): void {
  if (typeof Audio === "undefined") {
    return;
  }
  try {
    const a = new Audio(CAPTURE_WAV);
    a.volume = 0.4;
    void a.play();
  } catch {
    /* ignore */
  }
}

export function playTankCloakingSfx(): void {
  if (typeof Audio === "undefined") {
    return;
  }
  try {
    const a = new Audio(TANK_CLOAKING_WAV);
    a.volume = 0.4;
    void a.play();
  } catch {
    /* ignore */
  }
}

