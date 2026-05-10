package com.game.engine.ai;

import com.game.engine.PlayableGameSession;

import java.util.Set;

/**
 * Runs {@link AiEngine} steps synchronously for server-side computer opponents (no Swing timer).
 * After each full AI turn, calls {@link PlayableGameSession#endTurn()} so capture resolution and the
 * turn clock match human play.
 */
public final class HeadlessAiTurnRunner {

    /** Same defensive cap as {@link AiTurnExecutor}. */
    private static final int MAX_STEPS_PER_TURN = 512;

    private HeadlessAiTurnRunner() {
    }

    /**
     * While the active seat is AI-controlled, plays full turns until the next seat is human, the
     * match ends, or no AI seat is active.
     */
    public static void runUntilNextHumanOrDone(PlayableGameSession session, Set<Integer> aiSeats) {
        if (aiSeats == null || aiSeats.isEmpty()) {
            return;
        }
        while (!session.matchFinished()) {
            AiEngine probe = new AiEngine(session, aiSeats);
            if (!probe.controlsActivePlayer()) {
                return;
            }
            runOneFullAiTurn(session, aiSeats);
        }
    }

    private static void runOneFullAiTurn(PlayableGameSession session, Set<Integer> aiSeats) {
        AiEngine engine = new AiEngine(session, aiSeats);
        if (!engine.controlsActivePlayer()) {
            return;
        }
        for (int step = 0; step < MAX_STEPS_PER_TURN; step++) {
            if (session.matchFinished()) {
                return;
            }
            AiAction action = engine.nextAction();
            if (action instanceof AiAction.EndTurn) {
                session.endTurn();
                return;
            }
            AiActionApplicator.apply(session, action);
        }
        session.endTurn();
    }
}
