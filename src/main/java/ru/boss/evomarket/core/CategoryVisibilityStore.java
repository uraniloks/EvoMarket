package ru.boss.evomarket.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Persists user-hidden market categories separately from offer history. */
public final class CategoryVisibilityStore {
    private final Path file;
    private final Set<String> hidden = new LinkedHashSet<>();

    public CategoryVisibilityStore(Path file) {
        this.file = file;
        load();
    }

    public synchronized boolean isHidden(String category) {
        return category != null && hidden.contains(category);
    }

    public synchronized List<String> hiddenCategories() {
        return List.copyOf(hidden);
    }

    public synchronized boolean hide(String category) {
        if (category == null || category.isBlank()
                || MarketCategories.ALL.equals(category) || "Скрытые".equals(category)) return false;
        boolean changed = hidden.add(category.trim());
        if (changed) save();
        return changed;
    }

    public synchronized boolean restore(String category) {
        boolean changed = category != null && hidden.remove(category);
        if (changed) save();
        return changed;
    }

    public synchronized void restoreAll() {
        if (hidden.isEmpty()) return;
        hidden.clear();
        save();
    }

    private void load() {
        if (!Files.isRegularFile(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String category = line.trim();
                if (!category.isBlank() && !MarketCategories.ALL.equals(category)
                        && !"Скрытые".equals(category)) hidden.add(category);
            }
        } catch (IOException exception) {
            System.err.println("[EvoMarket] Не удалось загрузить скрытые категории: " + exception.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.write(file, hidden, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            System.err.println("[EvoMarket] Не удалось сохранить скрытые категории: " + exception.getMessage());
        }
    }
}
