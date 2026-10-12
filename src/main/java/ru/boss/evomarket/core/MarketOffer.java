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
        String paymentMethod
) {
    /** Backwards-compatible constructor for older callers and saved history. */
    public MarketOffer(Instant timestamp, TradeAction action, String item,
                       BigDecimal price, String seller, String rawText) {
        this(timestamp, action, item, price, seller, rawText,
                MarketCategories.paymentMethod(item, item));
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
