package ru.boss.evomarket.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        String normalized = MarketCategories.normalizeHiddenCategory(category);
        return normalized != null && hidden.contains(normalized);
    }

    public synchronized List<String> hiddenCategories() {
        return List.copyOf(hidden);
    }

    public synchronized boolean hide(String category) {
        String normalized = MarketCategories.normalizeHiddenCategory(category);
        if (normalized == null || MarketCategories.ALL.equals(normalized) || "Скрытые".equals(normalized)) return false;
        boolean changed = hidden.add(normalized);
        if (changed) save();
        return changed;
    }

    public synchronized boolean restore(String category) {
        String normalized = MarketCategories.normalizeHiddenCategory(category);
        boolean changed = normalized != null && hidden.remove(normalized);
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
        boolean changed = false;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String original = line.trim();
                String category = MarketCategories.normalizeHiddenCategory(original);
                if (category == null || MarketCategories.ALL.equals(category) || "Скрытые".equals(category)) {
                    changed = true;
                    continue;
                }
                if (!category.equals(original)) changed = true;
                if (!hidden.add(category)) changed = true;
            }
            // Migrate old typo/sentence-fragment entries once, so the hidden list doesn't
            // keep showing categories such as "По и", "Много лю", or mixed "ЛЛБ РАКР".
            if (changed) save();
        } catch (IOException exception) {
            System.err.println("[EvoMarket] Не удалось загрузить скрытые категории: " + exception.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            AtomicFiles.writeLines(file, hidden);
        } catch (IOException exception) {
            System.err.println("[EvoMarket] Не удалось сохранить скрытые категории: " + exception.getMessage());
        }
    }
}
