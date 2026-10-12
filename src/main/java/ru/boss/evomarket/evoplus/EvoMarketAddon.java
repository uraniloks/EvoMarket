package ru.boss.evomarket.evoplus;

import org.lwjgl.glfw.GLFW;
import ru.boss.evomarket.core.MarketStore;
import ru.boss.evomarket.core.CategoryVisibilityStore;
import ru.boss.evomarket.core.TradeAction;
import ru.dargen.evoplus.api.addon.Addon;
import ru.dargen.evoplus.api.addon.EvoPlusAddon;
import ru.dargen.evoplus.api.setting.SettingCategory;
import ru.dargen.evoplus.api.setting.type.BooleanSetting;
import ru.dargen.evoplus.api.setting.type.KeyBind;

public final class EvoMarketAddon implements EvoPlusAddon {
    @Override
    public void onInitialize(Addon addon) {
        MarketStore store = new MarketStore(addon.getAddonFolder().resolve("market-history.tsv"));
        CategoryVisibilityStore categoryVisibility = new CategoryVisibilityStore(
                addon.getAddonFolder().resolve("hidden-categories.txt"));

        SettingCategory general = addon.getSettings().category("general", "Основное");
        BooleanSetting scanning = general.toggle("scan-chat", "Сканировать торговый чат", true)
                .description("Искать в чате сообщения о покупке и продаже предметов.");

        MarketScanner scanner = new MarketScanner(store, scanning::get);
        scanner.register();

        general.key("open-market", "Открыть EvoMarket", KeyBind.keyboard(GLFW.GLFW_KEY_M, KeyBind.CONTROL))
                .description("Открыть окно рынка сочетанием Ctrl+M.")
                .onPress(() -> MarketWindow.open(addon, store, categoryVisibility));

        general.button("clear-history", "История", "Очистить историю", () -> store.clear())
                .description("Удаляет сохранённые объявления с этого компьютера.");

        BooleanSetting updateChecks = general.toggle("check-updates", "Проверять обновления", true)
                .description("Проверять совместимые опубликованные версии EvoMarket на Modrinth при запуске и раз в 12 часов.");
        new ModrinthUpdateChecker(addon, updateChecks::get,
                addon.getAddonFolder().resolve("update-state.properties")).register();

        general.widget("market-summary", "Сводка EvoMarket", 160, 34, ctx -> {
            ctx.rect(0, 0, ctx.getWidth(), ctx.getHeight(), 0xB0101018);
            ctx.text("EvoMarket · " + store.size() + " объявл.", 5, 4, 0xFFFFFFFF, true);
            ctx.text("Покупка: " + store.count(TradeAction.BUY) + "  Продажа: " + store.count(TradeAction.SELL),
                    5, 17, 0xFFD7D7D7, false);
        }).defaultBackground(true);
    }
}
