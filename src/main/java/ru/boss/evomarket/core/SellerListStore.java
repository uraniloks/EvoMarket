package ru.boss.evomarket.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Persistent case-insensitive list of Minecraft player names (favorites or ignored players). */
public final class SellerListStore {
    private final Path file;
    private final Map<String, String> names = new LinkedHashMap<>();

    public SellerListStore(Path file) {
        this.file = file;
        load();
    }

    public synchronized boolean contains(String username) {
        String key = key(username);
        return key != null && names.containsKey(key);
    }

    public synchronized List<String> names() {
        return List.copyOf(names.values());
    }

    public synchronized boolean add(String username) {
        String display = clean(username);
        String key = key(display);
        if (key == null || names.containsKey(key)) return false;
        names.put(key, display);
        save();
        return true;
    }

    public synchronized boolean remove(String username) {
        String key = key(username);
        if (key == null || names.remove(key) == null) return false;
        save();
        return true;
    }

    public synchronized void clear() {
        if (names.isEmpty()) return;
        names.clear();
        save();
    }

    private void load() {
        if (!Files.isRegularFile(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String display = clean(line);
                String key = key(display);
                if (key != null) names.putIfAbsent(key, display);
            }
        } catch (IOException exception) {
            System.err.println("[EvoMarket] Could not load player list: " + exception.getMessage());
        }
    }

    private void save() {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            AtomicFiles.writeLines(file, names.values());
        } catch (IOException exception) {
            System.err.println("[EvoMarket] Could not save player list: " + exception.getMessage());
        }
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replaceAll("(?i)§[0-9A-FK-OR]", "")
                .replaceAll("[\\r\\n\\t]", " ").trim();
    }

    private static String key(String value) {
        String cleaned = clean(value);
        if (cleaned.isBlank()) return null;
        return cleaned.toLowerCase(Locale.ROOT);
    }
}
