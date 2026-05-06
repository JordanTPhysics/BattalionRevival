package com.game.engine.ai;

import com.game.model.Player;
import com.game.model.map.GameMap;
import com.game.model.map.Tile;
import com.game.model.units.Unit;
import com.game.model.units.UnitAbilities;
import com.game.model.units.UnitType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * AI build scoring driven by an editable properties file.
 *
 * <p>Resource file: {@code /config/ai-unit-build-profiles.properties}. Optional local override:
 * {@code config/ai-unit-build-profiles.properties} in the working directory.</p>
 */
final class AiUnitBuildHeuristics {

    private static final String RESOURCE_PATH = "/config/ai-unit-build-profiles.properties";
    private static final Path OVERRIDE_PATH = Path.of("config", "ai-unit-build-profiles.properties");

    private final Map<UnitType, BuildProfile> profiles;

    private AiUnitBuildHeuristics(Map<UnitType, BuildProfile> profiles) {
        this.profiles = profiles;
    }

    static AiUnitBuildHeuristics load() {
        Map<UnitType, BuildProfile> profiles = new EnumMap<>(UnitType.class);
        for (UnitType type : UnitType.values()) {
            profiles.put(type, BuildProfile.empty());
        }

        Properties p = new Properties();
        try (InputStream in = AiUnitBuildHeuristics.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException ignored) {
        }

        if (Files.isRegularFile(OVERRIDE_PATH)) {
            try (InputStream in = Files.newInputStream(OVERRIDE_PATH)) {
                p.load(in);
            } catch (IOException ignored) {
            }
        }

        for (UnitType type : UnitType.values()) {
            String key = type.name();
            BuildProfile base = profiles.getOrDefault(type, BuildProfile.empty());
            profiles.put(
                type,
                new BuildProfile(
                    parseDouble(p.getProperty(key + ".bias"), base.bias),
                    parseTags(p.getProperty(key + ".goodVs")),
                    parseTags(p.getProperty(key + ".badVs")),
                    parseTags(p.getProperty(key + ".goodAt"))
                )
            );
        }
        return new AiUnitBuildHeuristics(profiles);
    }

    double scoreUnit(UnitType candidate, BuildContext ctx) {
        BuildProfile profile = profiles.getOrDefault(candidate, BuildProfile.empty());
        double score = profile.bias + baseCombatValue(candidate);

        for (BuildTag tag : profile.goodVs) {
            score += ctx.enemyPressure.getOrDefault(tag, 0.0) * 1.15;
        }
        for (BuildTag tag : profile.badVs) {
            score -= ctx.enemyPressure.getOrDefault(tag, 0.0) * 0.9;
        }
        for (BuildTag tag : profile.goodAt) {
            score += ctx.objectivePressure.getOrDefault(tag, 0.0) * 1.1;
        }
        for (BuildTag tag : candidateTags(candidate)) {
            double gap = Math.max(0.0, ctx.desiredCoverage.getOrDefault(tag, 0.0) - ctx.ownCoverage.getOrDefault(tag, 0.0));
            if (gap > 0.0) {
                score += gap * 1.35;
            }
        }
        return score;
    }

