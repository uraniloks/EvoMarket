package ru.boss.evomarket.evoplus;

import ru.boss.evomarket.core.MarketOffer;
import ru.boss.evomarket.core.MarketParser;
import ru.boss.evomarket.core.MarketStore;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * Reads the normal Fabric chat events and their canceled variants. Some chat
 * replacement mods cancel the original HUD message and render it in their own
 * windows; the canceled events let EvoMarket still inspect that received text.
 */
public final class MarketScanner {
    private static volatile MarketScanner activeScanner;

    private final MarketStore store;
    private final BooleanSupplier enabled;

    private final AtomicLong received = new AtomicLong();
    private final AtomicLong parsed = new AtomicLong();
    private final AtomicLong saved = new AtomicLong();
    private final AtomicLong ignored = new AtomicLong();

    private volatile String lastChannel = "ожидание";
    private volatile String lastMessage = "пока сообщений не получено";
    private volatile String lastResult = "открой чат на сервере, чтобы проверить сканер";

    public MarketScanner(MarketStore store, BooleanSupplier enabled) {
        this.store = store;
        this.enabled = enabled;
    }

    /**
     * Publishes this scanner instance for the regular Fabric client entrypoint.
     * Receive-event subscriptions are deliberately registered by
     * EvoMarketClientInitializer, not by the EvoPlus addon lifecycle. That keeps
     * event registration in Fabric's normal client classloader/lifecycle.
     */
    public void register() {
        activeScanner = this;
        lastChannel = "слушатель активен";
        lastResult = "ожидание сообщений Fabric client events";
        System.out.println("[EvoMarket] Scanner instance attached; waiting for client chat events.");
    }

    /** Called by the Fabric client entrypoint for each received chat/game message. */
    public static void receiveFromFabric(String channel, String raw, String sender, Instant timestamp) {
        MarketScanner scanner = activeScanner;
        if (scanner != null) {
            scanner.handle(channel, raw, sender, timestamp);
        }
    }

    /** Receives the same raw pre-channel message from ChatManager-Core / DiamondChat. */
    public static void receiveFromChatManager(String raw, String sender, Instant timestamp) {
        MarketScanner scanner = activeScanner;
        if (scanner != null) {
            scanner.handle("CHATMANAGER", raw, sender, timestamp);
        }
    }

    private void handle(String channel, String raw, String sender, Instant timestamp) {
        received.incrementAndGet();
        lastChannel = channel;
        lastMessage = compact(raw, 116);

        if (!enabled.getAsBoolean()) {
            lastResult = "сканирование выключено в настройках EvoMarket";
            return;
        }

        try {
            List<MarketOffer> offers = MarketParser.parseAll(raw, sender,
                    timestamp == null ? Instant.now() : timestamp);
            if (offers.isEmpty()) {
                ignored.incrementAndGet();
                lastResult = "сообщение получено, но торговое объявление не распознано";
                return;
            }

            parsed.addAndGet(offers.size());
            int added = store.addAll(offers);
            saved.addAndGet(added);
            lastResult = added > 0
                    ? "добавлено объявлений: " + added + " из " + offers.size()
                    : "повтор объявления пропущен — оно уже есть в истории";
        } catch (RuntimeException exception) {
            ignored.incrementAndGet();
            lastResult = "ошибка разбора сообщения: " + exception.getClass().getSimpleName();
            System.err.println("[EvoMarket] Ошибка обработки сообщения: " + exception.getMessage());
        }
    }

    private static String compact(String value, int max) {
        if (value == null || value.isBlank()) return "(пустое сообщение)";
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= max) return normalized;
        return normalized.substring(0, Math.max(0, max - 1)) + "…";
    }

    public long receivedCount() { return received.get(); }
    public long parsedCount() { return parsed.get(); }
    public long savedCount() { return saved.get(); }
    public long ignoredCount() { return ignored.get(); }
    public String lastChannel() { return lastChannel; }
    public String lastMessage() { return lastMessage; }
    public String lastResult() { return lastResult; }
    public boolean isEnabled() { return enabled.getAsBoolean(); }

    /** Compact status shown in the HUD and market window to diagnose missed offers. */
    public String statusLine() {
        return "Сканер: " + (isEnabled() ? "ВКЛ" : "ВЫКЛ")
                + " · сообщений: " + receivedCount()
                + " · распознано: " + parsedCount()
                + " · добавлено: " + savedCount();
    }

    /** History clear also resets session diagnostics, so new activity is easy to verify. */
    public void resetSessionCounters() {
        received.set(0);
        parsed.set(0);
        saved.set(0);
        ignored.set(0);
        lastChannel = "ожидание";
        lastMessage = "пока сообщений не получено";
        lastResult = "открой чат на сервере, чтобы проверить сканер";
    }
}
