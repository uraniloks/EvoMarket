package ru.boss.evomarket.core;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parser for free-form Russian trade messages used in PrisonEvo. */
public final class MarketParser {
    private static final Pattern INTENT = Pattern.compile(
            "(?iu)(?<![\\p{L}])(?:(?<buy>куплю|скупаю|скупает|скупают|покупаю|покупает|покупают|приобрету)|"
                    + "(?<sell>продам|продаю|продаем|продаём|продает|продаёт|продают|продажа|продать))(?![\\p{L}])"
    );

    // T/t = trillion, B/b and В/в = billion, as confirmed by the user.
    private static final Pattern PRICE_TOKEN = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])(\\d+(?:[.,]\\d+)?)\\s*"
                    + "(триллион(?:а|ов)?|трлн|т|t|б|b|в)(?![\\p{L}])"
    );
    // LC (ЛК) is a separate payment currency, not an item/category. Only treat it as
    // the price when the amount is introduced as a price, e.g. "за 299 лк" or "по 50 лк".
    private static final Pattern LK_PRICE = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])(?:за|по(?:\\s+цене)?|цена\\s*:?|стоимость\\s*:?|от)\\s*"
                    + "(\\d+(?:[.,]\\d+)?)\\s*(?:лк|личн\\p{L}*\\s+кабинет\\p{L}*)(?![\\p{L}])"
    );
    private static final Pattern SERVER_NAME = Pattern.compile(
            "([A-Za-z0-9_]{3,16})\\s+\\[[^\\]]+\\]\\s+\\[\\d{1,4}]\\s*:"
    );
    // Strip the server-provided sender/rank prefix before looking for trade verbs.
    // This prevents a word in decorated metadata from accidentally creating a listing.
    private static final Pattern CHAT_BODY_PREFIX = Pattern.compile("(?s)^.*?\\[\\d{1,4}]\\s*:\\s*");
    private static final Pattern COLOR_CODE = Pattern.compile("(?i)§[0-9A-FK-OR]");
    // Looking for a clan is social/recruitment chat, not a market purchase.
    private static final Pattern CLAN_SEARCH = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])(?:ищу|ищем|нужен|нужна|нужны|хочу\\s+(?:вступить\\s+в|в))\\s+.{0,100}?(?<![\\p{L}\\p{N}])клан\\p{L}*(?![\\p{L}\\p{N}])"
    );
    // Compact LK exchange quotes such as "1к15 валюту" or "1k15 валюту".
    // Only treat this notation as a currency rate when the traded-currency word follows it.
    private static final Pattern COMPACT_LK_CURRENCY_RATE = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])(\\d+(?:[.,]\\d+)?)\\s*[кk]\\s*(\\d+(?:[.,]\\d+)?)(?![\\p{L}\\p{N}])(?=\\s+(?:(?:игров\\p{L}*|внутриигров\\p{L}*)\\s+)?валют\\p{L}*)"
    );
    // Messages describing a helper/mod rather than an actual trade must not be
    // interpreted as offers just because they mention the words "buy" or "sell".
    private static final Pattern NON_TRADE_PROMO = Pattern.compile(
            "(?iu)(?:анализиру\\p{L}*\\s+сообщен\\p{L}*"
                    + "|добавля\\p{L}*\\s+в\\s+удобн\\p{L}*\\s+панел"
                    + "|удобн\\p{L}*\\s+панел"
                    + "|не\\s+надо\\s+следить\\s+за\\s+чат"
                    + "|можно\\s+отойти.{0,80}вернут"
                    + "|все?\\s+прекрасно\\s+видно"
                    + "|продать\\s+что[- ]?то\\s+или\\s+купить"
                    + "|купить\\s+что[- ]?то\\s+или\\s+продать"
                    + "|фото\\s+своих\\s+ножек"
                    + "|фотк\\p{L}*\\s+своих\\s+ножек"
                    + "|кто\\s+(?:за|по)\\s+(?:лк|личн\\p{L}*\\s+кабинет\\p{L}*)\\s+много"
                    + "|в\\s+целом\\s+все?\\s+посох\\p{L}*"
                    + "|посох\\p{L}*.{0,80}\\bнельзя\\b.{0,50}(?:купить|продать))"
    );
    private static final Pattern GENERIC_ITEM = Pattern.compile(
            "(?iu)^(?:что[- ]?то(?:\\s+или)?|что[- ]?либо|что[- ]?нибудь|всё|все|товар|предмет)(?:\\s+.*)?$"
    );
    private static final Pattern CURRENCY_AMOUNT_PREFIX = Pattern.compile(
            "(?iu)^\\s*\\d+(?:[.,]\\d+)?\\s*(?:триллион(?:а|ов)?|трлн|т|t|б|b|в)(?![\\p{L}\\p{N}])"
    );
    private static final Pattern CURRENCY_RATE = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])(\\d+(?:[.,]\\d+)?)\\s*([тtбbв])\\s*к\\s*(\\d+(?:[.,]\\d+)?)(?![\\p{L}\\p{N}])"
    );
    private static final Pattern FILLER_ONLY = Pattern.compile(
            "(?iu)^[\\s\\p{Punct}]*(?:если\\s+(?:ч[её]|что)|пишите?\\s+(?:в\\s+)?(?:лс|личку)|писать\\s+(?:в\\s+)?(?:лс|личку)|(?:в\\s+)?(?:лс|личку)|больше\\s*[-—]?\\s*больше)[\\s\\p{Punct}]*$"
    );
    // Sometimes a player writes an item first and puts the verb after it: "кейсы тож продаю".
    // These are recovered as a separate offer instead of treating the trailing "если че" as an item.
    private static final Pattern REVERSED_INTENT = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])(?<item>[\\p{L}][\\p{L}\\p{N} _/-]{0,40}?)\\s+(?:тож(?:е)?|также)\\s+(?:(?<buy>куплю|скупаю|скупает|скупают|покупаю|покупает|покупают|приобрету)|(?<sell>продам|продаю|продаем|продаём|продает|продаёт|продают|продажа|продать))(?![\\p{L}])"
    );

    private MarketParser() {}

    /** Backwards-compatible single-result helper. */
    public static MarketOffer parse(String input, String senderHint, Instant timestamp) {
        List<MarketOffer> offers = parseAll(input, senderHint, timestamp);
        return offers.isEmpty() ? null : offers.get(0);
    }

    /** Parses every explicit buy/sell clause in a message, preserving the original message as evidence. */
    public static List<MarketOffer> parseAll(String input, String senderHint, Instant timestamp) {
        if (input == null || input.isBlank()) return List.of();

        String text = COLOR_CODE.matcher(input).replaceAll("").replace('\u00a0', ' ')
                .replaceAll("\\s+", " ").trim();
        String body = extractMessageBody(text);
        if (isNonTradeNoise(text) || isNonTradeNoise(body) || CLAN_SEARCH.matcher(body).find()) return List.of();
        Matcher intents = INTENT.matcher(body);
        List<IntentMark> marks = new ArrayList<>();
        while (intents.find()) {
            TradeAction action = intents.group("buy") != null ? TradeAction.BUY : TradeAction.SELL;
            marks.add(new IntentMark(intents.start(), intents.end(), action));
        }
        if (marks.isEmpty()) return List.of();

        String seller = senderHint == null || senderHint.isBlank() ? extractSeller(text) : senderHint.trim();
        if (seller.isBlank()) seller = "неизвестно";
        Instant safeTimestamp = timestamp == null ? Instant.now() : timestamp;
        List<MarketOffer> offers = new ArrayList<>();

        for (int i = 0; i < marks.size(); i++) {
            IntentMark intent = marks.get(i);
            int segmentEnd = i + 1 < marks.size() ? marks.get(i + 1).start : body.length();
            if (segmentEnd < intent.end) continue;
            String segment = body.substring(intent.end, segmentEnd).trim();
            if (segment.isBlank() || isFillerOnly(segment)) continue;

            PriceResult parsedPrice = findPrice(segment, 0);
            String item;
            boolean currencyLot = startsWithCurrencyAmount(segment);
            // If a clause begins with a T/B amount, that amount is the game-currency lot,
            // while a later "за N лк" is the payment price.
            if (currencyLot) {
                item = "Игровая валюта";
            } else {
                int itemEnd = parsedPrice == null ? segment.length() : parsedPrice.start;
                item = cleanItem(segment.substring(0, Math.max(0, itemEnd)));
            }
            if (item.length() > 80) item = item.substring(0, 77).trim() + "...";

            // Contact-only tails ("в ЛС", "/m player"), filler, and generic prose
            // such as "что-то или купить" are not market listings.
            if (item.isBlank()) {
                if (currencyLot) item = "Игровая валюта";
                else continue;
            }
            if (GENERIC_ITEM.matcher(item.trim()).matches() || isFillerOnly(item)
                    || MarketCategories.isFillerItem(item)
                    || MarketCategories.isNonTradableItem(item)) continue;
            String paymentMethod = MarketCategories.paymentMethod(item, segment);
            offers.add(new MarketOffer(safeTimestamp, intent.action, item,
                    parsedPrice == null ? null : parsedPrice.amount, seller, text, paymentMethod));
        }

        // Recover reversed additional offers such as "кейсы тож продаю" while ignoring
        // the filler clause "если че" that can follow the verb.
        Matcher reversed = REVERSED_INTENT.matcher(body);
        while (reversed.find()) {
            String item = cleanItem(reversed.group("item")
                    .replaceAll("(?iu)\\s+(?:тож(?:е)?|также)\\s*$", ""));
            if (item.isBlank() || isFillerOnly(item) || MarketCategories.isFillerItem(item)
                    || MarketCategories.isNonTradableItem(item)) continue;
            TradeAction action = reversed.group("buy") != null ? TradeAction.BUY : TradeAction.SELL;
            String finalItem = item;
            boolean duplicate = offers.stream().anyMatch(existing -> existing.action() == action
                    && existing.displayItem().equalsIgnoreCase(MarketCategories.displayItem(finalItem, text))
                    && existing.price() == null);
            if (!duplicate) {
                int contextEnd = body.length();
                Matcher nextIntent = INTENT.matcher(body);
                nextIntent.region(reversed.end(), body.length());
                if (nextIntent.find()) contextEnd = nextIntent.start();
                String reverseContext = body.substring(reversed.start(), contextEnd);
                offers.add(new MarketOffer(safeTimestamp, action, item, null, seller, text,
                        MarketCategories.paymentMethod(item, reverseContext)));
            }
        }
        return List.copyOf(offers);
    }

    private static String extractMessageBody(String fullText) {
        if (fullText == null || fullText.isBlank()) return "";
        Matcher header = CHAT_BODY_PREFIX.matcher(fullText);
        return header.find() ? fullText.substring(header.end()).trim() : fullText.trim();
    }

    private static boolean isFillerOnly(String segment) {
        if (segment == null || segment.isBlank()) return true;
        return FILLER_ONLY.matcher(segment.trim()).matches();
    }

    private static PriceResult findPrice(String text, int searchFrom) {
        Matcher lkPrice = LK_PRICE.matcher(text);
        lkPrice.region(Math.max(0, searchFrom), text.length());
        if (lkPrice.find()) {
            return new PriceResult(lkPrice.start(), lkPrice.end(),
                    new BigDecimal(lkPrice.group(1).replace(',', '.')));
        }

        // Compact exchange-rate notation used in chat, e.g. 20бк1 or 1тк10.
        // Keep the first amount as the numeric value, and preserve the ratio in rawText
        // so the UI can show the full "20 B к 1" rate instead of pretending it's a flat price.
        Matcher rate = CURRENCY_RATE.matcher(text);
        rate.region(Math.max(0, searchFrom), text.length());
        if (rate.find()) {
            BigDecimal firstPart = tokenValue(rate.group(1), rate.group(2));
            return new PriceResult(rate.start(), rate.end(), firstPart);
        }

        Matcher compactLkRate = COMPACT_LK_CURRENCY_RATE.matcher(text);
        compactLkRate.region(Math.max(0, searchFrom), text.length());
        if (compactLkRate.find()) {
            // The numeric price field is only a sortable placeholder; the UI renders
            // the full exchange rate from rawText, including the LK payment label.
            return new PriceResult(compactLkRate.start(), compactLkRate.end(),
                    new BigDecimal(compactLkRate.group(1).replace(',', '.')));
        }

        Matcher matcher = PRICE_TOKEN.matcher(text);
        matcher.region(Math.max(0, searchFrom), text.length());
        if (!matcher.find()) return null;

        int start = matcher.start();
        int end = matcher.end();
        BigDecimal total = tokenValue(matcher.group(1), matcher.group(2));

        // Allow compound prices such as "16т-5б" (= 16T + 5B), but do not
        // combine prices from separate items in the same message.
        while (matcher.find()) {
            String gap = text.substring(end, matcher.start());
            if (!gap.matches("[\\s,;:+\\-–—()\\[\\]{}]*")) break;
            total = total.add(tokenValue(matcher.group(1), matcher.group(2)));
            end = matcher.end();
        }
        return new PriceResult(start, end, total);
    }

    private static BigDecimal tokenValue(String number, String suffix) {
        BigDecimal value = new BigDecimal(number.replace(',', '.'));
        String unit = suffix.toLowerCase(Locale.ROOT);
        if (unit.startsWith("т") || unit.startsWith("t")) {
            return value.multiply(new BigDecimal("1000000000000"));
        }
        return value.multiply(new BigDecimal("1000000000"));
    }

    private static boolean startsWithCurrencyAmount(String input) {
        String value = input == null ? "" : input;
        Matcher compactLkRate = COMPACT_LK_CURRENCY_RATE.matcher(value);
        return CURRENCY_AMOUNT_PREFIX.matcher(value).find()
                || CURRENCY_RATE.matcher(value).lookingAt()
                || (compactLkRate.find() && compactLkRate.start() == 0);
    }

    private static String cleanItem(String input) {
        String value = input.trim();
        value = value.replaceAll("(?iu)(?:[,;.!]?\\s*)(?:(?:пишите?|писать)\\s+)?(?:в\\s+)?(?:лс|личку|личные сообщения)\\s*[!.!?]*$", "");
        value = value.replaceAll("(?iu)\\s*/\\s*(?:m|msg|tell|w)\\s+\\S+.*$", "");
        value = value.replaceAll("(?iu)\\s+(?:подробност\\p{L}*|если\\s+ч[её]|больше\\s*[-—]?\\s*больше)\\s*[!.!?]*$", "");
        value = value.replaceAll("(?iu)\\s+(?:за|по цене|по|цена|от)\\s*$", "");
        value = value.replaceAll("(?iu)\\s+(?:за|по цене)\\s+\\S+\\s*$", "");
        value = value.replaceAll("^[\\s:;,.()\\[\\]{}—–-]+", "");
        value = value.replaceAll("[\\s:;,.!?()\\[\\]{}—–-]+$", "");
        return value.trim();
    }

    /** Returns true for informational/promotional messages that only discuss trading. */
    public static boolean isNonTradeNoise(String text) {
        if (text == null || text.isBlank()) return false;
        String cleaned = COLOR_CODE.matcher(text).replaceAll(" ").replaceAll("\\s+", " ").trim();
        return NON_TRADE_PROMO.matcher(cleaned).find();
    }

    private static String extractSeller(String text) {
        Matcher matcher = SERVER_NAME.matcher(text);
        if (matcher.find()) return matcher.group(1);

        int colon = text.indexOf(':');
        if (colon > 0) {
            String prefix = text.substring(0, colon).trim();
            if (prefix.length() > 48) prefix = prefix.substring(Math.max(0, prefix.length() - 48));
            return prefix;
        }
        return "неизвестно";
    }

    private record IntentMark(int start, int end, TradeAction action) {}
    private record PriceResult(int start, int end, BigDecimal amount) {}
}