    static BuildContext buildContext(GameMap map, Player active) {
        Map<BuildTag, Double> enemyPressure = new EnumMap<>(BuildTag.class);
        Map<BuildTag, Double> objectivePressure = new EnumMap<>(BuildTag.class);
        Map<BuildTag, Double> ownCoverage = new EnumMap<>(BuildTag.class);
        Map<BuildTag, Double> desiredCoverage = new EnumMap<>(BuildTag.class);
        for (BuildTag tag : BuildTag.values()) {
            enemyPressure.put(tag, 0.0);
            objectivePressure.put(tag, 0.0);
            ownCoverage.put(tag, 0.0);
            desiredCoverage.put(tag, 0.0);
        }

        int capturableStructures = 0;
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile tile = map.getTile(x, y);
                if (tile == null) {
                    continue;
                }
                if (tile.getStructure() != null && tile.getStructure().getOwner() != active) {
                    capturableStructures++;
                }
                Unit unit = tile.getUnit();
                if (unit == null || !unit.isAlive() || unit.getOwner() == active) {
                    continue;
                }
                double weight = 1.0 + (unit.getUnitType().damage() / 60.0);
                add(enemyPressure, toTag(unit.getArmorType()), weight);
                add(enemyPressure, toTag(unit.getUnitType().movementKind()), weight);
                if (unit.getAttackRange() > 1) {
                    add(enemyPressure, BuildTag.RANGED, weight);
                }
                for (String ability : unit.getAbilities()) {
                    BuildTag tag = toTag(ability);
                    if (tag != null) {
                        add(enemyPressure, tag, weight);
                    }
                }
            }
        }
        for (Unit own : active.getUnits()) {
            if (!own.isAlive() || own.getAttackPower() <= 0) {
                continue;
            }
            double weight = 1.0 + (own.getUnitType().damage() / 80.0);
            add(ownCoverage, toTag(own.getArmorType()), weight);
            add(ownCoverage, toTag(own.getUnitType().movementKind()), weight);
            if (own.getAttackRange() > 1) {
                add(ownCoverage, BuildTag.RANGED, weight);
            }
            if (own.hasAbility(UnitAbilities.CONQUEROR)) {
                add(ownCoverage, BuildTag.CAPTURE, weight);
            }
            if (own.hasAbility(UnitAbilities.ANTI_AIR)) {
                add(ownCoverage, BuildTag.ANTI_AIR, weight);
            }
        }

        if (capturableStructures > 0) {
            add(objectivePressure, BuildTag.CAPTURE, capturableStructures * 0.7);
        }
        desiredCoverage.put(BuildTag.LAND, 2.0);
        desiredCoverage.put(BuildTag.MEDIUM, 1.8);
        desiredCoverage.put(BuildTag.HEAVY, 1.5);
        desiredCoverage.put(BuildTag.RANGED, 1.7);
        desiredCoverage.put(BuildTag.CAPTURE, capturableStructures > 0 ? 1.2 : 0.4);
        desiredCoverage.put(BuildTag.AIR, enemyPressure.get(BuildTag.AIR) > 0 ? 1.4 : 0.7);
        desiredCoverage.put(BuildTag.NAVAL, enemyPressure.get(BuildTag.NAVAL) > 0 ? 1.3 : 0.5);
        desiredCoverage.put(BuildTag.ANTI_AIR, 0.9 + enemyPressure.get(BuildTag.AIR) * 0.35);
        return new BuildContext(enemyPressure, objectivePressure, ownCoverage, desiredCoverage);
    }

    private static double baseCombatValue(UnitType t) {
        double score = 0.2;
        score += t.damage() / 25.0;
        score += t.startingHealth() / 100.0;
        score += t.movementSpeed() / 10.0;
        if (t.attackRange() > 1) {
            score += 0.1;
        }

        if (t.movementKind() == UnitType.MovementKind.FOOT) {
            score -= 0.2;
        }
        if (t.movementKind() == UnitType.MovementKind.WHEELED) {
            score += 0.1;
        }
        if (t.movementKind() == UnitType.MovementKind.TRACKED) {
            score += 0.2;
        }
        if (t.movementKind() == UnitType.MovementKind.NAVAL) {
            score += 0.3;
        }
        if (t.movementKind() == UnitType.MovementKind.AIR) {
            score += 0.3;
        }

        return score;
    }

    private static Set<BuildTag> candidateTags(UnitType t) {
        Set<BuildTag> tags = EnumSet.noneOf(BuildTag.class);
        if (t.damage() <= 0) {
            return tags;
        }
        tags.add(toTag(t.armorType()));
        tags.add(toTag(t.movementKind()));
        if (t.attackRange() > 1) {
            tags.add(BuildTag.RANGED);
        }
        for (String ability : t.getAbilities()) {
            if (UnitAbilities.CONQUEROR.equals(ability)) {
                tags.add(BuildTag.CAPTURE);
            }
            if (UnitAbilities.ANTI_AIR.equals(ability)) {
                tags.add(BuildTag.ANTI_AIR);
            }
        }
        return tags;
    }

    private static void add(Map<BuildTag, Double> map, BuildTag tag, double amount) {
        map.put(tag, map.getOrDefault(tag, 0.0) + amount);
    }

    private static BuildTag toTag(Unit.ArmorType armor) {
        return switch (armor) {
            case LIGHT -> BuildTag.LIGHT;
            case MEDIUM -> BuildTag.MEDIUM;
            case HEAVY -> BuildTag.HEAVY;
        };
    }

    private static BuildTag toTag(UnitType.MovementKind move) {
        return switch (move) {
            case AIR -> BuildTag.AIR;
            case NAVAL -> BuildTag.NAVAL;
            default -> BuildTag.LAND;
        };
    }

    private static BuildTag toTag(String ability) {
        if (UnitAbilities.ANTI_AIR.equals(ability)) {
            return BuildTag.AIR;
        }
        if (UnitAbilities.ANTI_SUBMARINE.equals(ability)) {
            return BuildTag.NAVAL;
        }
        if (UnitAbilities.CLOAKER.equals(ability)) {
            return BuildTag.CLOAKED;
        }
        if (UnitAbilities.CONQUEROR.equals(ability)) {
            return BuildTag.CAPTURE;
        }
        if (UnitAbilities.EXPLOSIVE.equals(ability)) {
            return BuildTag.HEAVY;
        }
        return null;
    }

    private static double parseDouble(String raw, double fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Set<BuildTag> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return EnumSet.noneOf(BuildTag.class);
        }
        Set<BuildTag> out = EnumSet.noneOf(BuildTag.class);
        String[] parts = raw.split(",");
        for (String part : parts) {
            String name = part.trim().toUpperCase(Locale.ROOT);
            if (name.isEmpty()) {
                continue;
            }
            try {
                out.add(BuildTag.valueOf(name));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }

    enum BuildTag {
        LIGHT,
        MEDIUM,
        HEAVY,
        LAND,
        NAVAL,
        AIR,
        RANGED,
        CLOAKED,
        CAPTURE,
        ANTI_AIR
    }

    record BuildContext(
        Map<BuildTag, Double> enemyPressure,
        Map<BuildTag, Double> objectivePressure,
        Map<BuildTag, Double> ownCoverage,
        Map<BuildTag, Double> desiredCoverage
    ) {
    }

    private record BuildProfile(double bias, Set<BuildTag> goodVs, Set<BuildTag> badVs, Set<BuildTag> goodAt) {
        static BuildProfile empty() {
            return new BuildProfile(
                0.0,
                EnumSet.noneOf(BuildTag.class),
                EnumSet.noneOf(BuildTag.class),
                EnumSet.noneOf(BuildTag.class)
            );
        }
    }
}

