package com.game.engine.ai;

import com.game.engine.PlayableGameSession;

/**
 * Applies one {@link AiAction} to a {@link PlayableGameSession} with no presentation delay — shared
 * by {@link AiTurnExecutor}'s headless path and {@link HeadlessAiTurnRunner}.
 */
public final class AiActionApplicator {

    private AiActionApplicator() {
    }

    public static void apply(PlayableGameSession session, AiAction action) {
        if (action instanceof AiAction.PassUnit pu) {
            session.markUnitActionConsumed(pu.unit());
            return;
        }
        if (action instanceof AiAction.MoveUnit mu) {
            PlayableGameSession.MoveAlongPathOutcome o = session.executeMoveAlongPath(mu.unit(), mu.path());
            if (!o.accepted()) {
                session.markUnitActionConsumed(mu.unit());
                return;
            }
            session.finishActionAfterMoveAlongPath(mu.unit(), o);
            return;
        }
        if (action instanceof AiAction.MoveAndAttack ma) {
            PlayableGameSession.MoveAlongPathOutcome o = session.executeMoveAlongPath(ma.unit(), ma.path());
            if (!o.accepted()) {
                session.markUnitActionConsumed(ma.unit());
                return;
            }
            if (o.cloakedEnemyRevealed() != null) {
                session.finishActionAfterMoveAlongPath(ma.unit(), o);
                return;
            }
            if (session.canExecuteAttack(ma.unit(), ma.target())) {
                session.tryAttack(ma.unit(), ma.target());
            } else {
                session.markUnitActionConsumed(ma.unit());
            }
            return;
        }
        if (action instanceof AiAction.Attack a) {
            if (!session.tryAttack(a.attacker(), a.target())) {
                session.markUnitActionConsumed(a.attacker());
            }
            return;
        }
        if (action instanceof AiAction.BuildUnit b) {
            session.tryFactoryBuildUnit(b.factoryX(), b.factoryY(), b.type());
        }
    }
}
