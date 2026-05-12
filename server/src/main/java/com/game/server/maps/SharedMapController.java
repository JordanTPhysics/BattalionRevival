package com.game.server.maps;

import com.game.persistence.MapJsonPersistence;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * User-generated map catalog stored in PostgreSQL ({@link SharedMapStore}).
 */
@RestController
@RequestMapping("/api/maps")
public class SharedMapController {

    private static final int MAX_JSON_CHARS = 512 * 1024;

    private final SharedMapStore store;

    public SharedMapController(SharedMapStore store) {
        this.store = store;
    }

    public record MapUploadRequest(String slug, String ownerUsername, String mapJson, Integer schemaVersion) {
    }

    @GetMapping
    public List<SharedMapContracts.MapSummary> list() {
        return store.listSummaries();
    }

    /**
     * Raw map JSON (same format as local {@code .json} map files).
     */
    @GetMapping(value = "/{slug}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> downloadRaw(@PathVariable("slug") String slug) {
        String s = slug.trim().toLowerCase(Locale.ROOT);
        if (!SharedMapContracts.isSlugSafe(s)) {
            return ResponseEntity.badRequest().build();
        }
        try {
            return ResponseEntity.ok(store.readMapJson(s));
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<String> upload(@RequestBody MapUploadRequest body) {
        if (body == null || body.mapJson() == null || body.mapJson().isBlank()) {
            return ResponseEntity.badRequest().body("mapJson required");
        }
        if (body.mapJson().length() > MAX_JSON_CHARS) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body("mapJson exceeds size quota");
        }
        String slug = body.slug() == null ? "" : body.slug().trim().toLowerCase(Locale.ROOT);
        if (!SharedMapContracts.isSlugSafe(slug)) {
            return ResponseEntity.badRequest().body("slug must be lowercase alphanumeric/hyphen");
        }
        String owner = body.ownerUsername() == null ? "anonymous" : body.ownerUsername().trim();
        if (owner.isEmpty() || owner.length() > 64) {
            return ResponseEntity.badRequest().body("ownerUsername invalid");
        }
        if (store.slugExists(slug)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("slug already exists");
        }
        try {
            MapJsonPersistence.parse(body.mapJson());
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body("invalid map json: " + ex.getMessage());
        }

        try {
            store.saveUpload(
                slug,
                owner,
                body.mapJson(),
                body.schemaVersion != null ? body.schemaVersion() : 1
            );
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
        }
        return ResponseEntity.ok("saved");
    }
}
