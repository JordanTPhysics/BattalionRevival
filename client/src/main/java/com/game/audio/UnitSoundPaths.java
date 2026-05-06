package com.game.audio;

import com.game.model.units.UnitType;

import java.util.ArrayList;
import java.util.List;

/**
 * Central place to map SFX: type the full classpath resource path for each case
 * (paths are what {@link Class#getResourceAsStream(String)} expects, e.g. {@code /assets/sounds/foo.wav}).
 * <p>
 * Movement uses {@link UnitType.MovementKind}; attack uses {@link UnitType}. Return {@code null} to skip.
 */
public final class UnitSoundPaths {

    private static final String SOUND_DIRECTORY = "/assets/sounds/";

    private UnitSoundPaths() {
    }

    /**
     * Every classpath sound resource the game can play, with {@code null}/blank entries removed.
     * Used by {@link ClasspathWavPlayer#prewarm} so the first play of any sound doesn't pay the
     * decode + mixer-open cost.
     */
    public static List<String> allKnownClasspaths() {
        List<String> paths = new ArrayList<>();
        paths.add(destroyedUnitExplosionClasspath());
        for (UnitType.MovementKind kind : UnitType.MovementKind.values()) {
            String p = movementClasspath(kind);
            if (p != null && !p.isBlank()) {
                paths.add(p);
            }
        }
        for (UnitType type : UnitType.values()) {
            String p = attackClasspath(type);
            if (p != null && !p.isBlank() && !paths.contains(p)) {
                paths.add(p);
            }
        }
        return paths;
    }

    /** Played when a unit is reduced to 0 HP during combat (outgoing or counter). */
    public static String destroyedUnitExplosionClasspath() {
        return SOUND_DIRECTORY + "217_ExplosionSound_ExplosionSound.wav";
    }

    public static String captureStructureClasspath() {
        return SOUND_DIRECTORY + "217_ExplosionSound_ExplosionSound.wav";
    }

    public static String tankCloakingClasspath() {
        return SOUND_DIRECTORY + "179_Decloak_Decloak.wav";
    }

    public static String uboatCloakingClasspath() {
        return SOUND_DIRECTORY + "217_ExplosionSound_ExplosionSound.wav";
    }

    public static String movementClasspath(UnitType.MovementKind kind) {
        return switch (kind) {
            case FOOT -> SOUND_DIRECTORY + "353_CommandoMove2_CommandoMove2.wav";
            case WHEELED -> SOUND_DIRECTORY + "125_VehicleMove_VehicleMove.wav";
            case TRACKED -> SOUND_DIRECTORY + "162_TankMove2_TankMove2.wav";
            case NAVAL -> SOUND_DIRECTORY + "453_ShipMove_ShipMove.wav";
            case AIR -> SOUND_DIRECTORY + "516_AircraftMove_AircraftMove.wav";
        };
    }

    public static String attackClasspath(UnitType type) {
        return switch (type) {
            case Albatross -> null;
            case Battlecruiser -> SOUND_DIRECTORY + "338_FireBattlecruiser_FireBattlecruiser.wav";
            case Blockade -> null;
            case Commando -> SOUND_DIRECTORY + "444_FireStrikeCommando_FireStrikeCommando.wav";
            case Condor -> SOUND_DIRECTORY + "529_FireCondorBomber_FireCondorBomber.wav";
            case Corvette -> SOUND_DIRECTORY + "335_FireCorvetteFighter_FireCorvetteFighter.wav";
            case Hunter -> SOUND_DIRECTORY + "304_FireHunterSupport_FireHunterSupport.wav";
            case Raptor, Vulture -> SOUND_DIRECTORY + "227_FireRaptorFighter_FireRaptorFighter.wav";
            case Scorpion, Lancer -> SOUND_DIRECTORY + "479_FireScorpionTank_FireScorpionTank.wav";
            case Leviathan -> SOUND_DIRECTORY + "561_FireLeviathan_FireLeviathan.wav";
            case Spider -> SOUND_DIRECTORY + "624_FireSpider_FireSpider.wav";
            case Mortar -> SOUND_DIRECTORY + "559_FireMortarTruck_FireMortarTruck.wav";
            case Jammer -> null;
            case Flak -> SOUND_DIRECTORY + "190_FireFlakTank_FireFlakTank.wav";
            case Intrepid -> SOUND_DIRECTORY + "341_FireIntrepid_FireIntrepid.wav";
            case Hcommando -> SOUND_DIRECTORY + "233_FireRocketTruck_FireRocketTruck.wav";
            case Rocket, Warmachine -> SOUND_DIRECTORY + "233_FireRocketTruck_FireRocketTruck.wav";
            case Turret -> SOUND_DIRECTORY + "302_FireTurret_FireTurret.wav";
            case Uboat -> SOUND_DIRECTORY + "145_FireUboat_FireUboat.wav";
            case Stealth -> SOUND_DIRECTORY + "575_FireStealthTank_FireStealthTank.wav";
            case Annihilator -> SOUND_DIRECTORY + "3_FireAnnihilatorTank_FireAnnihilatorTank.wav";
        };
    }
}
