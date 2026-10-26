package ru.boss.evomarket.core;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record MarketOffer(
        Instant timestamp,
        TradeAction action,
        String item,
        BigDecimal price,
        String seller,
        String rawText,
        String paymentMethod,
        String tradeContext
) {
    private static final java.util.regex.Pattern EXCHANGE_RATE = java.util.regex.Pattern.compile(
            "(?iu)\\d+(?:[.,]\\d+)?\\s*[тtбbв]?\\s*[кk]\\s*\\d+");

    public MarketOffer {
        java.util.Objects.requireNonNull(timestamp, "timestamp");
        java.util.Objects.requireNonNull(action, "action");
        item = item == null ? "" : item;
        seller = seller == null || seller.isBlank() ? "неизвестно" : seller;
        rawText = rawText == null ? "" : rawText;
        tradeContext = tradeContext == null ? rawText : tradeContext;
    }

    public MarketOffer(Instant timestamp, TradeAction action, String item,
                       BigDecimal price, String seller, String rawText, String paymentMethod) {
        this(timestamp, action, item, price, seller, rawText, paymentMethod, rawText);
    }

    /** Backwards-compatible constructor for older callers and saved history. */
    public MarketOffer(Instant timestamp, TradeAction action, String item,
                       BigDecimal price, String seller, String rawText) {
        this(timestamp, action, item, price, seller, rawText,
                MarketCategories.paymentMethod(item, item));
    }

    /** Mixed payment quotes and exchange ratios are not scalar prices. */
    public BigDecimal comparisonPrice() {
        if (paymentLabel().equals("ЛК / игровая валюта")) return null;
        if (EXCHANGE_RATE.matcher(tradeContext).find()) return null;
        return price;
    }

    public boolean hasPrice() {
        return price != null;
    }

    /** Friendly display name, including compatibility for older saved currency offers. */
    public String displayItem() {
        return MarketCategories.displayItem(item, rawText);
    }

    /** Category is calculated from the existing item/raw text, so old history remains compatible. */
    public String category() {
        return MarketCategories.categoryFor(item, rawText);
    }

    public List<String> categories() {
        return MarketCategories.categoriesFor(item, rawText);
    }

    public String paymentLabel() {
        return paymentMethod == null ? "" : paymentMethod;
    }
}
