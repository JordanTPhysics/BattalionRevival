package com.game.engine.ai;

import com.game.engine.PlayableGameSession;
import com.game.model.Player;
import com.game.model.map.GameMap;
import com.game.model.map.Tile;
import com.game.model.structures.Structure;
import com.game.model.structures.StructureType;
import com.game.model.units.EngagementRules;
import com.game.model.units.Unit;
import com.game.model.units.UnitAbilities;
import com.game.model.units.UnitType;
import com.game.pathfinding.UnitMovementPaths;
import com.game.systems.CombatTerrain;
import com.game.systems.MovementReach;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Decision logic for AI-controlled players. Stateless across turns: every {@link #nextAction()}
 * call re-derives the best step from the live session state, so the same engine handles
 * arbitrary interleaving with the human-controlled side.
 *
 * <p>Per-call priority:</p>
 * <ol>
 *     <li>For each unit that has not yet acted, pick a useful action — stationary attack on the
 *         best-scored enemy in range; move-then-attack a melee target; foot-unit capture of a
 *         nearby enemy/neutral structure; advance toward the closest enemy. Otherwise pass the
 *         unit to release its action slot.</li>
 *     <li>Spend funds at owned factories on an affordable, eligible unit chosen by
 *         threat-aware weighted scoring (not purely by highest cost).</li>
 *     <li>Otherwise return {@link AiAction.EndTurn}.</li>
 * </ol>
 */
public class AiEngine {

    private static final int[][] ORTHOGONAL_DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final PlayableGameSession session;
    private final Set<Integer> aiPlayerIndices;
    private final Random rng = new Random();
    private final AiUnitBuildHeuristics buildHeuristics = AiUnitBuildHeuristics.load();

    public AiEngine(PlayableGameSession session, Set<Integer> aiPlayerIndices) {
        this.session = session;
        this.aiPlayerIndices = Set.copyOf(aiPlayerIndices);
    }

    /** Whether the player whose turn it currently is should be driven by this engine. */
    public boolean controlsActivePlayer() {
        if (!aiPlayerIndices.contains(session.getActivePlayerIndex())) {
            return false;
        }
        return !session.getActivePlayer().isEliminated();
    }

    public boolean controlsPlayerIndex(int playerIndex) {
        return aiPlayerIndices.contains(playerIndex);
    }

    /**
     * Returns the next action the AI wants the executor to apply. Idempotent only after the
     * executor has applied the previously returned action — repeated calls without applying may
     * loop on the same unit.
     */
    public AiAction nextAction() {
        Player active = session.getActivePlayer();
        if (active.isEliminated()) {
            return new AiAction.EndTurn();
        }
        for (Unit unit : new ArrayList<>(active.getUnits())) {
            if (!unit.isAlive() || unit.hasMoved()) {
                continue;
            }
            AiAction unitAction = decideForUnit(unit);
            if (unitAction != null) {
                return unitAction;
            }
        }
        AiAction build = decideBuild();
        if (build != null) {
            return build;
        }
        return new AiAction.EndTurn();
    }

    // ---- Unit action selection ----------------------------------------------

    private AiAction decideForUnit(Unit unit) {
        boolean canMove = session.canUnitMove(unit);
        boolean canAttack = session.canUnitAttack(unit);

        if (canAttack) {
            Unit stationaryTarget = bestStationaryTarget(unit);
            if (stationaryTarget != null) {
                return new AiAction.Attack(unit, stationaryTarget);
            }
        }

        if (canMove && canAttack && unit.getAttackRange() == 1) {
            MoveAttackPlan plan = bestMeleeMoveAttack(unit);
            if (plan != null) {
                return new AiAction.MoveAndAttack(unit, plan.path(), plan.target());
            }
        }

        if (canMove && unit.hasAbility(UnitAbilities.CONQUEROR)) {
            List<Point> capturePath = pathTowardCapture(unit);
            if (capturePath != null) {
                return new AiAction.MoveUnit(unit, capturePath);
            }
        }

        if (canMove) {
            List<Point> advancePath = pathTowardClosestEnemy(unit);
            if (advancePath != null) {
                return new AiAction.MoveUnit(unit, advancePath);
            }
        }

        return new AiAction.PassUnit(unit);
    }

    /** Best in-range enemy to attack from the unit's current cell, or {@code null} if none. */
    private Unit bestStationaryTarget(Unit attacker) {
        GameMap map = session.getMap();
        Unit best = null;
        double bestScore = 0.0;
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile tile = map.getTile(x, y);
                if (tile == null) {
                    continue;
                }
                Unit other = tile.getUnit();
                if (other == null || !other.isAlive() || other.getOwner() == attacker.getOwner()) {
                    continue;
                }
                if (!session.canExecuteAttack(attacker, other)) {
                    continue;
                }
                double score = scoreEngagement(attacker, other);
                if (score > bestScore) {
                    bestScore = score;
                    best = other;
                }
            }
        }
        return best;
    }

    /**
     * Looks for the highest-scored {@code (destination, target)} where the unit can step onto a
     * tile orthogonally adjacent to {@code target} and finish the move within its budget. Only
     * legal for melee units; ranged move-then-attack is not allowed by the engagement rules.
     */
    private MoveAttackPlan bestMeleeMoveAttack(Unit attacker) {
        GameMap map = session.getMap();
        Set<Point> reachable = MovementReach.reachableDestinations(map, attacker);
        if (reachable.isEmpty()) {
            return null;
        }
        MoveAttackPlan best = null;
        double bestScore = 0.0;
        for (Point dest : reachable) {
            for (int[] d : ORTHOGONAL_DIRS) {
                int ex = dest.x + d[0];
                int ey = dest.y + d[1];
                Tile enemyTile = map.getTile(ex, ey);
                if (enemyTile == null) {
                    continue;
                }
                Unit enemy = enemyTile.getUnit();
                if (enemy == null || !enemy.isAlive() || enemy.getOwner() == attacker.getOwner()) {
                    continue;
                }
                if (!EngagementRules.attackerCanTargetDefender(attacker, enemy, map)) {
                    continue;
                }
                List<Point> path = UnitMovementPaths.shortestLegalPath(map, attacker, dest.x, dest.y);
                if (path.size() < 2) {
                    continue;
                }
                // Score from the destination's terrain perspective: defender on a defense-heavy
                // tile is harder to damage, but the heuristic stays simple — prefer kills, then
                // raw outgoing - counter, lightly biased against long detours.
                double score = scoreEngagement(attacker, enemy) - 0.05 * (path.size() - 1);
                if (score > bestScore) {
                    bestScore = score;
                    best = new MoveAttackPlan(path, enemy);
                }
            }
        }
        return best;
    }

    /**
     * Heuristic: outgoing damage minus expected counter damage; bonus for confirmed kills. Uses
     * the same armor/ability multipliers the live combat system applies, but recomputed locally
     * so it remains a pure planning function.
     */
    private double scoreEngagement(Unit attacker, Unit defender) {
        if (!EngagementRules.attackerCanTargetDefender(attacker, defender, session.getMap())) {
            return Double.NEGATIVE_INFINITY;
        }
        int outgoing = approximateDamage(attacker, defender, true, attacker.getHealth());
        int counter = 0;
        int dist = attacker.getPosition().manhattanDistance(defender.getPosition());
        int defenderHpAfter = Math.max(0, defender.getHealth() - outgoing);
        if (defenderHpAfter > 0
            && dist >= defender.getMinAttackRange()
            && dist <= CombatTerrain.effectiveMaxAttackRange(session.getMap(), defender)
            && EngagementRules.attackerCanTargetDefender(defender, attacker, session.getMap())) {
            counter = approximateDamage(defender, attacker, false, defenderHpAfter);
        }
        boolean killShot = outgoing >= defender.getHealth();
        double killBonus = killShot ? 30.0 : 0.0;
        return (outgoing - counter) + killBonus;
    }

    private int approximateDamage(Unit attacker, Unit defender, boolean asInitiator, int attackerCurrentHp) {
        double mult = armorMultiplier(attacker.getAttackType(), defender.getArmorType());
        double dmg = attacker.getAttackPower() * mult;
        if (asInitiator && attacker.hasAbility(UnitAbilities.BLITZKRIEG)) {
            dmg *= 1.2;
        }
        if (defender.getArmorType() == Unit.ArmorType.LIGHT && attacker.hasAbility(UnitAbilities.RAPIDFIRE)) {
            dmg *= 2.0;
        }
        if (attacker.hasAbility(UnitAbilities.EXPLOSIVE)) {
            Tile dt = session.getMap().getTile(defender.getPosition().getX(), defender.getPosition().getY());
            boolean onStructure = dt != null && dt.getStructure() != null;
            if (defender.getArmorType() == Unit.ArmorType.HEAVY || onStructure) {
                dmg *= 2.0;
            }
        }
        if (attacker.hasAbility(UnitAbilities.CLOAKER) && attacker.isCloaked()) {
            dmg *= 2.0;
        }
        int max = Math.max(1, attacker.getMaxHealth());
        double ratio = Math.min(1.0, Math.max(0.0, attackerCurrentHp / (double) max));
        dmg *= ratio;
        return (int) Math.round(dmg);
    }

    private static double armorMultiplier(Unit.AttackType atk, Unit.ArmorType armor) {
        return switch (atk) {
            case NONE -> 0.0;
            case LIGHT -> switch (armor) {
                case LIGHT -> 1.2;
                case MEDIUM -> 1.0;
                case HEAVY -> 0.7;
            };
            case MEDIUM -> 1.0;
            case HEAVY -> switch (armor) {
                case LIGHT -> 0.7;
                case MEDIUM -> 1.0;
                case HEAVY -> 1.2;
            };
        };
    }

    // ---- Movement objectives ------------------------------------------------

    private List<Point> pathTowardCapture(Unit unit) {
        Point goal = nearestCapturableStructure(unit);
        if (goal == null) {
            return null;
        }
        return movePathTowardGoal(unit, goal);
    }

    private List<Point> pathTowardClosestEnemy(Unit unit) {
        Point goal = nearestEnemyUnit(unit);
        if (goal == null) {
            return null;
        }
        return movePathTowardGoal(unit, goal);
    }

    /**
     * Picks the reachable destination that gets closest (Manhattan) to {@code goal} and resolves
     * a legal path to it. Returns {@code null} if the unit cannot make progress this turn (the
     * caller decides whether to pass the unit or try a different objective).
     */
    private List<Point> movePathTowardGoal(Unit unit, Point goal) {
        GameMap map = session.getMap();
        Set<Point> reachable = MovementReach.reachableDestinations(map, unit);
        if (reachable.isEmpty()) {
            return null;
        }
        int selfX = unit.getPosition().getX();
        int selfY = unit.getPosition().getY();
        int currentDist = manhattan(selfX, selfY, goal.x, goal.y);
        Point best = null;
        int bestDist = currentDist;
        for (Point candidate : reachable) {
            int d = manhattan(candidate.x, candidate.y, goal.x, goal.y);
            if (d < bestDist) {
                bestDist = d;
                best = candidate;
            }
        }
        if (best == null) {
            return null;
        }
        List<Point> path = UnitMovementPaths.shortestLegalPath(map, unit, best.x, best.y);
        if (path.size() < 2) {
            return null;
        }
        return path;
    }

    private Point nearestCapturableStructure(Unit unit) {
        GameMap map = session.getMap();
        Player owner = unit.getOwner();
        int sx = unit.getPosition().getX();
        int sy = unit.getPosition().getY();
        Point best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile tile = map.getTile(x, y);
                if (tile == null) {
                    continue;
                }
                Structure st = tile.getStructure();
                if (st == null || !st.canCapture(unit) || st.getOwner() == owner) {
                    continue;
                }
                int d = manhattan(sx, sy, x, y);
                if (d < bestDist) {
                    bestDist = d;
                    best = new Point(x, y);
                }
            }
        }
        return best;
    }

    private Point nearestEnemyUnit(Unit unit) {
        GameMap map = session.getMap();
        Player owner = unit.getOwner();
        int sx = unit.getPosition().getX();
        int sy = unit.getPosition().getY();
        Point best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile tile = map.getTile(x, y);
                if (tile == null) {
                    continue;
                }
                Unit other = tile.getUnit();
                if (other == null || !other.isAlive() || other.getOwner() == owner) {
                    continue;
                }
                int d = manhattan(sx, sy, x, y);
                if (d < bestDist) {
                    bestDist = d;
                    best = new Point(x, y);
                }
            }
        }
        return best;
    }

    private static int manhattan(int ax, int ay, int bx, int by) {
        return Math.abs(ax - bx) + Math.abs(ay - by);
    }

    // ---- Building -----------------------------------------------------------

    /** Build at one of the active player's idle factories or {@code null} if none is useful. */
    private AiAction decideBuild() {
        Player active = session.getActivePlayer();
        for (Structure st : new ArrayList<>(active.getStructures())) {
            if (st.getType() != StructureType.Factory) {
                continue;
            }
            Point factoryPos = findStructurePosition(st);
            if (factoryPos == null) {
                continue;
            }
            UnitType chosen = pickBestAffordableUnit(active, factoryPos);
            if (chosen != null) {
                return new AiAction.BuildUnit(factoryPos.x, factoryPos.y, chosen);
            }
        }
        return null;
    }

    private Point findStructurePosition(Structure structure) {
        GameMap map = session.getMap();
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile tile = map.getTile(x, y);
                if (tile != null && tile.getStructure() == structure) {
                    return new Point(x, y);
                }
            }
        }
        return null;
    }

    /** Picks a threat-aware affordable unit with weighted variety. */
    private UnitType pickBestAffordableUnit(Player player, Point factoryPos) {
        AiUnitBuildHeuristics.BuildContext ctx = AiUnitBuildHeuristics.buildContext(session.getMap(), player);
        List<UnitType> buildable = new ArrayList<>();
        for (UnitType type : UnitType.values()) {
            if (type.damage() <= 0) {
                continue;
            }
            int price = session.factoryBuildPrice(type);
            if (price > player.getMoney()) {
                continue;
            }
            if (!session.canPlayerBuildUnitAtFactory(player, factoryPos.x, factoryPos.y, type)) {
                continue;
            }
            buildable.add(type);
        }
        if (buildable.isEmpty()) {
            return null;
        }

        ArmyKindStats kindStats = computeArmyKindStats(player);
        boolean hasCapCompliantOption = false;
        for (UnitType type : buildable) {
            if (!wouldBreakMovementKindCap(type, kindStats)) {
                hasCapCompliantOption = true;
                break;
            }
        }

        List<ScoredUnit> options = new ArrayList<>();
        for (UnitType type : buildable) {
            if (hasCapCompliantOption && wouldBreakMovementKindCap(type, kindStats)) {
                continue;
            }
            int price = session.factoryBuildPrice(type);
            double priceComponent = Math.min(1.3, price / 120.0);
            double score = buildHeuristics.scoreUnit(type, ctx)
                + priceComponent
                + movementKindDiversityScore(type, kindStats);
            options.add(new ScoredUnit(type, Math.max(0.05, score)));
        }
        if (options.isEmpty()) {
            return null;
        }
        options.sort(Comparator.comparingDouble(ScoredUnit::score).reversed());
        double bestScore = options.get(0).score();
        double cutoff = bestScore * 0.82;
        List<ScoredUnit> shortlist = new ArrayList<>();
        for (ScoredUnit option : options) {
            if (option.score() >= cutoff) {
                shortlist.add(option);
            }
        }
        if (shortlist.isEmpty()) {
            shortlist.add(options.get(0));
        }
        double total = 0.0;
        for (ScoredUnit option : shortlist) {
            total += option.score();
        }
        double pick = rng.nextDouble() * total;
        double running = 0.0;
        for (ScoredUnit option : shortlist) {
            running += option.score();
            if (pick <= running) {
                return option.type();
            }
        }
        return shortlist.get(shortlist.size() - 1).type();
    }

    private ArmyKindStats computeArmyKindStats(Player player) {
        Map<UnitType.MovementKind, Integer> byKind = new EnumMap<>(UnitType.MovementKind.class);
        int totalCombat = 0;
        for (Unit u : player.getUnits()) {
            if (!u.isAlive() || u.getAttackPower() <= 0) {
                continue;
            }
            totalCombat++;
            UnitType.MovementKind kind = u.getUnitType().movementKind();
            byKind.put(kind, byKind.getOrDefault(kind, 0) + 1);
        }
        return new ArmyKindStats(byKind, totalCombat);
    }

    private boolean wouldBreakMovementKindCap(UnitType candidate, ArmyKindStats stats) {
        int totalAfter = stats.totalCombatUnits() + 1;
        int sameKindAfter = stats.byKind().getOrDefault(candidate.movementKind(), 0) + 1;
        return sameKindAfter > (totalAfter / 2.0);
    }

    private double movementKindDiversityScore(UnitType candidate, ArmyKindStats stats) {
        int total = Math.max(1, stats.totalCombatUnits());
        int kindCount = stats.byKind().getOrDefault(candidate.movementKind(), 0);
        double share = kindCount / (double) total;
        if (kindCount == 0) {
            return 2.0;
        }
        if (share < 0.2) {
            return 0.9;
        }
        if (share > 0.45) {
            return -1.4;
        }
        return 0.0;
    }

    private record ScoredUnit(UnitType type, double score) {
    }

    private record ArmyKindStats(Map<UnitType.MovementKind, Integer> byKind, int totalCombatUnits) {
    }

    private record MoveAttackPlan(List<Point> path, Unit target) {
    }
}
