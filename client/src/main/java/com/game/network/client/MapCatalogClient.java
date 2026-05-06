package com.game.network.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.game.network.protocol.ProtocolJson;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * REST client for the map catalog and match bootstrap APIs on the Battalion server.
 */
public final class MapCatalogClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build();

    private static final TypeReference<List<MapSummary>> MAP_SUMMARY_LIST = new TypeReference<>() {
    };

    /**
     * Mirrors {@code GET /api/maps} entries (file-backed catalog on the server).
     */
    public record MapSummary(long id, String slug, String ownerUsername, int schemaVersion, String createdAt) {
    }

    private MapCatalogClient() {
    }

    public static List<MapSummary> listMaps(String serverRoot) throws IOException {
        String root = normalizeServerRoot(serverRoot);
        Request request = new Request.Builder()
            .url(root + "/api/maps")
            .get()
            .build();
        try (Response response = HTTP.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException(response.code() + " " + response.message() + ": " + respBody);
            }
            if (respBody.isBlank()) {
                return Collections.emptyList();
            }
            try {
                return ProtocolJson.mapper().readValue(respBody, MAP_SUMMARY_LIST);
            } catch (JsonProcessingException e) {
                throw new IOException("Bad catalog JSON: " + e.getMessage(), e);
            }
        }
    }

    public static String downloadMapJson(String serverRoot, String slug) throws IOException {
        String root = normalizeServerRoot(serverRoot);
        HttpUrl base = HttpUrl.parse(root);
        if (base == null) {
            throw new IllegalArgumentException("Invalid server URL");
        }
        HttpUrl url = base.newBuilder()
            .addPathSegment("api")
            .addPathSegment("maps")
            .addPathSegment(slug)
            .build();
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = HTTP.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            if (response.code() == 404) {
                throw new IOException("Map not found: " + slug);
            }
            if (!response.isSuccessful()) {
                throw new IOException(response.code() + " " + response.message() + ": " + respBody);
            }
            return respBody;
        }
    }

    /**
     * {@code POST /api/matches} — idempotent; every player can call before connecting.
     */
    public static String ensureMatch(String serverRoot, String matchId, String mapSlug)
        throws IOException, JsonProcessingException {
        String root = normalizeServerRoot(serverRoot);
        ObjectNode body = ProtocolJson.mapper().createObjectNode();
        body.put("matchId", matchId);
        body.put("mapSlug", mapSlug);
        String payload = ProtocolJson.mapper().writeValueAsString(body);

        Request request = new Request.Builder()
            .url(root + "/api/matches")
            .post(RequestBody.create(payload, JSON))
            .build();

        try (Response response = HTTP.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            if (response.code() == 404) {
                throw new IOException("Unknown map or match setup failed: " + respBody);
            }
            if (!response.isSuccessful()) {
                throw new IOException(response.code() + " " + response.message() + ": " + respBody);
            }
            return respBody.isEmpty() ? response.message() : respBody;
        }
    }

    /**
     * POST JSON body matching server {@code MapUploadRequest}.
     */
    public static String uploadMap(String serverRoot, String slug, String ownerUsername, String mapJson)
        throws IOException, JsonProcessingException {
        Objects.requireNonNull(mapJson, "mapJson");
        String root = normalizeServerRoot(serverRoot);
        ObjectNode body = ProtocolJson.mapper().createObjectNode();
        body.put("slug", slug);
        body.put("ownerUsername", ownerUsername == null || ownerUsername.isBlank() ? "anonymous" : ownerUsername.trim());
        body.put("mapJson", mapJson);
        body.put("schemaVersion", 1);
        String payload = ProtocolJson.mapper().writeValueAsString(body);

        Request request = new Request.Builder()
            .url(root + "/api/maps")
            .post(RequestBody.create(payload, JSON))
            .build();

        try (Response response = HTTP.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException(response.code() + " " + response.message() + ": " + respBody);
            }
            return respBody.isEmpty() ? response.message() : respBody;
        }
    }

    private static String normalizeServerRoot(String serverRoot) {
        if (serverRoot == null || serverRoot.isBlank()) {
            throw new IllegalArgumentException("Server URL is required.");
        }
        String s = serverRoot.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
