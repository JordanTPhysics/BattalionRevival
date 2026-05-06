package com.game.audio;

import com.game.model.units.Unit;

/** Resolves {@link UnitSoundPaths} and triggers playback on the appropriate {@link SoundLane}. */
public final class UnitSoundEffects {

    private final ClasspathWavPlayer player;

    public UnitSoundEffects(ClasspathWavPlayer player) {
        this.player = player;
    }

    public void playMove(Unit unit) {
        if (unit == null) {
            return;
        }
        String path = UnitSoundPaths.movementClasspath(unit.getUnitType().movementKind());
        player.playAsync(path, SoundLane.MOVEMENT);
    }

    /** Outgoing strike from the attacker (first hit in combat). */
    public void playOutgoingAttackIfArmed(Unit unit) {
        playAttackOnLane(unit, SoundLane.OUTGOING_ATTACK);
    }

    /** Counter strike from the defender. */
    public void playCounterAttackIfArmed(Unit unit) {
        playAttackOnLane(unit, SoundLane.COUNTER_ATTACK);
    }

    private void playAttackOnLane(Unit unit, SoundLane lane) {
        if (unit == null || !unit.isAlive()) {
            return;
        }
        if (unit.getAttackType() == Unit.AttackType.NONE || unit.getAttackPower() <= 0) {
            return;
        }
        String path = UnitSoundPaths.attackClasspath(unit.getUnitType());
        player.playAsync(path, lane);
    }

    /** After damage resolves: explosion SFX if the unit was destroyed. */
    public void playExplosionIfDead(Unit unit) {
        if (unit == null || unit.isAlive()) {
            return;
        }
        player.playAsync(UnitSoundPaths.destroyedUnitExplosionClasspath(), SoundLane.EXPLOSION);
    }
}
