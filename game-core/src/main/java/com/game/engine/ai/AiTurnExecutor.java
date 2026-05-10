package com.game.engine.ai;

import com.game.engine.PlayableGameSession;

import javax.swing.Timer;

/**
 * Drives an AI player's turn on the Swing EDT, stepping through {@link AiAction}s with a small
 * delay between each so the human can read what's happening. Each action is applied through
 * {@link AiStepRunner} so moves and attacks use the same visuals and audio as human play; the
 * runner invokes {@code whenComplete} when the step is fully presented. The executor stops when
 * the engine returns {@link AiAction.EndTurn}; the caller's {@code onTurnFinished} callback is
 * then responsible for actually advancing the session turn, so the same end-of-turn code path
 * runs for both human and AI ends.
 *
 * <p>A hard step cap defends against logic bugs that could otherwise loop forever — if it trips
 * the executor force-ends the turn.</p>
 */
public class AiTurnExecutor {

    /** Defensive cap; well above any real turn (units * potential per-unit decisions + builds). */
    private static final int MAX_STEPS_PER_TURN = 512;

    private final AiEngine engine;
    private final PlayableGameSession session;
    private final AiStepRunner presentation;
    private final int delayMs;
    private final Runnable onAfterStep;
    private final Runnable onTurnFinished;
    private Timer timer;
    private boolean running;
    private int stepsThisTurn;

    public AiTurnExecutor(
        AiEngine engine,
        PlayableGameSession session,
        AiStepRunner presentation,
        int delayMs,
        Runnable onAfterStep,
        Runnable onTurnFinished
    ) {
        this.engine = engine;
        this.session = session;
        this.presentation = presentation != null ? presentation : new DirectAiStepRunner(session);
        this.delayMs = Math.max(0, delayMs);
        this.onAfterStep = onAfterStep;
        this.onTurnFinished = onTurnFinished;
    }

    public boolean isRunning() {
        return running;
    }

    /** Begins the AI turn. No-op if already running or if the active player isn't AI. */
    public void start() {
        if (running || !engine.controlsActivePlayer()) {
            return;
        }
        running = true;
        stepsThisTurn = 0;
        scheduleNext();
    }

    /** Cancels any pending step. The session is left in whatever state the last step ended in. */
    public void stop() {
        running = false;
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    private void scheduleNext() {
        timer = new Timer(delayMs, e -> {
            timer = null;
            stepOnce();
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void stepOnce() {
        if (!running) {
            return;
        }
        if (session.matchFinished()) {
            finishTurn();
            return;
        }
        if (stepsThisTurn++ >= MAX_STEPS_PER_TURN) {
            finishTurn();
            return;
        }
        AiAction action = engine.nextAction();
        presentation.runPresentationStep(action, () -> afterPresentation(action));
    }

    private void afterPresentation(AiAction action) {
        if (!running) {
            return;
        }
        if (onAfterStep != null) {
            onAfterStep.run();
        }
        if (session.matchFinished()) {
            finishTurn();
            return;
        }
        if (action instanceof AiAction.EndTurn) {
            finishTurn();
            return;
        }
        scheduleNext();
    }

    private void finishTurn() {
        running = false;
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        if (onTurnFinished != null) {
            onTurnFinished.run();
        }
    }

    /**
     * Headless / fallback applicator: applies session effects immediately with no UI (legacy AI
     * behaviour).
     */
    private static final class DirectAiStepRunner implements AiStepRunner {
        private final PlayableGameSession session;

        DirectAiStepRunner(PlayableGameSession session) {
            this.session = session;
        }

        @Override
        public void runPresentationStep(AiAction action, Runnable whenComplete) {
            applyAction(action);
            whenComplete.run();
        }

        private void applyAction(AiAction action) {
            AiActionApplicator.apply(session, action);
        }
    }
}
