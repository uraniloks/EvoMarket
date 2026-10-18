package ru.boss.evomarket.core;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class MarketOfferSorterSelfTest {
    public static void main(String[] args) {
        MarketOffer lkExpensive = offer("lk-expensive", "ЛК", "100");
        MarketOffer lkCheap = offer("lk-cheap", "ЛК", "10");
        MarketOffer gameTiny = offer("game-tiny", "Игровая валюта", "1");
        MarketOffer unknown = offer("unknown", "", "2");

        List<MarketOffer> asc = MarketOfferSorter.sorted(List.of(lkExpensive, gameTiny, lkCheap, unknown), MarketSortMode.PRICE_ASC);
        check(asc.get(0).seller().equals("lk-cheap"), "LK offers should sort by price inside LK group");
        check(asc.get(1).seller().equals("lk-expensive"), "LK group should stay together");
        check(asc.get(2).seller().equals("game-tiny"), "game currency should be in its own group even with a smaller number");
        check(asc.get(3).seller().equals("unknown"), "unknown payment should be isolated after known currencies");

        List<MarketOffer> desc = MarketOfferSorter.sorted(List.of(lkCheap, lkExpensive), MarketSortMode.PRICE_DESC);
        check(desc.get(0).seller().equals("lk-expensive"), "descending price sort should reverse within the payment group");

        List<MarketOffer> newest = MarketOfferSorter.sorted(List.of(lkCheap, lkExpensive), MarketSortMode.NEWEST);
        check(newest.get(0).timestamp().isAfter(newest.get(1).timestamp()), "newest mode should sort by time");
        System.out.println("MarketOfferSorterSelfTest: all checks passed");
    }

    private static MarketOffer offer(String seller, String payment, String price) {
        long offset = switch (seller) {
            case "lk-cheap" -> 1;
            case "lk-expensive" -> 2;
            case "game-tiny" -> 3;
            default -> 4;
        };
        return new MarketOffer(Instant.EPOCH.plusSeconds(offset), TradeAction.SELL, "тестовый предмет",
                new BigDecimal(price), seller, "Продам тестовый предмет", payment);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
