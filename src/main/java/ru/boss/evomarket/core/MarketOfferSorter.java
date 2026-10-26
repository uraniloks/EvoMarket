package ru.boss.evomarket.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Sorts offers without comparing prices expressed in different payment currencies. */
public final class MarketOfferSorter {
    private MarketOfferSorter() {}

    public static List<MarketOffer> sorted(List<MarketOffer> input, MarketSortMode mode) {
        List<MarketOffer> offers = new ArrayList<>(input);
        MarketSortMode safeMode = mode == null ? MarketSortMode.NEWEST : mode;
        if (safeMode == MarketSortMode.NEWEST) {
            offers.sort(Comparator.comparing(MarketOffer::timestamp).reversed());
            return offers;
        }

        boolean ascending = safeMode == MarketSortMode.PRICE_ASC;
        offers.sort((left, right) -> {
            int groupCompare = Integer.compare(paymentGroup(left.paymentLabel()), paymentGroup(right.paymentLabel()));
            if (groupCompare != 0) return groupCompare;
            if (left.comparisonPrice() == null && right.comparisonPrice() != null) return 1;
            if (left.comparisonPrice() != null && right.comparisonPrice() == null) return -1;
            if (left.comparisonPrice() != null && right.comparisonPrice() != null) {
                int priceCompare = left.comparisonPrice().compareTo(right.comparisonPrice());
                if (priceCompare != 0) return ascending ? priceCompare : -priceCompare;
            }
            return right.timestamp().compareTo(left.timestamp());
        });
        return offers;
    }

    /** A group is ordered before price comparison; different payment units are never mixed. */
    private static int paymentGroup(String payment) {
        String value = payment == null ? "" : payment.toLowerCase(Locale.ROOT).trim();
        if (value.equals("лк")) return 0;
        if (value.equals("игровая валюта")) return 1;
        if (value.contains("лк") && value.contains("валют")) return 2;
        return 3;
    }
}
