package com.game.engine.ai;

import com.game.model.units.Unit;
import com.game.model.units.UnitType;

import java.awt.Point;
import java.util.List;

/**
 * Discrete actions the AI brain can request from its turn executor. Each action is one visible
 * step (move / attack / build / pass) so the executor can pace them with a small timer for
 * human readability.
 */
public sealed interface AiAction
    permits AiAction.EndTurn,
        AiAction.PassUnit,
        AiAction.MoveUnit,
        AiAction.MoveAndAttack,
        AiAction.Attack,
        AiAction.BuildUnit {

    /** Sentinel: no more useful work this turn — caller advances the session. */
    record EndTurn() implements AiAction {
    }

    /** Mark a unit's per-turn action as consumed without doing anything visible. */
    record PassUnit(Unit unit) implements AiAction {
    }

    /**
     * Move along {@code path} (path[0] is the unit's current cell) without an attack. The unit's
     * action is consumed by the executor when the move finishes.
     */
    record MoveUnit(Unit unit, List<Point> path) implements AiAction {
    }

    /**
     * Compound action: walk {@code path} then attack {@code target} at the destination. Only
     * legal for melee (range 1) units, matching the existing engagement rule for
     * move-then-attack.
     */
    record MoveAndAttack(Unit unit, List<Point> path, Unit target) implements AiAction {
    }

    /** Attack {@code target} from the unit's current cell (no movement). */
    record Attack(Unit attacker, Unit target) implements AiAction {
    }

    /** Spend funds at one of the player's idle factories to produce a fresh unit. */
    record BuildUnit(int factoryX, int factoryY, UnitType type) implements AiAction {
    }
}
