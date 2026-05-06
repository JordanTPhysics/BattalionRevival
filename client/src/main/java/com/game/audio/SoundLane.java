package com.game.audio;

/**
 * Audio lanes: each lane has its own single-thread queue, so movement, outgoing fire,
 * counter fire, and explosions can overlap in real time without blocking each other.
 * <p>
 * Within one lane, clips are strictly ordered (two rapid moves on the same lane queue up).
 */
public enum SoundLane {
    MOVEMENT,
    OUTGOING_ATTACK,
    COUNTER_ATTACK,
    EXPLOSION,
    /** Start menu self-test only; does not share a queue with gameplay. */
    DIAGNOSTIC
}
