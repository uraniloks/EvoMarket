package ru.boss.evomarket.core;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;

/** Regression cases found during the audit; no Minecraft runtime required. */
public final class MarketAuditSelfTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static MarketOffer parse(String message) {
        MarketOffer offer = MarketParser.parse(message, "tester", Instant.EPOCH);
        check(offer != null, "Missing offer: " + message);
        return offer;
    }
    public static void main(String[] args) throws Exception {
        for (String verb : List.of("Скуплю", "Скупаем", "Покупаем", "куплю", "скупаю", "продам", "продаю")) {
            check(parse(verb + " РКР за 3б").categories().contains("РКР"), verb);
        }
        for (String space : List.of(" ", "\u00a0", "\u202f", "\u2007")) {
            MarketOffer offer = parse("Продам РКР за 1" + space + "500 LC");
            check(offer.price().compareTo(new BigDecimal("1500")) == 0, "Grouped LC: " + offer);
            check(offer.paymentLabel().equals("ЛК"), "LC label");
        }
        check(parse("Продам РКР 3б").paymentLabel().equals("Игровая валюта"), "Implicit game price");
        check(parse("Продам РКР по 2,5т").price().compareTo(new BigDecimal("2500000000000")) == 0, "Decimal T");
        check(parse("Продам РКР за 999999999999999999999т").price().signum() > 0, "No integer overflow");
        check(MarketParser.parseAll("не продам РКР", null, null).isEmpty(), "Negated offer");
        check(MarketParser.parseAll("не куплю РКР", null, null).isEmpty(), "Negated purchase");
        check(MarketParser.parseAll("куплю " + "а".repeat(17000), null, null).isEmpty(), "Input bound");
        List<MarketOffer> clauses = MarketParser.parseAll("Продам 10т за 100 LC. Куплю РКР за 2б", "tester", Instant.EPOCH);
        check(clauses.size() == 2, "Two clauses");
        check(clauses.get(1).displayItem().equals("РКР"), "Currency must not leak to second item");
        check(!clauses.get(1).categories().contains("Игровая валюта"), "Currency must not leak to second category");
        check(clauses.get(1).tradeContext().contains("2б") && !clauses.get(1).tradeContext().contains("100"), "Local evidence context");
        check(parse("Кейсы тож продаю за 3б").price().compareTo(new BigDecimal("3000000000")) == 0, "Reversed offer price");
        check(MarketParser.parseAll("кто продает РКР?", null, null).isEmpty(), "Question is not a sale");
        check(MarketParser.parseAll("как продать РКР?", null, null).isEmpty(), "How-to is not a sale");
        check(VersionOrder.compare("999999999999999999999.0", "2.0") > 0, "Large version component");
        check(VersionOrder.compare("1.0-999999999999999999999", "1.0-2") > 0, "Large prerelease component");
        check(parse("Продаю валюту 20бк1").comparisonPrice() == null, "Do not sort a ratio as a scalar price");
        check(MarketParser.parseAll("куплю LC", null, null).isEmpty(), "LC alone is payment metadata");
        check(parse("Продам РКР за игровую валюту").paymentLabel().equals("Игровая валюта"), "Unicode word boundaries in currency phrase");
        check(parse("Продам РКР за LC или валюту").paymentLabel().equals("ЛК / игровая валюта"), "Mixed written payment");
        var directory = Files.createTempDirectory("evomarket-audit-");
        var visibility = new CategoryVisibilityStore(directory.resolve("hidden.txt"));
        for (String category : List.of("Игровая валюта", "ЗКСП", "КСП", "Кейс DP", "ЛЛК", "ЗО", "РО", "Мешочки с золотом", "Косметика", "Адский опыт", "Энд опыт", "Ведро опыта", "Ключи от данжей", "Лавовые кристаллы", "Незер. ядра", "Клубки паутины", "Другое")) {
            check(visibility.hide(category), "Hide " + category);
            check(visibility.isHidden(category), "Hidden " + category);
            check(visibility.hiddenCategories().contains(category), "Stable canonical label " + category);
            check(new CategoryVisibilityStore(directory.resolve("hidden.txt")).isHidden(category), "Persist " + category);
            check(visibility.restore(category), "Restore " + category);
            check(!visibility.isHidden(category), "Restored " + category);
        }
        var history = directory.resolve("history.tsv");
        var store = new MarketStore(history);
        long revision = store.revision();
        check(store.addAll(clauses) == 2, "Batch add");
        check(store.revision() > revision, "Revision advances");
        revision = store.revision();
        check(store.addAll(clauses) == 0 && store.revision() == revision, "Duplicates don't refresh UI");
        check(store.latest(-1).isEmpty(), "Negative limit");
        check(new MarketStore(history).latest(2).get(0).tradeContext().equals(clauses.get(1).tradeContext()), "Context roundtrip");
        Files.writeString(history, Files.readString(history) + "broken line\n");
        check(new MarketStore(history).size() == 2, "Damaged row isolation");
        store.clear();
        check(store.revision() > revision && store.size() == 0, "Clear revision");
        check(store.addAll(clauses) == 2, "Clear resets deduplication");
        var many = new java.util.ArrayList<MarketOffer>();
        for (int i = 0; i < 510; i++) many.add(new MarketOffer(Instant.EPOCH.plusSeconds(i), TradeAction.SELL, "РКР", BigDecimal.valueOf(i), "seller", "Продам РКР " + i));
        store.addAll(many);
        check(store.size() == 500, "History cap");
        check(new MarketStore(history).size() == 500, "Bounded reload");
        var relative = java.nio.file.Path.of("audit-history-" + java.util.UUID.randomUUID() + ".tsv");
        try {
            var relativeStore = new MarketStore(relative);
            check(relativeStore.add(clauses.getFirst()), "History with a filename without parent");
            check(new MarketStore(relative).size() == 1, "Relative history persisted");
        } finally { Files.deleteIfExists(relative); }
        System.out.println("MarketAuditSelfTest: " + checks + " checks passed");
    }
}
