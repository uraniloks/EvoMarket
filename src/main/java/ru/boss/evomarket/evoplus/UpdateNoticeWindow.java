package ru.boss.evomarket.evoplus;

import ru.dargen.evoplus.api.addon.Addon;
import ru.dargen.evoplus.api.ui.TextStyle;
import ru.dargen.evoplus.api.ui.Ui;
import ru.dargen.evoplus.api.ui.UiContainer;
import ru.dargen.evoplus.api.ui.UiWindow;

import java.awt.Desktop;
import java.net.URI;

/** Small in-game notice shown after a compatible newer Modrinth release is found. */
final class UpdateNoticeWindow {
    private UpdateNoticeWindow() {}

    static void open(Addon addon, ModrinthUpdateChecker.UpdateInfo update, Runnable onDismiss) {
        Ui ui = addon.getUi();
        UiWindow window = ui.window("EvoMarket — доступно обновление", 410, 250, true, true);
        UiContainer content = window.getContent();
        content.clear();
        content.add(ui.text("Доступно обновление EvoMarket", TextStyle.TITLE)
                .maxWidth(Math.max(240, window.getBodyWidth() - 24), true));
        content.add(ui.spacer(1, 5));
        content.add(ui.text("Установлена версия: " + currentVersion(), TextStyle.SECONDARY));
        content.add(ui.text("Новая версия: " + update.version(), TextStyle.TITLE));
        content.add(ui.spacer(1, 4));

        String changelog = cleanChangelog(update.changelog());
        if (!changelog.isBlank()) {
            content.add(ui.text("Что нового: " + changelog, TextStyle.BODY)
                    .maxWidth(Math.max(240, window.getBodyWidth() - 24), true));
        } else {
            content.add(ui.text("Открой страницу проекта, чтобы скачать новую версию.", TextStyle.SECONDARY)
                    .maxWidth(Math.max(240, window.getBodyWidth() - 24), true));
        }

        window.getFooter().add(
                ui.button("Открыть Modrinth", () -> {
                    openProjectPage(update.projectUrl());
                    window.close();
                }),
                ui.button("Позже", window::close)
        );
        window.onClose(onDismiss);
        window.open();
    }

    private static String currentVersion() {
        return ModrinthUpdateChecker.CURRENT_VERSION;
    }

    private static String cleanChangelog(String value) {
        if (value == null || value.isBlank()) return "";
        String cleaned = value.replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
                .replaceAll("[`*_~]", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.length() > 260) cleaned = cleaned.substring(0, 257) + "…";
        return cleaned;
    }

    private static void openProjectPage(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"modrinth.com".equalsIgnoreCase(uri.getHost())) {
                return;
            }
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
            } else {
                System.err.println("[EvoMarket] Open this URL in your browser: " + url);
            }
        } catch (Exception exception) {
            System.err.println("[EvoMarket] Could not open Modrinth: " + exception.getMessage());
        }
    }
}
