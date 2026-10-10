package ru.boss.evomarket.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Canonical item categories inferred from DiamondWorld's free-form trade chat. */
public final class MarketCategories {
    public static final String ALL = "Все категории";
    private static final String UNKNOWN = "предмет не распознан";

    private static final List<String> ORDER = List.of(
            "Игровая валюта",
            "ЗКСП", "КСП", "ЗАКР", "РАКР", "ЗЭКР", "РЭКР", "ЗКР", "РКР", "АКР", "ЭКР", "КР", "Кейс DP", "АРКР", "АДКР",
            "АСЛМ", "ЭСЛМ", "СЛМ", "ЛЛБ", "РЛБ", "ЛБ", "ЗО", "РО", "Души", "Кейсы", "Косметика", "Питомцы",
            "Лег апгрейд", "Опыт", "Адский опыт", "Энд опыт", "Доски", "Дерево", "Колбы", "Руны", "АСПЛ", "Орешки", "Пепел", "Незер. ядра", "Клубки паутины", "Другое"
    );

    private static final Pattern CURRENCY_PREFIX = Pattern.compile(
            "(?iu)^(?:(?:игров\\p{L}*|внутриигров\\p{L}*)\\s+)?валют\\p{L}*"
    );
    // Legacy chat shorthand: "валку к" refers to game currency being traded for LK, not a standalone category.
    private static final Pattern CURRENCY_ALIAS = Pattern.compile(
            "(?iu)^(?:валк\\p{L}*\\s+к|валют\\p{L}*\\s+к)(?:\\b.*)?$"
    );
    private static final Pattern CURRENCY_WORD_ALIAS = Pattern.compile(
            "(?iu)^(?:валк\\p{L}*|валют\\p{L}*)$"
    );
    // Natural chat phrases like "очень много валки по курсу 1к500м" are currency listings.
    private static final Pattern CURRENCY_BULK_ALIAS = Pattern.compile(
            "(?iu)^\\s*(?:(?:очень|реально|крайне)\\s+)?много\\s+валк\\p{L}*(?:\\s|$).*$"
    );
    private static final Pattern FILLER_ITEM = Pattern.compile(
            "(?iu)^[\\s\\p{Punct}]*(?:если\\s+(?:ч[её]|что)|пишите?\\s+(?:в\\s+)?(?:лс|личку)|писать\\s+(?:в\\s+)?(?:лс|личку)|(?:в\\s+)?(?:лс|личку)|больше\\s*[-—]?\\s*больше)[\\s\\p{Punct}]*$"
    );
    private static final Pattern INGAME_CURRENCY = Pattern.compile(
            "(?iu)^(?:внутриигров\\p{L}*(?:\\s+валют\\p{L}*)?|игров\\p{L}*\\s+валют\\p{L}*|курс\\s+валют\\p{L}*)"
    );
    private static final Pattern COMPACT_CURRENCY_RATE = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])(\\d+(?:[.,]\\d+)?)\\s*([тtбbв])\\s*к\\s*(\\d+(?:[.,]\\d+)?)(?![\\p{L}\\p{N}])"
    );
    // LK exchange-rate shorthand is only recognized when a currency word follows it,
    // preventing an arbitrary "1k15" in an item name from becoming a currency offer.
    private static final Pattern COMPACT_LK_CURRENCY_RATE = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])(\\d+(?:[.,]\\d+)?)\\s*[кk]\\s*(\\d+(?:[.,]\\d+)?)(?![\\p{L}\\p{N}])(?=\\s+(?:(?:игров\\p{L}*|внутриигров\\p{L}*)\\s+)?валют\\p{L}*)"
    );
    private static final Pattern CURRENCY_AMOUNT = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])\\d+(?:[.,]\\d+)?\\s*(?:триллион(?:а|ов)?|трлн|т|t|б|b|в)(?![\\p{L}])"
    );
    private static final Pattern LK_MARKER = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])(?:лк|личн\\p{L}*\\s+кабинет\\p{L}*)(?![\\p{L}\\p{N}])"
    );
    private static final Pattern GAME_CURRENCY_PAYMENT_WORD = Pattern.compile(
            "(?iu)(?:\\b(?:за|по|обмен\\s+(?:на|за)|оплата\\s*:?)\\s*(?:игров\\p{L}*\\s+|внутриигров\\p{L}*\\s+)?валют\\p{L}*|(?:/|\\bили\\s+)\\s*(?:игров\\p{L}*\\s+|внутриигров\\p{L}*\\s+)?валют\\p{L}*)"
    );
    private static final Pattern GAME_CURRENCY_AMOUNT_PAYMENT = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])за\\s*\\d+(?:[.,]\\d+)?\\s*(?:триллион(?:а|ов)?|трлн|т|t|б|b|в)(?![\\p{L}])"
    );
    private static final Pattern BULK = Pattern.compile("(?iu)^\\s*оптом(?:\\s|$)");
    private static final Pattern GOLD_NUTS = Pattern.compile(
            "(?iu)(?:золот\\p{L}*\\s+(?:орех\\p{L}*|орешк\\p{L}*)|(?<![\\p{L}\\p{N}])зо(?![\\p{L}\\p{N}]))"
    );
    private static final Pattern RARE_NUTS = Pattern.compile(
            "(?iu)(?:редк\\p{L}*\\s+(?:орех\\p{L}*|орешк\\p{L}*)|(?<![\\p{L}\\p{N}])ро(?![\\p{L}\\p{N}]))"
    );
    private static final Pattern LEGENDARY_LB = Pattern.compile(
            "(?iu)(?:легендарн\\p{L}*\\s+лаки\\s*[- ]?\\s*блок\\p{L}*|(?<![\\p{L}\\p{N}])ллб(?![\\p{L}\\p{N}]))"
    );
    private static final Pattern RARE_LB = Pattern.compile(
            "(?iu)(?:редк\\p{L}*\\s+лаки\\s*[- ]?\\s*блок\\p{L}*|(?<![\\p{L}\\p{N}])рлб(?![\\p{L}\\p{N}]))"
    );
    private static final Pattern PET_CASE = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])ксп(?:ы|а|ов|ами|ам)?(?![\\p{L}\\p{N}])|кейс\\p{L}*\\s+с\\s+питомц\\p{L}*"
    );
    private static final Pattern GOLDEN_PET_CASE_FULL = Pattern.compile("(?iu)золот\\p{L}*\\s+кейс\\p{L}*\\s+с\\s+питомц\\p{L}*");
    private static final Pattern CASE_DP_FULL = Pattern.compile("(?iu)(?:кейс\\p{L}*\\s*[- ]?\\s*dp|dp\\s*[- ]?\\s*кейс\\p{L}*)");
    private static final Pattern RARE_HELL_RUNE_CASE_FULL = Pattern.compile("(?iu)редк\\p{L}*\\s+адск\\p{L}*\\s+кейс\\p{L}*\\s+(?:с\\s+)?рун\\p{L}*");
    private static final Pattern GOLDEN_HELL_RUNE_CASE_FULL = Pattern.compile("(?iu)золот\\p{L}*\\s+адск\\p{L}*\\s+кейс\\p{L}*\\s+(?:с\\s+)?рун\\p{L}*");
    private static final Pattern RARE_ENDER_RUNE_CASE_FULL = Pattern.compile("(?iu)редк\\p{L}*\\s+эндер\\p{L}*\\s+кейс\\p{L}*\\s+(?:с\\s+)?рун\\p{L}*");
    private static final Pattern GOLDEN_ENDER_RUNE_CASE_FULL = Pattern.compile("(?iu)золот\\p{L}*\\s+эндер\\p{L}*\\s+кейс\\p{L}*\\s+(?:с\\s+)?рун\\p{L}*");
    private static final Pattern RARE_RUNE_CASE_FULL = Pattern.compile("(?iu)редк\\p{L}*\\s+кейс\\p{L}*\\s+(?:с\\s+)?рун\\p{L}*");
    private static final Pattern GOLDEN_RUNE_CASE_FULL = Pattern.compile("(?iu)золот\\p{L}*\\s+кейс\\p{L}*\\s+(?:с\\s+)?рун\\p{L}*");
    private static final Pattern HELL_RUNE_CASE_FULL = Pattern.compile("(?iu)адск\\p{L}*\\s+кейс\\p{L}*\\s+(?:с\\s+)?рун\\p{L}*");
    private static final Pattern ENDER_RUNE_CASE_FULL = Pattern.compile("(?iu)эндер\\p{L}*\\s+кейс\\p{L}*\\s+(?:с\\s+)?рун\\p{L}*");
    private static final Pattern RUNE_CASE_FULL = Pattern.compile("(?iu)кейс\\p{L}*\\s+(?:с\\s+)?рун\\p{L}*");
    private static final Pattern HELL_BAT_TREASURE_FULL = Pattern.compile("(?iu)адск\\p{L}*\\s+сокровищ\\p{L}*\\s+летуч\\p{L}*\\s+мыш\\p{L}*");
    private static final Pattern ENDER_BAT_TREASURE_FULL = Pattern.compile("(?iu)эндер\\p{L}*\\s+сокровищ\\p{L}*\\s+летуч\\p{L}*\\s+мыш\\p{L}*");
    private static final Pattern BAT_TREASURE_FULL = Pattern.compile("(?iu)сокровищ\\p{L}*\\s+летуч\\p{L}*\\s+мыш\\p{L}*");

    // Abbreviations are exact tokens, so ZAKR doesn't also become AKR, etc.
    private static final Pattern ZKSP_CODE = code("зксп");
    private static final Pattern RAKR_CODE = code("ракр");
    private static final Pattern ZAKR_CODE = code("закр");
    private static final Pattern REKR_CODE = code("рэкр");
    private static final Pattern ZEKR_CODE = code("зэкр");
    private static final Pattern ZKR_CODE = code("зкр");
    private static final Pattern EKR_CODE = code("экр");
    private static final Pattern RKR_CODE = code("ркр");
    private static final Pattern KSP_CODE = code("ксп");
    private static final Pattern KR_CODE = code("кр");
    private static final Pattern SLM_CODE = code("слм");
    private static final Pattern ASLM_CODE = code("аслм");
    private static final Pattern ESLM_CODE = code("эслм");

    private static final Pattern ARKR = code("аркр");
    private static final Pattern AKR = code("акр");
    private static final Pattern RKR = RKR_CODE;
    private static final Pattern ADKR = code("адкр");
    private static final Pattern KR = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])(?:кр|кров\\p{L}*)(?![\\p{L}\\p{N}])");
    private static final Pattern LEG_UPGRADE = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])(?:лег|легендарн\\p{L}*)\\s*[- ]?\\s*(?:ап{1,2}|апгре[йи]д\\p{L}*)(?![\\p{L}\\p{N}])"
    );
    private static final Pattern AXOLOTL = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])(?:аксолотл|асколотл)\\p{L}*(?![\\p{L}\\p{N}])"
    );
    private static final Pattern BOARDS = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])доск\\p{L}*(?![\\p{L}\\p{N}])");
    // These are item names used as cosmetic listings on this server, even if the seller
    // doesn't literally write the word "косметика".
    private static final Pattern COSMETIC_MARKERS = Pattern.compile(
            "(?iu)(?:косметик\\p{L}*|демоническ\\p{L}*\\s+крыл\\p{L}*|(?<![\\p{L}\\p{N}])крыл\\p{L}*(?![\\p{L}\\p{N}])|(?<![\\p{L}\\p{N}])улей(?![\\p{L}\\p{N}])|(?<![\\p{L}\\p{N}])гроб(?![\\p{L}\\p{N}])|монет\\p{L}*|катан\\p{L}*|комет\\p{L}*|скин\\s+(?:варден|варен)\\p{L}*)"
    );
    // Experience shorthand in this server: "к обыч", "к опыта", and "опыт 1-11 левл пета"
    // are all the regular experience category, while "кк ад", "ад рыбу" and "адский опыт"
    // refer to hell experience.
    private static final Pattern EXPERIENCE_ITEM = Pattern.compile(
            "(?iu)^\\s*(?:опыт\\p{L}*(?:\\s|$)|к\\s+(?:обыч\\p{L}*|опыт\\p{L}*)(?:\\s|$))"
    );
    private static final Pattern HELL_EXPERIENCE_ITEM = Pattern.compile(
            "(?iu)^\\s*(?:кк\\s+ад(?:ск\\p{L}*)?(?:\\s+рыб\\p{L}*)?|(?:ад|адск\\p{L}*)\\s+(?:рыб\\p{L}*|опыт\\p{L}*))"
    );
    private static final Pattern ENDER_EXPERIENCE_ITEM = Pattern.compile(
            "(?iu)^\\s*(?:(?:энд|эндер)\\s+(?:опыт\\p{L}*|рыб\\p{L}*)|(?:опыт\\p{L}*|рыб\\p{L}*)\\s+(?:энд|эндер))"
    );
    private static final Pattern FISHING_TREE = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])дерев\\p{L}*\\s+(?:с|со)\\s+рыбалк\\p{L}*"
    );
    private static final Pattern TREE_ITEM = Pattern.compile("(?iu)^\\s*дерев\\p{L}*(?:\\s+(?:с|со)\\s+рыбалк\\p{L}*)?");
    private static final Pattern FLASKS_ITEM = Pattern.compile("(?iu)^\\s*колб\\p{L}*\\s*[.!?,;:]*\\s*$");

    private MarketCategories() {}

    public static String displayItem(String item, String rawText) {
        String value = normalizeForMatching(item);
        if (isCurrencyListing(value, rawText)) return "Игровая валюта";
        if (isCosmeticItem(value)) return "Косметика";
        if (AXOLOTL.matcher(value).find()) return "Аксолотль";
        if (HELL_EXPERIENCE_ITEM.matcher(value).find()) return "Адский опыт";
        if (ENDER_EXPERIENCE_ITEM.matcher(value).find()) return "Энд опыт";
        if (EXPERIENCE_ITEM.matcher(value).find()) return "Опыт";
        if (FISHING_TREE.matcher(value).find() || TREE_ITEM.matcher(value).matches()) return "Дерево";
        if (FLASKS_ITEM.matcher(value).matches()) return "Колбы";
        if (isFillerItem(value)) return UNKNOWN;
        if (value.isBlank() || isUnknown(value)) return UNKNOWN;
        String inferred = canonicalNamedItem(value);
        return inferred == null ? value : inferred;
    }

    public static String categoryFor(MarketOffer offer) {
        return categoryFor(offer.item(), offer.rawText());
    }

    public static String categoryFor(String item, String rawText) {
        List<String> categories = categoriesFor(item, rawText);
        return categories.isEmpty() ? "Другое" : categories.get(0);
    }

    /** A single announcement may contain several items, so it can appear in multiple tabs. */
    public static List<String> categoriesFor(String item, String rawText) {
        String itemText = item == null ? "" : item.trim();
        if (isFillerItem(itemText)) return List.of();
        String normalizedItem = normalizeForMatching(itemText);
        String source = !normalizedItem.isBlank() && !isUnknown(normalizedItem) ? normalizedItem : tradeClause(rawText);
        source = normalizeForMatching(source);
        String lower = source.toLowerCase(Locale.ROOT);
        Set<String> found = new LinkedHashSet<>();

        // Match specific full names first and remove each matched phrase before checking
        // for generic "Кейсы" / "Руны", preventing subtypes from leaking into parent tabs.
        String remaining = lower;
        remaining = collectFull(remaining, GOLDEN_PET_CASE_FULL, "ЗКСП", found);
        remaining = collectFull(remaining, CASE_DP_FULL, "Кейс DP", found);
        remaining = collectFull(remaining, RARE_HELL_RUNE_CASE_FULL, "РАКР", found);
        remaining = collectFull(remaining, GOLDEN_HELL_RUNE_CASE_FULL, "ЗАКР", found);
        remaining = collectFull(remaining, RARE_ENDER_RUNE_CASE_FULL, "РЭКР", found);
        remaining = collectFull(remaining, GOLDEN_ENDER_RUNE_CASE_FULL, "ЗЭКР", found);
        remaining = collectFull(remaining, RARE_RUNE_CASE_FULL, "РКР", found);
        remaining = collectFull(remaining, GOLDEN_RUNE_CASE_FULL, "ЗКР", found);
        remaining = collectFull(remaining, HELL_RUNE_CASE_FULL, "АКР", found);
        remaining = collectFull(remaining, ENDER_RUNE_CASE_FULL, "ЭКР", found);
        remaining = collectFull(remaining, RUNE_CASE_FULL, "КР", found);
        remaining = collectFull(remaining, PET_CASE, "КСП", found);
        remaining = collectFull(remaining, HELL_BAT_TREASURE_FULL, "АСЛМ", found);
        remaining = collectFull(remaining, ENDER_BAT_TREASURE_FULL, "ЭСЛМ", found);
        remaining = collectFull(remaining, BAT_TREASURE_FULL, "СЛМ", found);

        // Exact token matching prevents e.g. РАКР from being mistaken for АКР.
        if (ZKSP_CODE.matcher(lower).find()) found.add("ЗКСП");
        if (RAKR_CODE.matcher(lower).find()) found.add("РАКР");
        if (ZAKR_CODE.matcher(lower).find()) found.add("ЗАКР");
        if (REKR_CODE.matcher(lower).find()) found.add("РЭКР");
        if (ZEKR_CODE.matcher(lower).find()) found.add("ЗЭКР");
        if (ZKR_CODE.matcher(lower).find()) found.add("ЗКР");
        if (EKR_CODE.matcher(lower).find()) found.add("ЭКР");
        if (RKR_CODE.matcher(lower).find()) found.add("РКР");
        if (KSP_CODE.matcher(lower).find()) found.add("КСП");
        if (KR_CODE.matcher(lower).find() || KR.matcher(lower).find()) found.add("КР");
        if (ARKR.matcher(lower).find()) found.add("АРКР");
        if (AKR.matcher(lower).find()) found.add("АКР");
        if (ADKR.matcher(lower).find()) found.add("АДКР");
        if (ASLM_CODE.matcher(lower).find()) found.add("АСЛМ");
        if (ESLM_CODE.matcher(lower).find()) found.add("ЭСЛМ");
        if (SLM_CODE.matcher(lower).find()) found.add("СЛМ");

        // Payment type never determines the traded item: currency sold for LK is still
        // game currency, whereas an item sold for LK remains that item's category.
        if (isCurrencyListing(normalizedItem, rawText)) found.add("Игровая валюта");
        if (isCosmeticItem(source)) found.add("Косметика");
        if (AXOLOTL.matcher(lower).find()) found.add("Питомцы");
        if (HELL_EXPERIENCE_ITEM.matcher(source).find()) found.add("Адский опыт");
        else if (ENDER_EXPERIENCE_ITEM.matcher(source).find()) found.add("Энд опыт");
        else if (EXPERIENCE_ITEM.matcher(source).find()) found.add("Опыт");
        if (LEG_UPGRADE.matcher(lower).find()) found.add("Лег апгрейд");
        if (BOARDS.matcher(lower).find()) found.add("Доски");
        if (FISHING_TREE.matcher(source).find() || TREE_ITEM.matcher(source).matches()) found.add("Дерево");
        if (FLASKS_ITEM.matcher(source).matches()) found.add("Колбы");
        if (remaining.contains("душ")) found.add("Души");
        if (remaining.contains("рун")) found.add("Руны");
        if (hasToken(remaining, "лб")) found.add("ЛБ");
        boolean legendaryLb = LEGENDARY_LB.matcher(remaining).find();
        boolean rareLb = RARE_LB.matcher(remaining).find();
        if (legendaryLb) found.add("ЛЛБ");
        if (rareLb) found.add("РЛБ");
        if (!legendaryLb && !rareLb && remaining.matches("(?s).*лаки\\s*[- ]?\\s*блок\\p{L}*.*")) found.add("ЛБ");
        if (remaining.contains("кейс")) found.add("Кейсы");
        if (remaining.contains("аспл") || hasToken(remaining, "аспл")) found.add("АСПЛ");
        if (remaining.contains("пепел")) found.add("Пепел");
        if (remaining.contains("незер") && remaining.contains("ядр")) found.add("Незер. ядра");
        if (remaining.contains("клубок паутин") || remaining.contains("клубки паутин")) found.add("Клубки паутины");
        if (!found.contains("ЗО") && GOLD_NUTS.matcher(remaining).find()) found.add("ЗО");
        if (!found.contains("РО") && RARE_NUTS.matcher(remaining).find()) found.add("РО");
        if (!found.contains("ЗО") && !found.contains("РО") && (remaining.contains("орех") || remaining.contains("орешк"))) {
            found.add("Орешки");
        }

        if (found.isEmpty()) {
            String custom = inferCustomCategory(source);
            found.add(custom);
        }

        List<String> result = new ArrayList<>(found);
        result.sort(Comparator
                .comparingInt((String category) -> {
                    int index = ORDER.indexOf(category);
                    return index < 0 ? ORDER.size() : index;
                })
                .thenComparing(String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    private static Pattern code(String value) {
        return Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])" + Pattern.quote(value)
                + "(?:ы|а|ов|ами|ам|ки)?(?![\\p{L}\\p{N}])");
    }

    private static String collectFull(String text, Pattern pattern, String category, Set<String> found) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            found.add(category);
            matcher.appendReplacement(output, " ");
        }
        matcher.appendTail(output);
        return output.toString();
    }

    public static List<String> available(List<MarketOffer> offers, TradeAction action) {
        Set<String> categories = new LinkedHashSet<>();
        for (MarketOffer offer : offers) {
            if (action != null && offer.action() != action) continue;
            categories.addAll(offer.categories());
        }
        List<String> result = new ArrayList<>(categories);
        result.sort(Comparator
                .comparingInt((String category) -> {
                    int index = ORDER.indexOf(category);
                    return index < 0 ? ORDER.size() : index;
                })
                .thenComparing(String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static String tradeClause(String rawText) {
        if (rawText == null || rawText.isBlank()) return "";
        String cleaned = rawText.replaceAll("(?i)§[0-9A-FK-OR]", " ").replaceAll("\\s+", " ").trim();
        Matcher intent = Pattern.compile("(?iu)(?<![\\p{L}])(?:куплю|скупаю|скупает|скупают|покупаю|покупает|покупают|приобрету|продам|продаю|продаем|продаём|продает|продаёт|продают|продажа|продать)(?![\\p{L}])").matcher(cleaned);
        if (!intent.find()) return cleaned;
        int start = intent.end();
        if (intent.find()) return cleaned.substring(start, intent.start());
        return cleaned.substring(start);
    }

    /**
     * Payment unit is separate from the traded item/category: game currency and LK are
     * like two different currencies. The context must be the specific parsed trade clause,
     * not the whole chat line, because one message can contain multiple offers.
     */
    public static String paymentMethod(String item, String context) {
        String itemValue = item == null ? "" : item.trim();
        String text = context == null ? "" : context
                .replaceAll("(?i)§[0-9A-FK-OR]", " ")
                .replaceAll("\\s+", " ").trim();
        boolean legacyCurrencyForLk = CURRENCY_ALIAS.matcher(itemValue).matches();
        boolean lk = legacyCurrencyForLk || LK_MARKER.matcher(text).find()
                || COMPACT_LK_CURRENCY_RATE.matcher(text).find();
        boolean gameCurrency = GAME_CURRENCY_PAYMENT_WORD.matcher(text).find()
                || (!lk && GAME_CURRENCY_AMOUNT_PAYMENT.matcher(text).find());
        if (lk && gameCurrency) return "ЛК / игровая валюта";
        if (lk) return "ЛК";
        if (gameCurrency) return "Игровая валюта";
        return "";
    }

    private static boolean isCurrencyListing(String item, String rawText) {
        if (isCurrencyItem(item)) return true;
        if (!isUnknown(item) && !BULK.matcher(item).find()) return false;
        return isCurrencyOffer(rawText);
    }

    private static boolean isCurrencyItem(String item) {
        if (item == null || item.isBlank()) return false;
        String value = item.trim();
        if (isUnknown(value)) return false;
        String normalized = normalizeForMatching(value);
        return CURRENCY_PREFIX.matcher(normalized).find()
                || CURRENCY_ALIAS.matcher(normalized).matches()
                || CURRENCY_WORD_ALIAS.matcher(normalized).matches()
                || CURRENCY_BULK_ALIAS.matcher(normalized).matches()
                || INGAME_CURRENCY.matcher(normalized).find()
                || value.matches("(?iu)^игровые\\s+деньги(?:\\b.*)?$")
                || value.matches("(?iu)^деньги(?:\\s+(?:за|по|курс|\\d|в лс|в личку).*)?$");
    }

    private static boolean isCurrencyOffer(String rawText) {
        if (rawText == null || rawText.isBlank()) return false;
        String clause = normalizeForMatching(tradeClause(rawText).trim());
        if (CURRENCY_PREFIX.matcher(clause).find()
                || CURRENCY_ALIAS.matcher(clause).matches()
                || CURRENCY_WORD_ALIAS.matcher(clause).matches()
                || CURRENCY_BULK_ALIAS.matcher(clause).matches()
                || INGAME_CURRENCY.matcher(clause).find()
                || COMPACT_CURRENCY_RATE.matcher(clause).find()
                || COMPACT_LK_CURRENCY_RATE.matcher(clause).find()
                || CURRENCY_AMOUNT.matcher(clause).lookingAt()) return true;

        // The word "оптом" alone isn't an item. Treat it as currency only when the
        // same clause contains a T/B amount and a personal-cabinet payment marker.
        boolean bulk = BULK.matcher(clause).find();
        boolean explicitCurrencyWord = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])(?:валют\\p{L}*|курс)(?![\\p{L}\\p{N}])")
                .matcher(clause).find();
        return bulk && (explicitCurrencyWord
                || (CURRENCY_AMOUNT.matcher(clause).find() && LK_MARKER.matcher(clause).find()));
    }

    private static boolean isCosmeticItem(String item) {
        return item != null && !item.isBlank() && COSMETIC_MARKERS.matcher(item).find();
    }

    private static String canonicalNamedItem(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (isCurrencyItem(value)) return "Игровая валюта";
        if (GOLDEN_PET_CASE_FULL.matcher(lower).find() || ZKSP_CODE.matcher(lower).find()) return "ЗКСП";
        if (CASE_DP_FULL.matcher(lower).find()) return "Кейс DP";
        if (RARE_HELL_RUNE_CASE_FULL.matcher(lower).find() || RAKR_CODE.matcher(lower).find()) return "РАКР";
        if (GOLDEN_HELL_RUNE_CASE_FULL.matcher(lower).find() || ZAKR_CODE.matcher(lower).find()) return "ЗАКР";
        if (RARE_ENDER_RUNE_CASE_FULL.matcher(lower).find() || REKR_CODE.matcher(lower).find()) return "РЭКР";
        if (GOLDEN_ENDER_RUNE_CASE_FULL.matcher(lower).find() || ZEKR_CODE.matcher(lower).find()) return "ЗЭКР";
        if (RARE_RUNE_CASE_FULL.matcher(lower).find() || RKR_CODE.matcher(lower).find()) return "РКР";
        if (GOLDEN_RUNE_CASE_FULL.matcher(lower).find() || ZKR_CODE.matcher(lower).find()) return "ЗКР";
        if (HELL_RUNE_CASE_FULL.matcher(lower).find() || AKR.matcher(lower).find()) return "АКР";
        if (ENDER_RUNE_CASE_FULL.matcher(lower).find() || EKR_CODE.matcher(lower).find()) return "ЭКР";
        if (RUNE_CASE_FULL.matcher(lower).find() || KR_CODE.matcher(lower).find()) return "КР";
        if (PET_CASE.matcher(lower).find() || KSP_CODE.matcher(lower).find()) return "КСП";
        if (ARKR.matcher(lower).find()) return "АРКР";
        if (ADKR.matcher(lower).find()) return "АДКР";
        if (RARE_LB.matcher(lower).find()) return "РЛБ";
        if (LEGENDARY_LB.matcher(lower).find()) return "ЛЛБ";
        if (AXOLOTL.matcher(lower).find()) return "Аксолотль";
        if (LEG_UPGRADE.matcher(lower).find()) return "Лег апгрейд";
        if (BOARDS.matcher(lower).find()) return "Доски";
        if (HELL_BAT_TREASURE_FULL.matcher(lower).find() || ASLM_CODE.matcher(lower).find()) return "АСЛМ";
        if (ENDER_BAT_TREASURE_FULL.matcher(lower).find() || ESLM_CODE.matcher(lower).find()) return "ЭСЛМ";
        if (BAT_TREASURE_FULL.matcher(lower).find() || SLM_CODE.matcher(lower).find()) return "СЛМ";
        if (isCosmeticItem(lower)) return "Косметика";
        if (HELL_EXPERIENCE_ITEM.matcher(lower).find()) return "Адский опыт";
        if (ENDER_EXPERIENCE_ITEM.matcher(lower).find()) return "Энд опыт";
        if (EXPERIENCE_ITEM.matcher(lower).find()) return "Опыт";
        if (FISHING_TREE.matcher(lower).find() || TREE_ITEM.matcher(lower).matches()) return "Дерево";
        if (FLASKS_ITEM.matcher(lower).matches()) return "Колбы";
        if (lower.matches("(?iu)^кейс\\p{L}*$")) return "Кейсы";
        return null;
    }

    /** Normalize count prefixes and decorative punctuation before classifying an item. */
    private static String normalizeForMatching(String input) {
        if (input == null) return "";
        String value = input.trim().replace('ё', 'е').replace('Ё', 'Е');
        // Amounts are quantities, not part of category names: 3ЛЛБ -> ЛЛБ, 3КК ад рыбы -> КК ад рыбы.
        value = value.replaceFirst("(?iu)^\\s*\\d+(?:[.,]\\d+)?\\s*", "");
        value = value.replaceAll("(?iu)\\s+(?:подробност\\p{L}*|если\\s+ч[ее]|больше\\s*[-—]?\\s*больше)\\s*$", "");
        value = value.replaceAll("[\\s.!?,;:()\\[\\]{}]+$", "");
        return value.replaceAll("\\s+", " ").trim();
    }

    /** These items are not tradeable on the target server and should never appear as offers. */
    public static boolean isNonTradableItem(String item) {
        if (item == null) return false;
        return Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])посох\\p{L}*(?![\\p{L}\\p{N}])")
                .matcher(item).find();
    }

    private static boolean isUnknown(String value) {
        return value == null || value.isBlank() || value.equalsIgnoreCase(UNKNOWN);
    }

    /** Used to hide old history entries that were incorrectly created from filler such as "если че". */
    public static boolean isFillerItem(String item) {
        if (item == null) return false;
        String value = item.trim();
        return FILLER_ITEM.matcher(value).matches()
                || value.matches("(?iu)^(?:подробност\\p{L}*|если\\s+ч[её]|больше\\s*[-—]?\\s*больше)$")
                || value.matches("(?iu)^кто\\s+(?:за|по)\\s+(?:лк|личн\\p{L}*\\s+кабинет\\p{L}*)\\s+много[.!?]*$");
    }

    private static boolean hasToken(String text, String token) {
        return Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])" + Pattern.quote(token) + "(?![\\p{L}\\p{N}])")
                .matcher(text).find();
    }

    private static String inferCustomCategory(String item) {
        if (item == null || item.isBlank() || isUnknown(item)) return "Другое";
        String value = item.toLowerCase(Locale.ROOT)
                .replaceAll("(?iu)\\b(?:за|по цене|цена|от|шт|штук|штуки|в лс|личку)\\b.*$", "")
                .replaceAll("(?iu)\\b(?:куплю|покупаю|скупаю|продам|продаю|продают|продажа|купить)\\b.*$", "")
                .replaceAll("[/\\\\,;:.!?()\\[\\]{}]+", " ")
                .replaceAll("\\d+", " ")
                .replaceAll("\\s+", " ").trim();
        if (value.isBlank()) return "Другое";
        String[] words = value.split(" ");
        String first = words[0];
        if (first.matches("(?iu)(?:не|это|предмет|игровая|игровой|игровую|просто|всё|все)")) {
            return words.length > 1 ? title(words[1]) : "Другое";
        }
        if (words.length > 1 && first.length() <= 12 && words[1].length() <= 12) {
            return title(first + " " + words[1]);
        }
        return title(first);
    }

    private static String title(String value) {
        if (value == null || value.isBlank()) return "Другое";
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }
}
