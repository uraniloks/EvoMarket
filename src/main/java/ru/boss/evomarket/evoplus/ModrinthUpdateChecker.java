package ru.boss.evomarket.evoplus;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.boss.evomarket.core.VersionOrder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import ru.dargen.evoplus.api.addon.Addon;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Checks compatible, published Modrinth versions without blocking the client thread. */
final class ModrinthUpdateChecker {
    /** Keep this equal to the project's Modrinth slug after the project page is created. */
    static final String MODRINTH_PROJECT_SLUG = "evomarket";
    static final String MODRINTH_PROJECT_URL = "https://modrinth.com/mod/" + MODRINTH_PROJECT_SLUG;
    static final String CURRENT_VERSION = "0.1.18";
    private static final String API_URL = "https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT_SLUG
            + "/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%2226.2%22%5D&include_changelog=true";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final long CHECK_INTERVAL_MS = Duration.ofHours(12).toMillis();
    private static final long RETRY_INTERVAL_MS = Duration.ofMinutes(30).toMillis();
    private static final long SNOOZE_MS = Duration.ofHours(24).toMillis();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final Addon addon;
    private final BooleanSupplier enabled;
    private final AtomicBoolean requestInProgress = new AtomicBoolean(false);
    private final UpdateState state;

    private volatile long nextCheckAt;
    private volatile UpdateInfo availableUpdate;
    private volatile String shownVersion = "";

    ModrinthUpdateChecker(Addon addon, BooleanSupplier enabled, Path statePath) {
        this.addon = addon;
        this.enabled = enabled;
        this.state = new UpdateState(statePath);
    }

    void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(Minecraft client) {
        if (!enabled.getAsBoolean()) return;

        long now = System.currentTimeMillis();
        if (now >= nextCheckAt && requestInProgress.compareAndSet(false, true)) {
            // Set a retry time immediately. The worker shortens it if the request fails.
            nextCheckAt = now + CHECK_INTERVAL_MS;
            CompletableFuture.runAsync(this::checkRemoteVersions)
                    .exceptionally(error -> {
                        nextCheckAt = System.currentTimeMillis() + RETRY_INTERVAL_MS;
                        requestInProgress.set(false);
                        return null;
                    });
        }

        UpdateInfo update = availableUpdate;
        if (update == null || update.version().equals(shownVersion)) return;
        // Minecraft 26.2 no longer exposes the old `client.screen` field.
        // Wait until the player is in a world, then show the EvoPlus notice window.
        if (client.player == null || client.level == null) return;
        if (state.isSnoozed(update.version(), now, SNOOZE_MS)) return;

        // Set before opening so the tick callback cannot spawn multiple windows.
        shownVersion = update.version();
        UpdateNoticeWindow.open(addon, update, () -> {
            state.dismiss(update.version());
            // Permit the same version to be shown again after the 24-hour snooze expires.
            shownVersion = "";
        });
    }

    private void checkRemoteVersions() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "uraniloks/EvoMarket/" + CURRENT_VERSION + " (https://github.com/uraniloks/EvoMarket)")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                // Before the Modrinth project is published, a 404 is expected; fail silently.
                nextCheckAt = System.currentTimeMillis() + (response.statusCode() == 404
                        ? CHECK_INTERVAL_MS : RETRY_INTERVAL_MS);
                return;
            }

            JsonElement root = JsonParser.parseString(response.body());
            if (!root.isJsonArray()) return;
            JsonArray versions = root.getAsJsonArray();
            UpdateInfo latest = null;
            for (JsonElement element : versions) {
                if (!element.isJsonObject()) continue;
                JsonObject version = element.getAsJsonObject();
                String status = string(version, "status", "listed");
                if (!"listed".equalsIgnoreCase(status)) continue;
                String versionNumber = string(version, "version_number", "");
                if (versionNumber.isBlank()) continue;
                if (latest == null || VersionOrder.compare(versionNumber, latest.version()) > 0) {
                    latest = new UpdateInfo(versionNumber,
                            string(version, "name", "EvoMarket " + versionNumber),
                            string(version, "changelog", "").trim(),
                            MODRINTH_PROJECT_URL);
                }
            }

            if (latest != null && VersionOrder.compare(latest.version(), CURRENT_VERSION) > 0) {
                availableUpdate = latest;
            } else {
                availableUpdate = null;
            }
            nextCheckAt = System.currentTimeMillis() + CHECK_INTERVAL_MS;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            nextCheckAt = System.currentTimeMillis() + RETRY_INTERVAL_MS;
        } catch (Exception exception) {
            // Network and JSON errors must never interrupt gameplay.
            nextCheckAt = System.currentTimeMillis() + RETRY_INTERVAL_MS;
        } finally {
            requestInProgress.set(false);
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

    record UpdateInfo(String version, String name, String changelog, String projectUrl) {}

    private static final class UpdateState {
        private final Path file;
        private String dismissedVersion = "";
        private long dismissedAt;

        private UpdateState(Path file) {
            this.file = file;
            load();
        }

        synchronized boolean isSnoozed(String version, long now, long snoozeMillis) {
            return version.equals(dismissedVersion) && dismissedAt > 0 && now - dismissedAt < snoozeMillis;
        }

        synchronized void dismiss(String version) {
            dismissedVersion = version;
            dismissedAt = System.currentTimeMillis();
            Properties properties = new Properties();
            properties.setProperty("dismissedVersion", dismissedVersion);
            properties.setProperty("dismissedAt", Long.toString(dismissedAt));
            try {
                Files.createDirectories(file.getParent());
                try (var output = Files.newOutputStream(file, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                    properties.store(output, "EvoMarket update notification state");
                }
            } catch (IOException exception) {
                System.err.println("[EvoMarket] Could not save update state: " + exception.getMessage());
            }
        }

        private synchronized void load() {
            if (!Files.isRegularFile(file)) return;
            Properties properties = new Properties();
            try (var input = Files.newInputStream(file)) {
                properties.load(input);
                dismissedVersion = properties.getProperty("dismissedVersion", "");
                dismissedAt = Long.parseLong(properties.getProperty("dismissedAt", "0"));
            } catch (Exception ignored) {
                dismissedVersion = "";
                dismissedAt = 0;
            }
        }
    }
}
