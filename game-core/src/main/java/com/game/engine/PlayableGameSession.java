package com.game.engine;

import com.game.model.Player;
import com.game.model.Position;
import com.game.model.map.GameMap;
import com.game.model.map.Tile;
import com.game.model.structures.Structure;
import com.game.model.structures.StructureType;
import com.game.model.units.EngagementRules;
import com.game.model.units.FacingDirection;
import com.game.model.units.Unit;
import com.game.model.units.UnitAbilities;
import com.game.model.units.UnitType.MovementKind;
import com.game.model.units.UnitType;
import com.game.systems.FactorySpawn;
import com.game.systems.JammingRules;
import com.game.pathfinding.UnitMovementPaths;
import com.game.systems.CombatSystem;
import com.game.systems.CombatTerrain;
import com.game.systems.EconomySystem;
import com.game.systems.MovementSystem;
import com.game.network.protocol.MatchSnapshot;
import com.game.network.protocol.PlayerSnapshot;
import com.game.network.protocol.ProtocolVersions;
import com.game.network.protocol.UnitSnapshot;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Wires map data, players, turn order, economy, movement, combat, and structure capture for the interactive game UI.
 */
public class PlayableGameSession {
    /** Funds granted to a {@link UnitType#Warmachine} after spending an action to drill on an ore deposit. */
    public static final int WARMACHINE_DRILL_INCOME = 600;

    private final GameMap map;
    private final List<Player> players;
    private final TurnManager turnManager;
    private final EconomySystem economySystem = new EconomySystem();
    private final MovementSystem movementSystem = new MovementSystem();
    private final CombatSystem combatSystem = new CombatSystem();
    /**
     * Friends pulled off their home tile while the mover passes through; keyed by that tile so restoring when the
     * mover vacates a tile is not blocked by a newer displacement from another tile.
     */
    private final Map<Point, ArrayDeque<DisplacedAlly>> displacedAlliesDuringMoveAnim = new HashMap<>();
    /**
     * Factories that have already produced a unit this turn (active player only refreshes factories each turn).
     * Used so the HUD / map crosshair disappears after building.
     */
    private final Set<Point> factoriesThatProducedThisTurn = new HashSet<>();
    /** Players who have fielded at least one Kingpin-capable unit this match. */
    private final Set<Player> kingpinEligibleTeams = new HashSet<>();

    public PlayableGameSession(GameMap map) {
        this(map, false);
    }

    /**
     * @param hydrationBootstrap when {@code true}, skips spawning units from map sprites and skips opening economy /
     *     turn bootstrap — used before {@link #applyAuthoritativeSnapshot(MatchSnapshot)} completes wiring.
     */
    private PlayableGameSession(GameMap map, boolean hydrationBootstrap) {
        this.map = map;
        int teamCount = Math.max(GameMap.MIN_TEAMS, map.getTeamCount());
        this.players = new ArrayList<>(teamCount);
        String[] names = {"Red", "Blue", "Green", "Yellow"};
        for (int i = 0; i < teamCount; i++) {
            players.add(new Player(names[i]));
        }
        wireStructuresFromMap();
        if (!hydrationBootstrap) {
            wireUnitsFromMap();
        }
        this.turnManager = new TurnManager(players);
        if (!hydrationBootstrap) {
            applyRoundStartEconomyAndAbilities();
            startTurnForCurrentPlayer();
        }
    }

