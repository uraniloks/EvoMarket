package ru.boss.evomarket.evoplus;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
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

    public void register() {
        ClientReceiveMessageEvents.CHAT.register((message, playerChatMessage, sender, boundChatType, timestamp) ->
                handle("CHAT", message.getString(), senderName(sender), timestamp));

        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
                handle(overlay ? "GAME / actionbar" : "GAME", message.getString(), null, Instant.now()));

        // Other client mods (chat replacements, filters, channel windows) can
        // cancel vanilla display after receiving a message. Scan those too.
        ClientReceiveMessageEvents.CHAT_CANCELED.register((message, playerChatMessage, sender, boundChatType, timestamp) ->
                handle("CHAT_CANCELED", message.getString(), senderName(sender), timestamp));

        ClientReceiveMessageEvents.GAME_CANCELED.register((message, overlay) ->
                handle(overlay ? "GAME_CANCELED / actionbar" : "GAME_CANCELED", message.getString(), null, Instant.now()));
    }

    private void handle(String channel, String raw, String sender, Instant timestamp) {
        received.incrementAndGet();
        lastChannel = channel;
        lastMessage = compact(raw, 116);

        if (!enabled.getAsBoolean()) {
            lastResult = "сканирование выключено в настройках EvoMarket";
            return;
        }

        List<MarketOffer> offers = MarketParser.parseAll(raw, sender,
                timestamp == null ? Instant.now() : timestamp);
        if (offers.isEmpty()) {
            ignored.incrementAndGet();
            lastResult = "сообщение получено, но ключевое слово покупки/продажи не найдено";
            return;
        }

        parsed.addAndGet(offers.size());
        int added = 0;
        for (MarketOffer offer : offers) {
            if (store.add(offer)) {
                saved.incrementAndGet();
                added++;
            }
        }
        lastResult = added > 0
                ? "добавлено объявлений: " + added + " из " + offers.size()
                : "объявление уже было получено — повтор пропущен";
    }

    private static String senderName(GameProfile sender) {
        return sender == null ? null : sender.name();
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
}
