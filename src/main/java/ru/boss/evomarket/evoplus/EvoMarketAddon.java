package ru.boss.evomarket.evoplus;

import org.lwjgl.glfw.GLFW;
import net.fabricmc.loader.api.FabricLoader;
import ru.boss.evomarket.core.MarketStore;
import ru.boss.evomarket.core.CategoryVisibilityStore;
import ru.boss.evomarket.core.SellerListStore;
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
        SellerListStore ignoredSellers = new SellerListStore(
                addon.getAddonFolder().resolve("ignored-sellers.txt"));
        SellerListStore favoriteSellers = new SellerListStore(
                addon.getAddonFolder().resolve("favorite-sellers.txt"));

        SettingCategory general = addon.getSettings().category("general", "Основное");
        BooleanSetting scanning = general.toggle("scan-chat", "Сканировать торговый чат", true)
                .description("Искать в чате сообщения о покупке и продаже предметов.");

        MarketScanner scanner = new MarketScanner(store, scanning::get);
        scanner.register();
        registerChatManagerBridgeIfPresent();

        general.key("open-market", "Открыть EvoMarket", KeyBind.keyboard(GLFW.GLFW_KEY_M, KeyBind.CONTROL))
                .description("Открыть окно рынка сочетанием Ctrl+M.")
                .onPress(() -> MarketWindow.open(addon, store, categoryVisibility, ignoredSellers, favoriteSellers, scanner));

        general.button("clear-history", "История", "Очистить историю", () -> {
            store.clear();
            scanner.resetSessionCounters();
        }).description("Удаляет сохранённые объявления и сбрасывает диагностические счётчики на этом компьютере.");

        BooleanSetting updateChecks = general.toggle("check-updates", "Проверять обновления", true)
                .description("Проверять совместимые опубликованные версии EvoMarket на Modrinth при запуске и раз в 12 часов.");
        new ModrinthUpdateChecker(addon, updateChecks::get,
                addon.getAddonFolder().resolve("update-state.properties")).register();

        // Keep the HUD summary intentionally compact. Detailed scanner counters remain
        // available in the market window for troubleshooting, not in the everyday HUD.
        general.widget("market-summary", "Сводка EvoMarket", 200, 36, ctx -> {
            ctx.rect(0, 0, ctx.getWidth(), ctx.getHeight(), 0xD0101018);
            ctx.rect(0, 0, 2, ctx.getHeight(), scanner.isEnabled() ? 0xFF48D597 : 0xFFE45B65);
            ctx.text("EvoMarket · " + store.size() + " объявл.", 6, 3, 0xFFFFFFFF, true);
            ctx.text("Покупка: " + store.count(TradeAction.BUY) + " · Продажа: " + store.count(TradeAction.SELL),
                    6, 19, 0xFFD7DDE7, false);
        }).defaultBackground(true);
    }
    /** Register the optional ChatManager-Core listener without forcing that mod to be installed. */
    private static void registerChatManagerBridgeIfPresent() {
        if (!FabricLoader.getInstance().isModLoaded("chatmanager_core")) return;
        try {
            Class<?> bridge = Class.forName("ru.boss.evomarket.integration.ChatManagerCoreIntegration");
            bridge.getMethod("register").invoke(null);
            System.out.println("[EvoMarket] ChatManager-Core integration registered.");
        } catch (ReflectiveOperationException | LinkageError exception) {
            System.err.println("[EvoMarket] ChatManager-Core detected, but integration registration failed: "
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

}