    /**
     * Reconstructs session state from a server-authored snapshot (multiplayer sync).
     */
    public static PlayableGameSession fromAuthoritativeSnapshot(MatchSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.schemaVersion() != ProtocolVersions.MATCH_SNAPSHOT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported snapshot schema version: " + snapshot.schemaVersion());
        }
        GameMap hydratedMap = SnapshotGameMapBuilder.build(snapshot);
        PlayableGameSession session = new PlayableGameSession(hydratedMap, true);
        session.applyAuthoritativeSnapshot(snapshot);
        return session;
    }

    public GameMap getMap() {
        return map;
    }

    public List<Player> getPlayers() {
        return List.copyOf(players);
    }

    public Player getActivePlayer() {
        return turnManager.getCurrentPlayer();
    }

    public int getActivePlayerIndex() {
        return turnManager.getCurrentPlayerIndex();
    }

    public int getRoundNumber() {
        return turnManager.getRoundNumber();
    }

    public void startTurnForCurrentPlayer() {
        Player active = turnManager.getCurrentPlayer();
        factoriesThatProducedThisTurn.clear();
        if (active.isEliminated()) {
            return;
        }
        active.resetTurnState();
    }

    private void applyRoundStartEconomyAndAbilities() {
        for (Player p : players) {
            if (p.isEliminated()) {
                continue;
            }
            applyTurnStartUnitAbilities(p);
            economySystem.applyTurnIncome(p, turnManager.getRoundNumber());
        }
    }

    private void applyTurnStartUnitAbilities(Player active) {
        for (Unit u : new ArrayList<>(active.getUnits())) {
            if (!u.isAlive()) {
                continue;
            }
            if (u.hasAbility(UnitAbilities.MAINTENANCE)) {
                u.heal(Math.max(1, (int) Math.round(u.getMaxHealth() * 0.10)));
            }
        }
        for (Unit source : new ArrayList<>(active.getUnits())) {
            if (!source.isAlive() || !source.hasAbility(UnitAbilities.RESUPPLY)) {
                continue;
            }
            int sx = source.getPosition().getX();
            int sy = source.getPosition().getY();
            for (Unit ally : new ArrayList<>(active.getUnits())) {
                if (!ally.isAlive() || ally == source) {
                    continue;
                }
                int ax = ally.getPosition().getX();
                int ay = ally.getPosition().getY();
                if (Math.abs(sx - ax) + Math.abs(sy - ay) <= 2) {
                    ally.heal(Math.max(1, (int) Math.round(ally.getMaxHealth() * 0.10)));
                }
            }
        }
    }

    /**
     * Whether this factory tile should display the idle "ready to act" crosshair for the current player —
     * owned factory that has not built yet this turn.
     */
    public boolean isFactoryEligibleForIdleCrosshair(int factoryX, int factoryY) {
        if (getActivePlayer().isEliminated()) {
            return false;
        }
        Tile t = map.getTile(factoryX, factoryY);
        if (t == null) {
            return false;
        }
        Structure s = t.getStructure();
        if (s == null || s.getType() != StructureType.Factory) {
            return false;
        }
        if (s.getOwner() != getActivePlayer()) {
            return false;
        }
        return !factoriesThatProducedThisTurn.contains(new Point(factoryX, factoryY));
    }

    /** True once at most one non-eliminated team remains (game cannot continue normally). */
    public boolean matchFinished() {
        int active = 0;
        for (Player p : players) {
            if (!p.isEliminated()) {
                active++;
            }
        }
        return active <= 1;
    }

    /** If exactly one team remains, they are the victor; empty when no winner (still in play, or stalemate). */
    public Optional<Player> getWinnerIfAny() {
        Player sole = null;
        for (Player p : players) {
            if (!p.isEliminated()) {
                if (sole != null) {
                    return Optional.empty();
                }
                sole = p;
            }
        }
        return sole != null ? Optional.of(sole) : Optional.empty();
    }

    public void surrender(Player player) {
        if (player == null || player.isEliminated()) {
            return;
        }
        eliminateDefeatedTeam(player, resolveStructureRecipient(null, player));
    }

    private void applyAuthoritativeSnapshot(MatchSnapshot snapshot) {
        clearAllRuntimeUnits();
        for (Player p : players) {
            p.getStructures().clear();
        }
        wireStructuresFromMap();
        for (PlayerSnapshot ps : snapshot.players()) {
            if (ps.seatIndex() < 0 || ps.seatIndex() >= players.size()) {
                continue;
            }
            Player p = players.get(ps.seatIndex());
            p.setMoney(ps.money());
            p.setEliminated(ps.eliminated());
        }
        for (UnitSnapshot us : snapshot.units()) {
            if (us.ownerSeatIndex() < 0 || us.ownerSeatIndex() >= players.size()) {
                continue;
            }
            Player owner = players.get(us.ownerSeatIndex());
            if (owner.isEliminated()) {
                continue;
            }
            UnitType ut = UnitType.valueOf(us.unitType());
            Unit u = new Unit(ut, owner, new Position(us.x(), us.y()));
            int loss = u.getMaxHealth() - Math.min(u.getMaxHealth(), Math.max(0, us.health()));
            if (loss > 0) {
                u.applyDamage(loss);
            }
            u.setHasMoved(us.hasMoved());
            u.setCloaked(us.cloaked());
            if (ut == UnitType.Warmachine && us.warmachineFunds() != null) {
                u.setWarmachineFunds(us.warmachineFunds());
            }
            owner.getUnits().add(u);
            Tile tile = map.getTile(us.x(), us.y());
            if (tile != null) {
                tile.setUnit(u);
                tile.setUnitSpriteId(ut.name());
                try {
                    tile.setUnitFacing(FacingDirection.valueOf(us.facing()));
                } catch (IllegalArgumentException ex) {
                    tile.setUnitFacing(FacingDirection.EAST);
                }
                syncUnitTeamMarkerOnTile(u);
            }
            if (u.hasAbility(UnitAbilities.KINGPIN)) {
                kingpinEligibleTeams.add(owner);
            }
        }
        turnManager.restoreClock(snapshot.roundNumber(), snapshot.activePlayerIndex());
        startTurnForCurrentPlayer();
    }

    private void clearAllRuntimeUnits() {
        displacedAlliesDuringMoveAnim.clear();
        factoriesThatProducedThisTurn.clear();
        kingpinEligibleTeams.clear();
        for (Player p : players) {
            for (Unit u : new ArrayList<>(p.getUnits())) {
                forciblyRemoveUnitFromMap(u);
            }
            p.getUnits().clear();
        }
    }

    /**
     * Resolves end-of-turn captures, advances turn order, then starts the next player's turn.
     * Economy and turn-start healing tick once per full round (after all surviving seats acted).
     */
    public void endTurn() {
        resolveStructureCapturesForActivePlayer();
        boolean startedNewRound = turnManager.nextTurn();
        if (startedNewRound) {
            applyRoundStartEconomyAndAbilities();
        }
        startTurnForCurrentPlayer();
    }

    public boolean isOwnedByActivePlayer(Unit unit) {
        return unit != null && unit.getOwner() == getActivePlayer();
    }

    public boolean canUnitAttack(Unit attacker) {
        if (attacker == null || attacker.getOwner().isEliminated() || attacker.hasMoved() || !attacker.isAlive()) {
            return false;
        }
        return attacker.getAttackPower() > 0 && attacker.getAttackType() != Unit.AttackType.NONE;
    }

    public boolean canUnitMove(Unit unit) {
        return unit != null
            && !unit.getOwner().isEliminated()
            && !unit.hasMoved()
            && unit.isAlive()
            && unit.getOwner() == getActivePlayer();
    }

    public boolean tryMoveUnit(Unit unit, int destX, int destY) {
        if (!canUnitMove(unit)) {
            return false;
        }
        Tile source = map.getTile(unit.getPosition().getX(), unit.getPosition().getY());
        if (source == null) {
            return false;
        }
        resetCaptureProgressIfLeavingStructure(source, unit);

        boolean moved = movementSystem.moveUnit(map, unit, new Position(destX, destY));
        if (!moved) {
            return false;
        }
        syncUnitTeamMarkerOnTile(unit);
        if (unit.hasAbility(UnitAbilities.CLOAKER)) {
            unit.setCloaked(true);
        }
        refreshCloakAndJammingAfterAnyUnitMoved();
        // Adjacent-enemy uncloak is deferred until action consume (see markUnitActionConsumed) so
        // move-then-melee can still apply cloaked damage. Instant move path spends the action here
        // via MovementSystem.moveUnit, so strip cloak now if applicable.
        uncloakCloakerIfOrthogonalEnemyAdjacent(unit);
        return true;
    }

    public boolean validateMovementPath(Unit unit, List<Point> pathIncludingStart) {
        return UnitMovementPaths.isValidMovementPath(map, unit, pathIncludingStart);
    }

    /**
     * Walks {@code unit} step-by-step along a pre-validated path, applying the same facing,
     * displacement, cloak/jamming bookkeeping as the animated player flow but without animation.
     * Used by the AI to perform a move synchronously while preserving the unit's per-turn action
     * (so a follow-up attack can still happen). Caller is responsible for marking the unit's
     * action as consumed via {@link #finishActionAfterMoveAlongPath(Unit, MoveAlongPathOutcome)} (or by chaining
     * {@link #tryAttack(Unit, Unit)} which marks it via {@link #completeAttackAfterCombat(Unit, Unit)}).
     *
     * <p>If a step would land on a cloaked enemy, that enemy is uncloaked and the move stops on
     * the previous tile (mirroring the runtime discovery rule).</p>
     *
     * @return {@link MoveAlongPathOutcome#accepted()} {@code false} if the path was invalid before any
     *     step; otherwise {@code true} with {@link MoveAlongPathOutcome#cloakedEnemyRevealed()} set when
     *     the mover discovered a cloaked opponent (already uncloaked in state).
     */
    public MoveAlongPathOutcome executeMoveAlongPath(Unit unit, List<Point> pathIncludingStart) {
        if (!canUnitMove(unit) || !validateMovementPath(unit, pathIncludingStart)) {
            return MoveAlongPathOutcome.rejected();
        }
        clearMoveAnimationDisplacementStack();
        resetCaptureBeforeMove(unit);
        int cloakBreakIndex = -1;
        Unit cloakInterrupted = null;
        for (int i = 1; i < pathIncludingStart.size(); i++) {
            Point from = pathIncludingStart.get(i - 1);
            Point to = pathIncludingStart.get(i);
            Unit cloakedAtNext = cloakedEnemyAtStep(unit, to.x, to.y);
            if (cloakedAtNext != null) {
                cloakedAtNext.setCloaked(false);
                rewindMovementSteps(unit, pathIncludingStart, i - 1);
                cloakInterrupted = cloakedAtNext;
                cloakBreakIndex = i;
                break;
            }
            applyMovementStepWithFacing(unit, from, to);
        }
        List<Point> clientAnimPath;
        if (cloakInterrupted != null) {
            if (cloakBreakIndex <= 0) {
                clientAnimPath = null;
            } else {
                clientAnimPath = new ArrayList<>(pathIncludingStart.subList(0, cloakBreakIndex));
            }
        } else {
            clientAnimPath = new ArrayList<>(pathIncludingStart);
        }
        if (clientAnimPath != null && clientAnimPath.size() < 2) {
            clientAnimPath = null;
        }
        unit.setPendingClientMovePathIncludingStart(clientAnimPath);
        completeAnimatedMove(unit);
        return MoveAlongPathOutcome.ok(cloakInterrupted);
    }

    /**
     * After {@link #executeMoveAlongPath(Unit, List)} succeeds, consumes the mover's action or — when
     * a cloaked enemy was revealed mid-path — performs a {@linkplain UnitAbilities#TRACKER} discovery
     * strike if allowed (same bonus as the animated discovery flow).
     */
    public void finishActionAfterMoveAlongPath(Unit unit, MoveAlongPathOutcome outcome) {
        if (!outcome.accepted()) {
            throw new IllegalArgumentException("finishActionAfterMoveAlongPath requires an accepted outcome");
        }
        Unit revealed = outcome.cloakedEnemyRevealed();
        if (revealed != null
            && unit.hasAbility(UnitAbilities.TRACKER)
            && canExecuteAttack(unit, revealed)) {
            if (!tryAttack(unit, revealed, true)) {
                markUnitActionConsumed(unit);
            }
        } else {
            markUnitActionConsumed(unit);
        }
    }

    public void resetCaptureBeforeMove(Unit unit) {
        Tile source = map.getTile(unit.getPosition().getX(), unit.getPosition().getY());
        if (source != null) {
            resetCaptureProgressIfLeavingStructure(source, unit);
        }
    }

    public void applyMovementStepWithFacing(Unit unit, Point from, Point to) {
        Tile fromTile = map.getTile(from.x, from.y);
        if (fromTile != null) {
            fromTile.setUnitFacing(UnitMovementPaths.facingForStep(from, to));
        }
        Tile destTile = map.getTile(to.x, to.y);
        if (destTile == null) {
            return;
        }
        Unit occupant = destTile.getUnit();
        if (occupant != null && occupant != unit && occupant.getOwner() == unit.getOwner()) {
            String spriteId = destTile.getUnitSpriteId();
            FacingDirection facing = destTile.getUnitFacing();
            Integer teamId = destTile.getUnitTeamId();
            movementSystem.clearUnitAndPresentationFromTile(destTile);
            Point homeKey = new Point(to.x, to.y);
            displacedAlliesDuringMoveAnim
                .computeIfAbsent(homeKey, k -> new ArrayDeque<>())
                .addLast(new DisplacedAlly(occupant, to.x, to.y, spriteId, facing, teamId));
        }
        movementSystem.relocateUnitWithSprite(map, unit, to.x, to.y);
        restoreDisplacedAlliesAfterMoverVacated(from);
    }

    /** Clears pass-through ally bookkeeping; call only when no allies are pending (after rewind or a completed move). */
    public void clearMoveAnimationDisplacementStack() {
        if (hasPendingDisplacedAllies()) {
            throw new IllegalStateException("Pass-through displacement pending; rewind or restore before clear");
        }
        displacedAlliesDuringMoveAnim.clear();
    }

    /**
     * Undoes forward motion along {@code pathIncludingStart} by stepping the mover backward from
     * {@code currentTileIndexInPath} down to index 0. Uses the same displacement rules as forward steps.
     */
    public void rewindMovementSteps(Unit mover, List<Point> pathIncludingStart, int currentTileIndexInPath) {
        if (mover == null || pathIncludingStart == null || currentTileIndexInPath < 1) {
            return;
        }
        for (int i = currentTileIndexInPath; i >= 1; i--) {
            applyMovementStepWithFacing(mover, pathIncludingStart.get(i), pathIncludingStart.get(i - 1));
        }
    }

    private void restoreDisplacedAlliesAfterMoverVacated(Point vacated) {
        Point key = new Point(vacated.x, vacated.y);
        ArrayDeque<DisplacedAlly> pending = displacedAlliesDuringMoveAnim.remove(key);
        if (pending == null) {
            return;
        }
        while (!pending.isEmpty()) {
            DisplacedAlly top = pending.pollFirst();
            movementSystem.placeUnitWithSprite(
                map,
                top.ally(),
                top.homeX(),
                top.homeY(),
                top.spriteId(),
                top.facing(),
                top.teamId()
            );
        }
    }

    private boolean hasPendingDisplacedAllies() {
        for (ArrayDeque<DisplacedAlly> q : displacedAlliesDuringMoveAnim.values()) {
            if (q != null && !q.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Post-move bookkeeping (team marker, displacement stack, cloak refresh) without consuming the
     * unit's per-turn action. The caller decides when to spend the action via
     * {@link #markUnitActionConsumed(Unit)}: immediately for move-only flows, or via
     * {@link #completeAttackAfterCombat(Unit, Unit)} when the move is followed by an attack as part
     * of the same compound action.
     */
    public void completeAnimatedMove(Unit unit) {
        syncUnitTeamMarkerOnTile(unit);
        if (hasPendingDisplacedAllies()) {
            throw new IllegalStateException("Pass-through allies still pending before completeAnimatedMove");
        }
        displacedAlliesDuringMoveAnim.clear();
        if (unit.hasAbility(UnitAbilities.CLOAKER)) {
            unit.setCloaked(true);
        }
        refreshCloakAndJammingAfterAnyUnitMoved();
    }

    /**
     * Marks the unit's per-turn action as consumed so it can neither move nor attack again until
     * the next turn. Idempotent and safe to call on a {@code null} unit.
     */
    public void markUnitActionConsumed(Unit unit) {
        if (unit != null) {
            unit.setHasMoved(true);
            uncloakCloakerIfOrthogonalEnemyAdjacent(unit);
        }
    }

    /**
     * After resolving combat (outgoing and possible counter): normally consumes the attacker's action.
     * An attacker with {@link UnitAbilities#SCAVENGER} who is still alive after exchanges and whose
     * primary target {@code defender} was destroyed keeps its action for the rest of the turn
     * (another move, attack, or move-attack).
     */
    public void markUnitActionConsumed(Unit attacker, Unit defender) {
        if (attacker != null
            && attacker.isAlive()
            && defender != null
            && !defender.isAlive()
            && attacker.hasAbility(UnitAbilities.SCAVENGER)) {
            return;
        }
        markUnitActionConsumed(attacker);
    }

    /**
     * Whether the active player can see this unit on the map (cloaked enemies are hidden unless a friendly
     * is adjacent).
     */
    public boolean canActivePlayerPerceiveUnit(Unit unit) {
        if (unit == null || !unit.isAlive()) {
            return false;
        }
        Player active = getActivePlayer();
        if (unit.getOwner() == active) {
            return true;
        }
        if (!unit.isCloaked()) {
            return true;
        }
        for (Unit friendly : active.getUnits()) {
            if (!friendly.isAlive()) {
                continue;
            }
            if (friendly.getPosition().manhattanDistance(unit.getPosition()) <= 1) {
                return true;
            }
        }
        return false;
    }

    private void refreshCloakAndJammingAfterAnyUnitMoved() {
        applyJammingUncloakToAllCloakedUnits();
    }

    /**
     * Cloaker design: orthogonal adjacency to an enemy reveals the unit when its turn action is
     * finished (move-only, or after attack resolution). Not applied immediately after movement so a
     * cloaked unit can move beside an enemy and melee with the cloaked damage bonus on that attack.
     */
    private void uncloakCloakerIfOrthogonalEnemyAdjacent(Unit unit) {
        if (unit == null || !unit.isAlive() || !unit.isCloaked() || !unit.hasAbility(UnitAbilities.CLOAKER)) {
            return;
        }
        if (hasEnemyOnOrthogonalAdjacentTile(unit)) {
            unit.setCloaked(false);
        }
    }

    private void applyJammingUncloakToAllCloakedUnits() {
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile t = map.getTile(x, y);
                Unit u = t == null ? null : t.getUnit();
                if (u == null || !u.isAlive() || !u.isCloaked()) {
                    continue;
                }
                if (JammingRules.isCloakedUnitAffectedByEnemyJammer(map, u)) {
                    u.setCloaked(false);
                }
            }
        }
    }

    private boolean hasEnemyOnOrthogonalAdjacentTile(Unit subject) {
        int sx = subject.getPosition().getX();
        int sy = subject.getPosition().getY();
        Player owner = subject.getOwner();
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            Tile nt = map.getTile(sx + d[0], sy + d[1]);
            if (nt == null) {
                continue;
            }
            Unit occ = nt.getUnit();
            if (occ != null && occ.isAlive() && occ.getOwner() != owner) {
                return true;
            }
        }
        return false;
    }

    private record DisplacedAlly(
        Unit ally,
        int homeX,
        int homeY,
        String spriteId,
        FacingDirection facing,
        Integer teamId
    ) {
    }

    public void syncUnitTeamMarkerOnTile(Unit unit) {
        Tile dest = map.getTile(unit.getPosition().getX(), unit.getPosition().getY());
        if (dest != null) {
            int teamIdx = players.indexOf(unit.getOwner()) + 1;
            if (teamIdx >= 1) {
                dest.setUnitTeamId(teamIdx);
            }
        }
    }

    public boolean playerOwnsControlStructure(Player player, StructureType control) {
        if (player == null) {
            return false;
        }
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile tile = map.getTile(x, y);
                if (tile == null) {
                    continue;
                }
                Structure s = tile.getStructure();
                if (s != null && s.getType() == control && s.getOwner() == player) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Factory must sit on a shore tile to produce naval units. */
    public boolean isCoastalFactoryTile(int factoryX, int factoryY) {
        Tile t = map.getTile(factoryX, factoryY);
        return t != null && t.getTerrainType().isCoastalShoreForSeaFactory();
    }

    public int factoryBuildPrice(UnitType type) {
        double priceMultiplier = 1.0;
        if(type.armorType() == Unit.ArmorType.HEAVY) {
            priceMultiplier *= 1.5;
        }
        if(type.armorType() == Unit.ArmorType.MEDIUM) {
            priceMultiplier *= 1.2;
        }

        if(type.attackType() == Unit.AttackType.HEAVY) {
            priceMultiplier *= 1.5;
        }

        if(type.movementKind() == MovementKind.AIR) {
            priceMultiplier *= type.movementSpeed() * type.movementSpeed() / 12;
        }

        if(type.movementKind() == MovementKind.NAVAL) {
            priceMultiplier *= 1.3;
        }


        return (int) Math.round(Math.max(50.0, (type.startingHealth() / 2.0 + type.damage() * 5.0) * priceMultiplier));
    }

    public boolean canPlayerBuildUnitAtFactory(Player player, int factoryX, int factoryY, UnitType type) {
        Tile ft = map.getTile(factoryX, factoryY);
        if (ft == null || ft.getStructure() == null || ft.getStructure().getType() != StructureType.Factory) {
            return false;
        }
        if (ft.getStructure().getOwner() != player) {
            return false;
        }
        // Production is blocked while any unit (friendly or hostile) sits on the factory tile.
        if (ft.getUnit() != null) {
            return false;
        }
        // Transports (Albatross / Leviathan) are not factory-buildable; they are produced by
        // converting an existing land unit in place via the unit context menu.
        if (type.isTransport()) {
            return false;
        }
        UnitType.FactoryBuildCategory cat = type.factoryBuildCategory();
        if (cat == UnitType.FactoryBuildCategory.AIR && !playerOwnsControlStructure(player, StructureType.AirControl)) {
            return false;
        }
        if (cat == UnitType.FactoryBuildCategory.SEA) {
            if (!playerOwnsControlStructure(player, StructureType.SeaControl)) {
                return false;
            }
            if (!isCoastalFactoryTile(factoryX, factoryY)) {
                return false;
            }
        }
        if (cat == UnitType.FactoryBuildCategory.LAND
            && !playerOwnsControlStructure(player, StructureType.GroundControl)) {
            return false;
        }
        return FactorySpawn.findSpawn(map, factoryX, factoryY, type, 4) != null;
    }

    /**
     * Units a {@link UnitType#Warmachine} may fabricate: same roster as factories minus transports,
     * immobile types, and further Warmachines.
     */
    public boolean isWarmachineProducibleUnitType(UnitType type) {
        return type != null && !type.isTransport() && type != UnitType.Warmachine && type.movementSpeed() > 0;
    }

    /** Kingpin production does not require Air / Sea / Ground control structures — only a valid adjacent spawn. */
    public boolean canWarmachineProduceUnit(Unit wm, UnitType type) {
        if (wm == null
            || wm.getUnitType() != UnitType.Warmachine
            || !wm.isAlive()
            || !isOwnedByActivePlayer(wm)
            || wm.hasMoved()
            || getActivePlayer().isEliminated()
            || !isWarmachineProducibleUnitType(type)) {
            return false;
        }
        int cost = factoryBuildPrice(type);
        if (wm.getWarmachineFunds() < cost) {
            return false;
        }
        int wx = wm.getPosition().getX();
        int wy = wm.getPosition().getY();
        return FactorySpawn.findAdjacentSpawn(map, wx, wy, type) != null;
    }

    public boolean tryWarmachineBuildUnit(Unit wm, UnitType type) {
        if (!canWarmachineProduceUnit(wm, type)) {
            return false;
        }
        int cost = factoryBuildPrice(type);
        if (!wm.trySpendWarmachineFunds(cost)) {
            return false;
        }
        int wx = wm.getPosition().getX();
        int wy = wm.getPosition().getY();
        Point spawn = FactorySpawn.findAdjacentSpawn(map, wx, wy, type);
        if (spawn == null) {
            wm.addWarmachineFunds(cost);
            return false;
        }
        Player p = getActivePlayer();
        FactorySpawn.placeNewUnit(map, type, p, spawn);
        Unit built = map.getTile(spawn.x, spawn.y).getUnit();
        if (built != null) {
            syncUnitTeamMarkerOnTile(built);
            if (built.hasAbility(UnitAbilities.KINGPIN)) {
                kingpinEligibleTeams.add(p);
            }
            markUnitActionConsumed(built);
        }
        markUnitActionConsumed(wm);
        return true;
    }

    public boolean canWarmachineDrill(Unit wm) {
        if (wm == null
            || wm.getUnitType() != UnitType.Warmachine
            || !wm.isAlive()
            || !isOwnedByActivePlayer(wm)
            || wm.hasMoved()
            || getActivePlayer().isEliminated()) {
            return false;
        }
        Tile t = map.getTile(wm.getPosition().getX(), wm.getPosition().getY());
        return t != null && t.isOreDeposit();
    }

    public boolean tryWarmachineDrill(Unit wm) {
        if (!canWarmachineDrill(wm)) {
            return false;
        }
        wm.addWarmachineFunds(WARMACHINE_DRILL_INCOME);
        markUnitActionConsumed(wm);
        return true;
    }

    /**
     * A foot unit owned by the active player and not yet acted-on this turn may morph into an
     * {@link UnitType#Albatross}. The conversion does not consume the unit's action, but per the
     * design rules a unit that has already moved this turn may not convert.
     */
    public boolean canConvertUnitToAlbatross(Unit unit) {
        if (!isEligibleForTransportConversion(unit)) {
            return false;
        }
        return unit.getUnitType().canConvertToAlbatross();
    }

    /**
     * A ground unit (foot / wheeled / tracked) standing on a coastal shore tile may morph into a
     * {@link UnitType#Leviathan}. Same per-turn rule as the Albatross conversion: not allowed
     * after the unit has moved.
     */
    public boolean canConvertUnitToLeviathan(Unit unit) {
        if (!isEligibleForTransportConversion(unit)) {
            return false;
        }
        if (!unit.getUnitType().canConvertToLeviathan()) {
            return false;
        }
        Tile tile = map.getTile(unit.getPosition().getX(), unit.getPosition().getY());
        return tile != null && tile.getTerrainType().isCoastalShoreForSeaFactory();
    }

    /**
     * A converted transport may revert to its original land-unit type, provided the unit hasn't
     * moved this turn and the tile under it is traversable for the original movement kind (a sky
     * Albatross can't drop a foot unit into the open sea).
     */
    public boolean canRevertTransport(Unit unit) {
        if (!isEligibleForTransportConversion(unit)) {
            return false;
        }
        UnitType origin = unit.getOriginalUnitType();
        if (origin == null) {
            return false;
        }
        Tile tile = map.getTile(unit.getPosition().getX(), unit.getPosition().getY());
        if (tile == null) {
            return false;
        }
        return tile.getTerrainType().canTraverseKind(origin.movementKind());
    }

    private boolean isEligibleForTransportConversion(Unit unit) {
        if (unit == null || !unit.isAlive() || !isOwnedByActivePlayer(unit)) {
            return false;
        }
        return !unit.hasMoved();
    }

    public boolean convertUnitToAlbatross(Unit unit) {
        if (!canConvertUnitToAlbatross(unit)) {
            return false;
        }
        applyTransportConversion(unit, UnitType.Albatross);
        return true;
    }

    public boolean convertUnitToLeviathan(Unit unit) {
        if (!canConvertUnitToLeviathan(unit)) {
            return false;
        }
        applyTransportConversion(unit, UnitType.Leviathan);
        return true;
    }

    public boolean revertTransport(Unit unit) {
        if (!canRevertTransport(unit)) {
            return false;
        }
        unit.revertToOriginalType();
        Tile tile = map.getTile(unit.getPosition().getX(), unit.getPosition().getY());
        if (tile != null) {
            tile.setUnitSpriteId(unit.getUnitType().name());
        }
        return true;
    }

    private void applyTransportConversion(Unit unit, UnitType target) {
        unit.convertToTransport(target);
        Tile tile = map.getTile(unit.getPosition().getX(), unit.getPosition().getY());
        if (tile != null) {
            tile.setUnitSpriteId(target.name());
        }
    }

    public boolean tryFactoryBuildUnit(int factoryX, int factoryY, UnitType type) {
        Player p = getActivePlayer();
        if (p.isEliminated() || !canPlayerBuildUnitAtFactory(p, factoryX, factoryY, type)) {
            return false;
        }
        int cost = factoryBuildPrice(type);
        if (!p.spendMoney(cost)) {
            return false;
        }
        Point spawn = FactorySpawn.findSpawn(map, factoryX, factoryY, type, 4);
        if (spawn == null) {
            p.addMoney(cost);
            return false;
        }
        FactorySpawn.placeNewUnit(map, type, p, spawn);
        Unit built = map.getTile(spawn.x, spawn.y).getUnit();
        if (built != null) {
            syncUnitTeamMarkerOnTile(built);
            if (built.hasAbility(UnitAbilities.KINGPIN)) {
                kingpinEligibleTeams.add(p);
            }
            // Freshly produced units cannot act on the turn they were built.
            markUnitActionConsumed(built);
        }
        factoriesThatProducedThisTurn.add(new Point(factoryX, factoryY));
        return true;
    }

    /**
     * Whether an attack from {@code attacker} to {@code defender} is allowed (no side effects).
     * Used by the UI for click validation and for staged combat presentation.
     */
    public boolean canExecuteAttack(Unit attacker, Unit defender) {
        if (getActivePlayer().isEliminated()) {
            return false;
        }
        if (!isOwnedByActivePlayer(attacker) || defender == null || defender.getOwner() == attacker.getOwner()) {
            return false;
        }
        if (!canUnitAttack(attacker)) {
            return false;
        }
        int dist = attacker.getPosition().manhattanDistance(defender.getPosition());
        int maxReach = CombatTerrain.effectiveMaxAttackRange(map, attacker);
        if (dist > maxReach || dist < attacker.getMinAttackRange()) {
            return false;
        }
        // Defense-in-depth: a unit the active player can't perceive (cloaked enemy without a
        // friendly spotter) can't be picked as an attack target.
        if (!canActivePlayerPerceiveUnit(defender)) {
            return false;
        }
        return EngagementRules.attackerCanTargetDefender(attacker, defender, map);
    }

    /** Sets map facing on {@code from}'s tile toward {@code toward} (for combat presentation). */
    public void orientUnitTowardTarget(Unit from, Unit toward) {
        if (from == null || toward == null) {
            return;
        }
        Tile t = map.getTile(from.getPosition().getX(), from.getPosition().getY());
        if (t == null) {
            return;
        }
        Point pFrom = new Point(from.getPosition().getX(), from.getPosition().getY());
        Point pTo = new Point(toward.getPosition().getX(), toward.getPosition().getY());
        t.setUnitFacing(UnitMovementPaths.facingForStep(pFrom, pTo));
    }

    public void applyOutgoingStrike(Unit attacker, Unit defender) {
        combatSystem.outgoingStrike(attacker, defender, map);
    }

    /**
     * Tracker movement-discovery surprise strike. Caller should already have uncloaked
     * {@code defender} so engagement rules and perception treat the unit as visible.
     */
    public void applyDiscoveryStrike(Unit attacker, Unit defender) {
        combatSystem.outgoingDiscoveryStrike(attacker, defender, map);
    }

    /**
     * If the next step would land on a cloaked enemy of {@code mover}, returns that enemy without
     * mutating any state. Returns {@code null} otherwise. The UI uses this to interrupt the glide
     * animation and trigger discovery (uncloak + maybe auto-attack).
     */
    public Unit cloakedEnemyAtStep(Unit mover, int x, int y) {
        if (mover == null) {
            return null;
        }
        Tile tile = map.getTile(x, y);
        if (tile == null) {
            return null;
        }
        Unit occ = tile.getUnit();
        if (occ == null || occ == mover || !occ.isAlive() || !occ.isCloaked()) {
            return null;
        }
        if (occ.getOwner() == mover.getOwner()) {
            return null;
        }
        return occ;
    }

    public boolean defenderEligibleForCounterattack(Unit defender, Unit attacker) {
        return combatSystem.defenderCanCounterattack(defender, attacker, map);
    }

    public void applyCounterStrike(Unit defender, Unit attacker) {
        combatSystem.counterStrike(defender, attacker, map);
    }

    public void completeAttackAfterCombat(Unit attacker, Unit defender) {
        markUnitActionConsumed(attacker, defender);
        Player killerIfDefenderDies = attacker != null ? attacker.getOwner() : null;
        Player killerIfAttackerDies = defender != null ? defender.getOwner() : null;
        cleanupDeadUnit(defender);
        considerEliminationAfterLastUnitDestroyed(defender, killerIfDefenderDies);
        cleanupDeadUnit(attacker);
        considerEliminationAfterLastUnitDestroyed(attacker, killerIfAttackerDies);
    }

    public boolean tryAttack(Unit attacker, Unit defender) {
        return tryAttack(attacker, defender, false);
    }

    /**
     * @param trackerDiscoveryFirstStrike when {@code true}, the first strike uses
     *     {@link CombatSystem#outgoingDiscoveryStrike} (Tracker surprise bonus). The defender must
     *     already be uncloaked; used after movement-interrupt discovery on headless/server paths.
     */
    public boolean tryAttack(Unit attacker, Unit defender, boolean trackerDiscoveryFirstStrike) {
        if (!canExecuteAttack(attacker, defender)) {
            return false;
        }
        orientUnitTowardTarget(attacker, defender);
        if (trackerDiscoveryFirstStrike) {
            combatSystem.outgoingDiscoveryStrike(attacker, defender, map);
        } else {
            combatSystem.outgoingStrike(attacker, defender, map);
        }
        if (combatSystem.defenderCanCounterattack(defender, attacker, map)) {
            orientUnitTowardTarget(defender, attacker);
            combatSystem.counterStrike(defender, attacker, map);
        }
        completeAttackAfterCombat(attacker, defender);
        return true;
    }

    private void cleanupDeadUnit(Unit unit) {
        if (unit == null || unit.isAlive()) {
            return;
        }
        unit.getOwner().getUnits().remove(unit);
        int x = unit.getPosition().getX();
        int y = unit.getPosition().getY();
        Tile t = map.getTile(x, y);
        if (t != null && t.getUnit() == unit) {
            t.setUnit(null);
            t.setUnitSpriteId(null);
            t.setUnitTeamId(null);
        }
    }

    private void wireStructuresFromMap() {
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile tile = map.getTile(x, y);
                if (tile == null) {
                    continue;
                }
                Structure st = tile.getStructure();
                if (st == null) {
                    continue;
                }
                Integer tid = tile.getStructureTeamId();
                if (tid != null) {
                    Player p = players.get(tid - 1);
                    st.setOwner(p);
                    if (!p.getStructures().contains(st)) {
                        p.getStructures().add(st);
                    }
                    updateStructureTeamIdOnTile(st);
                }
            }
        }
    }

    private void wireUnitsFromMap() {
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile tile = map.getTile(x, y);
                if (tile == null) {
                    continue;
                }
                if (tile.getUnitSpriteId() == null || tile.getUnit() != null) {
                    continue;
                }
                int team = tile.getUnitTeamId() != null ? tile.getUnitTeamId() : 1;
                Player owner = players.get(Math.min(team, players.size()) - 1);
                UnitType type = UnitSpriteMapper.inferUnitType(tile.getUnitSpriteId());
                Unit unit = new Unit(type, owner, new Position(x, y));
                tile.setUnit(unit);
                owner.getUnits().add(unit);
                if (unit.hasAbility(UnitAbilities.KINGPIN)) {
                    kingpinEligibleTeams.add(owner);
                }
            }
        }
    }

    private void resetCaptureProgressIfLeavingStructure(Tile sourceTile, Unit mover) {
        Structure st = sourceTile.getStructure();
        if (st == null || !st.canCapture(mover)) {
            return;
        }
        Player stOwner = st.getOwner();
        if (stOwner == null || stOwner != mover.getOwner()) {
            st.resetCaptureProgress();
        }
    }

    private void resolveStructureCapturesForActivePlayer() {
        Player active = getActivePlayer();
        for (Unit unit : new ArrayList<>(active.getUnits())) {
            if (!unit.isAlive()) {
                continue;
            }
            Position pos = unit.getPosition();
            Tile tile = map.getTile(pos.getX(), pos.getY());
            if (tile == null) {
                continue;
            }
            Structure st = tile.getStructure();
            if (st == null || !st.canCapture(unit)) {
                continue;
            }
            Player structOwner = st.getOwner();
            if (structOwner == unit.getOwner()) {
                continue;
            }
            Player before = st.getOwner();
            st.progressCapture(unit);
            Player after = st.getOwner();
            if (before != after) {
                syncStructureOwnershipLists(st, before, after);
                if (st.getType() == StructureType.Capital) {
                    eliminateTeamAfterCapitalCapture(before, after, st);
                }
            }
        }
    }

    private void syncStructureOwnershipLists(Structure st, Player before, Player after) {
        if (before != null) {
            before.getStructures().remove(st);
        }
        if (after != null && !after.getStructures().contains(st)) {
            after.getStructures().add(st);
        }
        updateStructureTeamIdOnTile(st);
    }

    private void considerEliminationAfterLastUnitDestroyed(Unit deadUnit, Player nominalKiller) {
        if (deadUnit == null || deadUnit.isAlive()) {
            return;
        }
        Player defeated = deadUnit.getOwner();
        if (defeated.isEliminated() || !shouldBeEliminatedFromUnitState(defeated)) {
            return;
        }
        eliminateDefeatedTeam(defeated, resolveStructureRecipient(nominalKiller, defeated));
    }

    private boolean shouldBeEliminatedFromUnitState(Player player) {
        boolean hasAliveUnit = false;
        boolean hasAliveKingpin = false;
        boolean hasAliveNonAimless = false;
        for (Unit u : player.getUnits()) {
            if (!u.isAlive()) {
                continue;
            }
            hasAliveUnit = true;
            if (u.hasAbility(UnitAbilities.KINGPIN)) {
                hasAliveKingpin = true;
            }
            if (!u.hasAbility(UnitAbilities.AIMLESS)) {
                hasAliveNonAimless = true;
            }
        }
        if (!hasAliveUnit) {
            return true;
        }
        if (kingpinEligibleTeams.contains(player) && !hasAliveKingpin) {
            return true;
        }
        return !hasAliveNonAimless;
    }

    /**
     * Prefers the team that dealt the knockout blow; if that team is unavailable (e.g. mutual wipe),
     * falls back to another surviving faction, otherwise structures may go neutral.
     */
    private Player resolveStructureRecipient(Player nominalKiller, Player defeated) {
        if (nominalKiller != null && !nominalKiller.isEliminated() && nominalKiller != defeated) {
            return nominalKiller;
        }
        for (Player p : players) {
            if (p != defeated && !p.isEliminated()) {
                return p;
            }
        }
        return null;
    }

    private void eliminateTeamAfterCapitalCapture(Player defeated, Player capturer, Structure capitalCaptured) {
        destroyStructureOnMap(capitalCaptured);
        eliminateDefeatedTeam(defeated, resolveStructureRecipient(capturer, defeated));
    }

    private void eliminateDefeatedTeam(Player defeated, Player recipient) {
        if (defeated == null || defeated.isEliminated()) {
            return;
        }
        forciblyClearAllUnits(defeated);
        for (Structure st : new ArrayList<>(defeated.getStructures())) {
            if (st.getType() == StructureType.Capital) {
                destroyStructureOnMap(st);
            } else if (recipient != null) {
                transferStructureToRecipient(st, recipient);
            } else {
                neutralizeStructure(st);
            }
        }
        defeated.setEliminated(true);
    }

    private void transferStructureToRecipient(Structure st, Player recipient) {
        Player from = st.getOwner();
        if (recipient == null || from == recipient) {
            return;
        }
        st.setOwner(recipient);
        syncStructureOwnershipLists(st, from, recipient);
    }

    private void neutralizeStructure(Structure st) {
        Player from = st.getOwner();
        if (from != null) {
            from.getStructures().remove(st);
        }
        st.setOwner(null);
        updateStructureTeamIdOnTile(st);
    }

    private void forciblyClearAllUnits(Player owner) {
        for (Unit u : new ArrayList<>(owner.getUnits())) {
            forciblyRemoveUnitFromMap(u);
        }
    }

    private void forciblyRemoveUnitFromMap(Unit unit) {
        if (unit == null) {
            return;
        }
        unit.getOwner().getUnits().remove(unit);
        int x = unit.getPosition().getX();
        int y = unit.getPosition().getY();
        Tile t = map.getTile(x, y);
        if (t != null && t.getUnit() == unit) {
            t.setUnit(null);
            t.setUnitSpriteId(null);
            t.setUnitTeamId(null);
        }
    }

    private void destroyStructureOnMap(Structure st) {
        if (st == null) {
            return;
        }
        Player owner = st.getOwner();
        if (owner != null) {
            owner.getStructures().remove(st);
        }
        Tile tile = findTileContainingStructure(st);
        if (tile != null) {
            tile.setStructure(null);
            tile.setStructureTeamId(null);
        }
    }

    private Tile findTileContainingStructure(Structure st) {
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile t = map.getTile(x, y);
                if (t != null && t.getStructure() == st) {
                    return t;
                }
            }
        }
        return null;
    }

    /** Keeps {@link Tile#getStructureTeamId()} aligned with {@link Structure#getOwner()} for UI and saves. */
    private void updateStructureTeamIdOnTile(Structure st) {
        Tile tile = findTileContainingStructure(st);
        if (tile == null) {
            return;
        }
        Player owner = st.getOwner();
        if (owner == null) {
            tile.setStructureTeamId(null);
            return;
        }
        int ix = players.indexOf(owner);
        tile.setStructureTeamId(ix >= 0 ? ix + 1 : null);
    }

    /**
     * Result of {@link #executeMoveAlongPath(Unit, List)}.
     *
     * @param accepted {@code false} only when the path was rejected before any movement was applied.
     * @param cloakedEnemyRevealed when non-null, the mover stopped on the previous tile because that enemy
     *     was hidden there; the unit is already uncloaked in game state.
     */
    public record MoveAlongPathOutcome(boolean accepted, Unit cloakedEnemyRevealed) {
        public static MoveAlongPathOutcome rejected() {
            return new MoveAlongPathOutcome(false, null);
        }

        public static MoveAlongPathOutcome ok(Unit cloakedEnemyRevealed) {
            return new MoveAlongPathOutcome(true, cloakedEnemyRevealed);
        }
    }
}
