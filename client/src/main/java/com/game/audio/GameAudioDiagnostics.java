package com.game.audio;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;

/**
 * Read-only checks for the desktop Java Sound stack (mixers, etc.). Used by the start menu audio self-test.
 */
public final class GameAudioDiagnostics {

    private GameAudioDiagnostics() {
    }

    /** One line per mixer; useful when {@link ClasspathWavPlayer#playOnceBlockingForDiagnostics} reports line errors. */
    public static String mixerSummary() {
        Mixer.Info[] infos = AudioSystem.getMixerInfo();
        if (infos == null || infos.length == 0) {
            return "(AudioSystem returned no mixers — javax.sound may be unavailable.)";
        }
        StringBuilder sb = new StringBuilder();
        for (Mixer.Info info : infos) {
            sb.append("• ").append(info.getName());
            if (info.getDescription() != null && !info.getDescription().isBlank()) {
                sb.append(" — ").append(info.getDescription());
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }
}
