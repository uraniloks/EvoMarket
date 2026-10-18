package ru.boss.evomarket.evoplus;

import ru.boss.evomarket.core.MarketStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/** Regression test for routing both Fabric and ChatManager-Core messages to the addon scanner. */
public final class MarketScannerBridgeSelfTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("evomarket-scanner-bridge");
        MarketStore store = new MarketStore(directory.resolve("history.tsv"));
        AtomicBoolean enabled = new AtomicBoolean(true);
        MarketScanner scanner = new MarketScanner(store, enabled::get);

        // Events arriving before the addon exposes its scanner must not crash the client.
        MarketScanner.receiveFromFabric("CHAT", "pre-init", "tester", Instant.now());
        check(scanner.receivedCount() == 0, "no active scanner should safely ignore early events");

        scanner.register();
        MarketScanner.receiveFromFabric(
                "CHAT",
                "Ⓜ KILLER melida163 [NexCore] [467]: Скупаю РЛБ, 350б — шт. в ЛС /m melida163",
                "melida163",
                Instant.now()
        );

        check(scanner.receivedCount() == 1, "Fabric bridge should increment received counter");
        check(scanner.parsedCount() >= 1, "trade message should be parsed");
        check(scanner.savedCount() >= 1, "trade offer should be stored");
        check(store.size() >= 1, "history should contain the received offer");

        int priorReceived = (int) scanner.receivedCount();
        MarketScanner.receiveFromChatManager(
                "Ⓜ KILLER melida163 [NexCore] [468]: Куплю РКР за 20б",
                "melida163",
                Instant.now()
        );
        check(scanner.receivedCount() == priorReceived + 1,
                "ChatManager-Core/DiamondChat event route should reach the active scanner");
        check(scanner.parsedCount() >= 2, "ChatManager-Core trade message should be parsed");
        check(store.size() >= 2, "ChatManager-Core trade offer should be stored");

        int beforeDisabled = (int) scanner.receivedCount();
        enabled.set(false);
        MarketScanner.receiveFromFabric("CHAT", "Продам КР за 10т", "tester", Instant.now());
        check(scanner.receivedCount() == beforeDisabled + 1, "messages should still be counted when scanning is disabled");
        check(store.size() >= 1, "disabled scanner should not add offers");

        System.out.println("MarketScannerBridgeSelfTest: all checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
