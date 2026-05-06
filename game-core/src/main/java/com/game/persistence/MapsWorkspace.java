package com.game.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the shared {@code maps} directory used by the client (chooser, builder, CLI default map)
 * and by the server demo bootstrap.
 *
 * <p>Uses {@code maps} next to {@link System#getProperty(String) user.dir} when present; otherwise
 * walks parents so running from a subproject directory (e.g. {@code client/}) still finds a repo-root
 * {@code maps/} folder. Override with {@code -Dcom.game.mapsDir=/absolute/path}.</p>
 */
public final class MapsWorkspace {

    private MapsWorkspace() {
    }

    public static Path mapsDirectory() {
        String override = System.getProperty("com.game.mapsDir");
        if (override != null && !override.isBlank()) {
            return Paths.get(override.trim()).toAbsolutePath().normalize();
        }
        Path cwd = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path adjacent = cwd.resolve("maps");
        if (Files.isDirectory(adjacent)) {
            return adjacent;
        }
        Path dir = cwd;
        for (int depth = 0; depth < 64 && dir != null; depth++) {
            Path candidate = dir.resolve("maps");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            Path parent = dir.getParent();
            if (parent == null || parent.equals(dir)) {
                break;
            }
            dir = parent;
        }
        return adjacent;
    }
}
