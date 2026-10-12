package ru.boss.evomarket.core;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Map;

/** Small local history. It stores only messages visible in the player's client. */
public final class MarketStore {
    private static final int MAX_HISTORY = 500;
    private static final Duration DEDUP_WINDOW = Duration.ofSeconds(3);

    private final Path file;
    private final ArrayDeque<MarketOffer> offers = new ArrayDeque<>(); // newest first
    private final Map<String, Instant> recentlySeen = new HashMap<>();

    public MarketStore(Path file) {
        this.file = file;
        load();
    }

    public synchronized boolean add(MarketOffer offer) {
        // Never persist non-offers caused by filler after a trade verb (e.g. "если че").
        if (offer == null || MarketCategories.isFillerItem(offer.item())) return false;
        // Suppress repeated reposts of the exact same listing even when they arrive minutes apart.
        String fingerprint = fingerprint(offer);
        if (offers.stream().anyMatch(existing -> fingerprint(existing).equals(fingerprint))) return false;
        String dedupeKey = fingerprint;
        Instant now = offer.timestamp();
        Instant previous = recentlySeen.get(dedupeKey);
        if (previous != null && Duration.between(previous, now).abs().compareTo(DEDUP_WINDOW) <= 0) {
            return false;
        }
        recentlySeen.put(dedupeKey, now);
        recentlySeen.entrySet().removeIf(entry -> Duration.between(entry.getValue(), now).abs().compareTo(Duration.ofMinutes(2)) > 0);

        offers.addFirst(offer);
        while (offers.size() > MAX_HISTORY) offers.removeLast();
        save();
        return true;
    }

    public synchronized List<MarketOffer> latest(int limit) {
        List<MarketOffer> result = new ArrayList<>(Math.min(limit, offers.size()));
        int count = 0;
        for (MarketOffer offer : offers) {
            if (count++ >= limit) break;
            result.add(offer);
        }
        return result;
    }

    public synchronized int size() {
        return offers.size();
    }

    public synchronized long count(TradeAction action) {
        return offers.stream().filter(offer -> offer.action() == action).count();
    }

    public synchronized void clear() {
        offers.clear();
        recentlySeen.clear();
        save();
    }

    private void load() {
        if (!Files.exists(file)) return;
        Set<String> loadedFingerprints = new HashSet<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\t", -1);
                if (parts.length != 6 && parts.length != 7) continue;
                try {
                    Instant time = Instant.parse(decode(parts[0]));
                    TradeAction action = TradeAction.valueOf(decode(parts[1]));
                    String item = decode(parts[2]);
                    if (MarketCategories.isFillerItem(item)) continue;
                    String priceText = decode(parts[3]);
                    BigDecimal price = priceText.isBlank() ? null : new BigDecimal(priceText);
                    String seller = decode(parts[4]);
                    String raw = decode(parts[5]);
                    String payment = parts.length >= 7 ? decode(parts[6]) : MarketCategories.paymentMethod(item, item);
                    if (MarketParser.isNonTradeNoise(raw)) continue;
                    MarketOffer offer = new MarketOffer(time, action, item, price, seller, raw, payment);
                    if (!loadedFingerprints.add(fingerprint(offer))) continue;
                    offers.addLast(offer);
                } catch (RuntimeException ignored) {
                    // Ignore a damaged line; keep the rest of the history.
                }
                if (offers.size() >= MAX_HISTORY) break;
            }
        } catch (IOException exception) {
            System.err.println("[EvoMarket] Не удалось прочитать историю: " + exception.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            List<String> lines = new ArrayList<>(offers.size());
            for (MarketOffer offer : offers) {
                lines.add(encode(offer.timestamp().toString()) + "\t"
                        + encode(offer.action().name()) + "\t"
                        + encode(offer.item()) + "\t"
                        + encode(offer.price() == null ? "" : offer.price().toPlainString()) + "\t"
                        + encode(offer.seller()) + "\t"
                        + encode(offer.rawText()) + "\t"
                        + encode(offer.paymentLabel()));
            }
            Files.write(file, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            System.err.println("[EvoMarket] Не удалось сохранить историю: " + exception.getMessage());
        }
    }

    private static String fingerprint(MarketOffer offer) {
        String raw = offer.rawText() == null ? "" : offer.rawText();
        raw = raw.replaceAll("(?i)§[0-9A-FK-OR]", " ").replaceAll("\\s+", " ").trim();
        Matcher senderPrefix = Pattern.compile("(?iu)^.*?\\[\\d{1,4}]\\s*:\\s*").matcher(raw);
        if (senderPrefix.find()) raw = raw.substring(senderPrefix.end());
        String normalizedRaw = raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        String normalizedSeller = offer.seller() == null ? "" : offer.seller().toLowerCase(Locale.ROOT).trim();
        String normalizedItem = offer.displayItem().toLowerCase(Locale.ROOT).trim();
        String price = offer.price() == null ? "" : offer.price().stripTrailingZeros().toPlainString();
        return normalizedSeller + "|" + offer.action().name() + "|" + normalizedItem + "|"
                + price + "|" + offer.paymentLabel() + "|" + normalizedRaw;
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
