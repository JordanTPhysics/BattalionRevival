package com.game.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Plays WAV resources from the classpath using one single-thread executor per {@link SoundLane}.
 * Lanes overlap (movement can play while attack plays); within a lane, sounds are serialized.
 * <p>
 * Each WAV is read and decoded to PCM signed 16-bit little-endian exactly once, then cached for
 * the lifetime of the JVM. Playback uses a fresh {@link SourceDataLine} per request: write the
 * cached PCM bytes, {@link SourceDataLine#drain()} so the device buffer fully empties, then close.
 * <p>
 * {@link javax.sound.sampled.Clip} was avoided here because {@code Clip.drain()} on Windows
 * DirectSound regularly returns before playback has finished, which previously made the
 * try-with-resources {@code close()} cut clips off and produced random missing sounds.
 * <p>
 * Optional cap: set {@code -Dcom.game.audio.maxSoundMs=N} with {@code N > 0} to stop each clip after
 * {@code N} milliseconds (per clip, per lane). Default {@code 0} means play to the end.
 */
public final class ClasspathWavPlayer {

    private static final Logger LOGGER = Logger.getLogger(ClasspathWavPlayer.class.getName());

    /** Linear gain 0..1 applied to PCM before writing to the mixer (all lanes). */
    private static volatile float masterVolume = 1f;

    private static final int WRITE_CHUNK_BYTES = 8 * 1024;

    private static final Map<SoundLane, ExecutorService> LANES = new EnumMap<>(SoundLane.class);

    /** Decoded PCM samples + format, keyed by normalized classpath resource path. */
    private static final ConcurrentMap<String, DecodedClip> DECODED_CACHE = new ConcurrentHashMap<>();

    static {
        for (SoundLane lane : SoundLane.values()) {
            String name = "audio-" + lane.name().toLowerCase(Locale.ROOT).replace('_', '-');
            LANES.put(
                lane,
                Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, name);
                    t.setDaemon(true);
                    return t;
                })
            );
        }
    }

    /**
     * Sets master output gain ({@code 0} = silent, {@code 1} = full). Values are clamped to {@code [0, 1]}.
     * Affects subsequent playback on all lanes.
     */
    public static void setMasterVolume(float linear0to1) {
        if (linear0to1 < 0f) {
            masterVolume = 0f;
        } else if (linear0to1 > 1f) {
            masterVolume = 1f;
        } else {
            masterVolume = linear0to1;
        }
    }

    public static float getMasterVolume() {
        return masterVolume;
    }

    public void playAsync(String classpathResource, SoundLane lane) {
        if (classpathResource == null || classpathResource.isBlank()) {
            return;
        }
        String path = normalizeClasspath(classpathResource);
        executor(lane).execute(() -> {
            String err = playBlockingInternal(path);
            if (err != null) {
                LOGGER.warning(() -> "[" + lane + "] Sound: " + err);
            }
        });
    }

    /**
     * Decodes every supplied resource into the cache, pre-opens a {@link SourceDataLine} to spin up
     * the OS audio mixer, and primes each lane's worker thread. Runs entirely on a dedicated daemon
     * thread so it never blocks the caller. Safe to call multiple times — already-decoded resources
     * are skipped via the cache. After this completes, the first {@link #playAsync} call has no
     * decode work, no thread-creation latency, and no first-time mixer-open latency.
     */
    public void prewarm(Iterable<String> classpathResources) {
        Thread t = new Thread(() -> runPrewarm(classpathResources), "audio-prewarm");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private static void runPrewarm(Iterable<String> classpathResources) {
        AudioFormat firstFormat = null;
        if (classpathResources != null) {
            for (String r : classpathResources) {
                if (r == null || r.isBlank()) {
                    continue;
                }
                String path = normalizeClasspath(r);
                try {
                    DecodedClip d = decodeOrGet(path);
                    if (firstFormat == null && d != null) {
                        firstFormat = d.format;
                    }
                } catch (IOException | UnsupportedAudioFileException ex) {
                    LOGGER.log(Level.FINE, "Prewarm decode failed: " + path, ex);
                } catch (RuntimeException ex) {
                    LOGGER.log(Level.FINE, "Prewarm decode failed: " + path, ex);
                }
            }
        }
        if (firstFormat != null) {
            warmMixer(firstFormat);
        }
        primeLaneThreads();
    }

    /**
     * Open and close a {@link SourceDataLine} so the first real playback doesn't pay the
     * device-open cost (Windows DirectSound in particular is slow on the very first acquire).
     */
    private static void warmMixer(AudioFormat format) {
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            try {
                line.open(format);
            } finally {
                line.close();
            }
        } catch (LineUnavailableException | RuntimeException ex) {
            LOGGER.log(Level.FINE, "Prewarm mixer-open failed", ex);
        }
    }

    /**
     * Submit a no-op task to each lane's executor so the underlying daemon thread is created
     * before the first real playback request needs it.
     */
    private static void primeLaneThreads() {
        for (ExecutorService exec : LANES.values()) {
            try {
                exec.execute(() -> {
                });
            } catch (RuntimeException ignored) {
                // Executor rejected (already shut down) — nothing to prime.
            }
        }
    }

    /**
     * Blocks until the request has run on the given lane (same ordering as {@link #playAsync}).
     *
     * @return {@code null} if playback finished; otherwise a short error description
     */
    public String playBlockingOrError(String classpathResource, SoundLane lane) {
        return submitAndWait(classpathResource, lane);
    }

    /**
     * Self-test on {@link SoundLane#DIAGNOSTIC} so it never queues behind gameplay.
     */
    public static String playOnceBlockingForDiagnostics(String classpathResource) {
        return submitAndWait(classpathResource, SoundLane.DIAGNOSTIC);
    }

    private static String submitAndWait(String classpathResource, SoundLane lane) {
        if (classpathResource == null || classpathResource.isBlank()) {
            return "No sound path (null or blank).";
        }
        String path = normalizeClasspath(classpathResource);
        Future<String> future = executor(lane).submit(() -> playBlockingInternal(path));
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Interrupted";
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof Error) {
                throw (Error) c;
            }
            LOGGER.log(Level.WARNING, "Audio task failed [" + lane + "]", e);
            return c != null ? c.getClass().getSimpleName() + ": " + c.getMessage() : String.valueOf(e);
        }
    }

    private static ExecutorService executor(SoundLane lane) {
        return LANES.get(lane);
    }

    private static String normalizeClasspath(String classpathResource) {
        return classpathResource.startsWith("/") ? classpathResource : "/" + classpathResource;
    }

    /**
     * @return {@code null} on success
     */
    private static String playBlockingInternal(String classpathResource) {
        DecodedClip decoded;
        try {
            decoded = decodeOrGet(classpathResource);
        } catch (UnsupportedAudioFileException e) {
            return "Unsupported audio format for " + classpathResource + ": " + e.getMessage();
        } catch (IOException e) {
            return "I/O error: " + e.getMessage();
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        if (decoded == null) {
            return "Resource not on classpath: " + classpathResource;
        }
        if (decoded.pcm.length == 0) {
            return "Decoded zero audio bytes: " + classpathResource;
        }
        return playDecoded(decoded, classpathResource);
    }

    private static String playDecoded(DecodedClip decoded, String classpathResource) {
        AudioFormat format = decoded.format;
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        SourceDataLine line;
        try {
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);
        } catch (LineUnavailableException e) {
            return "No audio line available (mixer in use or no output device?): " + e.getMessage();
        } catch (IllegalArgumentException e) {
            return "No matching SourceDataLine for format " + format + ": " + e.getMessage();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unexpected line open error: " + classpathResource, e);
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        try {
            line.start();
            writeAllPcmRespectingCap(line, decoded.pcm, format);
            line.drain();
            return null;
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            try {
                line.stop();
            } catch (RuntimeException ignored) {
                // Some mixers throw if the line was already auto-stopped; safe to swallow before close.
            }
            line.close();
        }
    }

    /**
     * Writes the cached PCM bytes to {@code line} chunk by chunk. {@code SourceDataLine.write} blocks
     * while the device buffer is full, so this naturally paces with playback. Honors
     * {@code com.game.audio.maxSoundMs} by stopping early once the cap is exceeded.
     */
    private static void writeAllPcmRespectingCap(SourceDataLine line, byte[] pcm, AudioFormat format) {
        long maxMs = Long.getLong("com.game.audio.maxSoundMs", 0L);
        long deadlineNanos = maxMs > 0
            ? System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(250L, maxMs))
            : 0L;
        int frameSize = Math.max(1, format.getFrameSize());
        int chunk = Math.max(frameSize, (WRITE_CHUNK_BYTES / frameSize) * frameSize);
        float v = masterVolume;
        boolean scale = v < 0.999f;
        byte[] scratch = scale ? new byte[chunk] : null;
        int written = 0;
        while (written < pcm.length) {
            if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) {
                return;
            }
            int remaining = pcm.length - written;
            int toWrite = Math.min(chunk, remaining);
            int n;
            if (!scale) {
                n = line.write(pcm, written, toWrite);
            } else {
                scalePcm16LeChunk(pcm, written, toWrite, v, scratch);
                n = line.write(scratch, 0, toWrite);
            }
            if (n <= 0) {
                return;
            }
            written += n;
        }
    }

    /**
     * Scales 16-bit little-endian PCM in {@code src[srcOff .. srcOff+length)} into {@code dest[0 .. length)}.
     * {@code dest} must be at least {@code length} bytes. {@code length} must be a multiple of 2.
     */
    private static void scalePcm16LeChunk(byte[] src, int srcOff, int length, float gain, byte[] dest) {
        for (int i = 0; i < length; i += 2) {
            int lo = src[srcOff + i] & 0xff;
            int hi = src[srcOff + i + 1];
            int s = (hi << 8) | lo;
            float f = s * gain;
            int scaled = Math.round(f);
            if (scaled > 32767) {
                scaled = 32767;
            } else if (scaled < -32768) {
                scaled = -32768;
            }
            dest[i] = (byte) scaled;
            dest[i + 1] = (byte) (scaled >> 8);
        }
    }

    private static DecodedClip decodeOrGet(String classpathResource)
        throws UnsupportedAudioFileException, IOException {
        DecodedClip cached = DECODED_CACHE.get(classpathResource);
        if (cached != null) {
            return cached;
        }
        DecodedClip loaded = loadDecoded(classpathResource);
        if (loaded == null) {
            return null;
        }
        DecodedClip prior = DECODED_CACHE.putIfAbsent(classpathResource, loaded);
        return prior != null ? prior : loaded;
    }

    private static DecodedClip loadDecoded(String classpathResource)
        throws UnsupportedAudioFileException, IOException {
        try (InputStream raw = ClasspathWavPlayer.class.getResourceAsStream(classpathResource)) {
            if (raw == null) {
                return null;
            }
            try (BufferedInputStream buffered = new BufferedInputStream(raw, 64 * 1024);
                 AudioInputStream source = AudioSystem.getAudioInputStream(buffered);
                 AudioInputStream pcm = toPcmSigned16Stream(source)) {
                AudioFormat format = pcm.getFormat();
                byte[] frames = pcm.readAllBytes();
                int frameSize = format.getFrameSize();
                if (frameSize <= 0 || (frames.length != 0 && frames.length % frameSize != 0)) {
                    throw new IOException("Bad frame layout (" + frameSize + " / " + frames.length + ")");
                }
                return new DecodedClip(format, frames);
            }
        }
    }

    private static AudioInputStream toPcmSigned16Stream(AudioInputStream source)
        throws UnsupportedAudioFileException {
        AudioFormat f = source.getFormat();
        if (isPcmSigned16LittleEndian(f)) {
            return source;
        }
        AudioFormat target = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            f.getSampleRate(),
            16,
            f.getChannels(),
            f.getChannels() * 2,
            f.getSampleRate(),
            false
        );
        if (!AudioSystem.isConversionSupported(target, f)) {
            throw new UnsupportedAudioFileException("Cannot convert " + f + " to PCM_SIGNED 16-bit LE");
        }
        return AudioSystem.getAudioInputStream(target, source);
    }

    private static boolean isPcmSigned16LittleEndian(AudioFormat f) {
        return f.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)
            && f.getSampleSizeInBits() == 16
            && !f.isBigEndian()
            && f.getFrameSize() == f.getChannels() * 2;
    }

    /** Immutable, fully-decoded PCM payload ready to be written to a {@link SourceDataLine}. */
    private record DecodedClip(AudioFormat format, byte[] pcm) {
    }
}
