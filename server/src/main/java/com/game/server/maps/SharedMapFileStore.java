package com.game.server.maps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.game.network.protocol.ProtocolJson;
import com.game.persistence.MapJsonPersistence;
import com.game.model.map.GameMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Persists uploaded maps as {@code <slug>.json} plus optional {@code <slug>.meta.json} under a directory
 * (no database).
 */
@Component
public class SharedMapFileStore {

    private final Path root;

    public SharedMapFileStore(@Value("${battalion.shared-maps.directory:shared-maps}") String directory) {
        this.root = Paths.get(directory).toAbsolutePath().normalize();
    }

    public Path getRoot() {
        return root;
    }

    public boolean slugExists(String slug) throws IOException {
        return Files.isRegularFile(resolveMapPath(slug));
    }

    public void saveUpload(String slug, String ownerUsername, String mapJson, int schemaVersion) throws IOException {
        Files.createDirectories(root);
        Path mapPath = resolveMapPath(slug);
        if (Files.exists(mapPath)) {
            throw new IllegalStateException("slug already exists");
        }
        Files.writeString(mapPath, mapJson, StandardCharsets.UTF_8);

        ObjectNode meta = ProtocolJson.mapper().createObjectNode();
        meta.put("ownerUsername", ownerUsername == null || ownerUsername.isBlank() ? "anonymous" : ownerUsername.trim());
        meta.put("schemaVersion", schemaVersion);
        meta.put("createdAt", Instant.now().toString());
        Files.writeString(
            resolveMetaPath(slug),
            ProtocolJson.mapper().writeValueAsString(meta),
            StandardCharsets.UTF_8
        );
    }

    public String readMapJson(String slug) throws IOException {
        return Files.readString(resolveMapPath(slug), StandardCharsets.UTF_8);
    }

    public GameMap loadMap(String slug) {
        try {
            return MapJsonPersistence.parse(readMapJson(slug));
        } catch (IOException e) {
            throw new IllegalArgumentException("map not found: " + slug, e);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    public List<SharedMapContracts.MapSummary> listSummaries() throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<SharedMapContracts.MapSummary> out = new ArrayList<>();
        try (Stream<Path> stream = Files.list(root)) {
            stream
                .filter(Files::isRegularFile)
                .filter(p -> {
                    String n = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    return n.endsWith(".json") && !n.endsWith(".meta.json");
                })
                .forEach(mapPath -> {
                    String fileName = mapPath.getFileName().toString();
                    String slug = fileName.substring(0, fileName.length() - 5);
                    try {
                        MetaRead meta = readMeta(slug);
                        FileTime lm = Files.getLastModifiedTime(mapPath);
                        long id = lm.toMillis();
                        out.add(new SharedMapContracts.MapSummary(
                            id,
                            slug,
                            meta.ownerUsername(),
                            meta.schemaVersion(),
                            meta.createdAt() != null ? meta.createdAt() : lm.toString()
                        ));
                    } catch (IOException ignored) {
                        // skip broken entry
                    }
                });
        }
        out.sort(Comparator.comparing(SharedMapContracts.MapSummary::slug, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private MetaRead readMeta(String slug) throws IOException {
        Path mp = resolveMetaPath(slug);
        if (!Files.isRegularFile(mp)) {
            return new MetaRead("anonymous", 1, null);
        }
        JsonNode n = ProtocolJson.mapper().readTree(Files.readString(mp, StandardCharsets.UTF_8));
        String owner = n.has("ownerUsername") ? n.get("ownerUsername").asText("anonymous") : "anonymous";
        int ver = n.has("schemaVersion") ? n.get("schemaVersion").asInt(1) : 1;
        String created = n.has("createdAt") ? n.get("createdAt").asText(null) : null;
        return new MetaRead(owner, ver, created);
    }

    private record MetaRead(String ownerUsername, int schemaVersion, String createdAt) {
    }

    private Path resolveMapPath(String slug) {
        validateSlug(slug);
        Path p = root.resolve(slug + ".json").normalize();
        requireUnderRoot(p);
        return p;
    }

    private Path resolveMetaPath(String slug) {
        validateSlug(slug);
        Path p = root.resolve(slug + ".meta.json").normalize();
        requireUnderRoot(p);
        return p;
    }

    private static void validateSlug(String slug) {
        if (!SharedMapContracts.isSlugSafe(slug)) {
            throw new IllegalArgumentException("invalid slug");
        }
    }

    private void requireUnderRoot(Path p) {
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("path escapes store root");
        }
    }
}
