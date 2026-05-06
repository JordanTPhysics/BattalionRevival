package com.game.server.maps;

import java.util.Locale;
import java.util.regex.Pattern;

public final class SharedMapContracts {

    public static final Pattern SLUG_SAFE = Pattern.compile("^[a-z0-9][a-z0-9-]{1,62}$");

    private SharedMapContracts() {
    }

    public static boolean isSlugSafe(String slug) {
        return slug != null && SLUG_SAFE.matcher(slug.trim().toLowerCase(Locale.ROOT)).matches();
    }

    public record MapSummary(long id, String slug, String ownerUsername, int schemaVersion, String createdAt) {
    }
}
