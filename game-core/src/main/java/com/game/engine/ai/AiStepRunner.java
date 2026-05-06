package com.game.engine.ai;

/**
 * Presents one AI {@link AiAction}'s worth of gameplay (including move/combat visuals and SFX)
 * and signals the executor on the Swing EDT when the presentation is finished so the next action
 * can be scheduled.
 */
@FunctionalInterface
public interface AiStepRunner {

    void runPresentationStep(AiAction action, Runnable whenComplete);
}
