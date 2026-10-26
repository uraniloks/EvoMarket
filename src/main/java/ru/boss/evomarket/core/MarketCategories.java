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
            "АСЛМ", "ЭСЛМ", "СЛМ", "ЛЛК", "РЛБ", "ЛБ", "ЗО", "РО", "Мешочки с золотом", "Души", "Кейсы", "Косметика", "Питомцы",
            "Лег апгрейд", "Опыт", "Адский опыт", "Энд опыт", "Ведро опыта", "Зелья", "Охотки", "Ключи от данжей", "Ингры", "Лавовые кристаллы", "Литые доспехи", "Доски", "Дерево", "Колбы", "Руны", "АСПЛ", "Пепел", "Незер. ядра", "Клубки паутины", "Другое"
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
    // Currency shorthand often comes with LK or a compact quote right after the word:
    // "валку за ЛК (1тк/15б)", "валку 1к20", and plain "валку" are still currency listings.
    private static final Pattern LEGACY_CURRENCY_ITEM = Pattern.compile(
            "(?iu)^\\s*(?:валк\\p{L}*|валют\\p{L}*)(?:\\s+(?:к|за\\s+лк|лк|курс|по\\s+курсу|\\d+(?:[.,]\\d+)?\\s*[кk]\\s*\\d+(?:[.,]\\d+)?).*)?$"
    );
    private static final Pattern CURRENCY_AFTER_LK_PREFIX = Pattern.compile(
            "(?iu)^\\s*за\\s+(?:лк|lc|личн\\p{L}*\\s+кабинет\\p{L}*)\\s+\\d+(?:[.,]\\d+)?\\s*(?:триллион(?:а|ов)?|трлн|т|t|б|b|в)(?![\\p{L}\\p{N}])"
    );
    private static final Pattern LK_ONLY_ITEM = Pattern.compile("(?iu)^\\s*(?:за\\s+)?(?:лк|lc)(?:\\s+не)?\\s*$");
    // Natural chat phrases like "очень много валки по курсу 1к500м" are currency listings.
    private static final Pattern CURRENCY_BULK_ALIAS = Pattern.compile(
            "(?iu)^\\s*(?:(?:очень|реально|крайне)\\s+)?много\\s+(?:валк|валют)\\p{L}*(?:\\s|$).*$"
    );
    private static final Pattern FILLER_ITEM = Pattern.compile(
            "(?iu)^[\\s\\p{Punct}]*(?:если\\s+(?:ч[её]|что)|пишите?\\s+(?:в\\s+)?(?:лс|личку)|писать\\s+(?:в\\s+)?(?:лс|личку)|(?:в\\s+)?(?:лс|личку)|больше\\s*[-—]?\\s*больше|или\\s+обменяю|можно|мешк\\p{L}*\\s+с|мешок\\s+с)[\\s\\p{Punct}]*$"
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
            "(?iu)(?<![\\p{L}])(?:лк|lc|личн\\p{L}*\\s+кабинет\\p{L}*)(?![\\p{L}\\p{N}])"
    );
    private static final Pattern GAME_CURRENCY_PAYMENT_WORD = Pattern.compile(
            "(?iuU)(?:\\b(?:за|по|обмен\\s+(?:на|за)|оплата\\s*:?)\\s*(?:игров\\p{L}*\\s+|внутриигров\\p{L}*\\s+)?валют\\p{L}*|(?:/|\\bили\\s+)\\s*(?:игров\\p{L}*\\s+|внутриигров\\p{L}*\\s+)?валют\\p{L}*)"
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
            "(?iu)(?:косметик\\p{L}*|демоническ\\p{L}*\\s+крыл\\p{L}*|(?<![\\p{L}\\p{N}])крыл\\p{L}*(?![\\p{L}\\p{N}])|(?<![\\p{L}\\p{N}])улей(?![\\p{L}\\p{N}])|(?<![\\p{L}\\p{N}])гроб(?![\\p{L}\\p{N}])|монет\\p{L}*|катан\\p{L}*|комет\\p{L}*|скин\\p{L}*(?:\\s+[\\p{L}\\p{N}_-]+)?)"
    );
    // Experience shorthand in this server: "к обыч", "к опыта", and "опыт 1-11 левл пета"
    // are all the regular experience category, while "кк ад", "ад рыбу" and "адский опыт"
    // refer to hell experience.
    private static final Pattern EXPERIENCE_ITEM = Pattern.compile(
            "(?iu)^\\s*(?:опыт\\p{L}*(?:\\s|$)|к\\s+(?:обыч\\p{L}*|опыт\\p{L}*)(?:\\s|$))"
    );
    private static final Pattern HELL_EXPERIENCE_ITEM = Pattern.compile(
            "(?iu)^\\s*(?:(?:кк|к)\\s+ад(?:ск\\p{L}*)?(?:\\s+(?:рыб\\p{L}*|опыт\\p{L}*))?|(?:ад|адск\\p{L}*)\\s+(?:рыб\\p{L}*|опыт\\p{L}*))"
    );
    private static final Pattern ENDER_EXPERIENCE_ITEM = Pattern.compile(
            "(?iu)^\\s*(?:(?:энд|эндер)\\s+(?:опыт\\p{L}*|рыб\\p{L}*)|(?:опыт\\p{L}*|рыб\\p{L}*)\\s+(?:энд|эндер))"
    );
    private static final Pattern FISHING_TREE = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])дерев\\p{L}*\\s+(?:с|со)\\s+рыбалк\\p{L}*"
    );
    private static final Pattern TREE_ITEM = Pattern.compile(
            "(?iu)^\\s*дерев(?:о|ья|ьев|а|у|ом|ьям|ьями|ьях)(?![\\p{L}])"
    );
    private static final Pattern FLASKS_ITEM = Pattern.compile("(?iu)^\\s*колб\\p{L}*(?:\\s+для\\s+коллекц\\p{L}*)?\\s*[.!?,;:]*\\s*$");
    private static final Pattern BUCKET_ITEM = Pattern.compile("(?iu)(?=.*ведр\\p{L}*)(?=.*опыт\\p{L}*).*");
    private static final Pattern INGREDIENT_ITEM = Pattern.compile("(?iu)^\\s*ингр\\p{L}*(?:\\s+на\\s+эксо\\p{L}*)?.*$");
    private static final Pattern DUNGEON_KEY_ITEM = Pattern.compile("(?iu)ключ\\p{L}*\\s+от\\s+данж\\p{L}*");
    private static final Pattern LAVA_CRYSTALS = Pattern.compile("(?iu)(?:лав\\p{L}*|лава)\\s+кристалл\\p{L}*");
    private static final Pattern CAST_ARMOR = Pattern.compile("(?iu)(?:лит(?:ые|ое|ая)|лить[еёя]\\p{L}*)\\s+доспех\\p{L}*");
    private static final Pattern LEGENDARY_LB_CODE = code("ллб");
    private static final Pattern LEGENDARY_LK_CODE = code("ллк");
    private static final Pattern PET_EXPERIENCE_ITEM = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])рыб\\p{L}*\\s+для\\s+(?:ада|адск\\p{L}*|энда|эндер\\p{L}*)\\s+пет\\p{L}*(?:\\s+с\\s+\\d+\\s+до\\s+\\d+\\s+л(?:вл|евл)\\p{L}*)?"
    );
    private static final Pattern POTION_ITEM = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])зель\\p{L}*(?![\\p{L}\\p{N}])");
    private static final Pattern GOLD_BAGS = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])(?:мешочк\\p{L}*|мешок\\p{L}*|мешк\\p{L}*)(?:\\s+(?:с\\s+)?золот\\p{L}*)?"
    );
    private static final Pattern HELL_BAT_TREASURE_SHORT = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])адск\\p{L}*\\s+слм(?![\\p{L}\\p{N}])");
    private static final Pattern ENDER_BAT_TREASURE_SHORT = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])эндер\\s+слм(?![\\p{L}\\p{N}])");
    private static final Pattern RUNE_UPGRADE = Pattern.compile("(?iu)руническ\\p{L}*\\s+апгре[йи]д\\p{L}*");
    private static final Pattern GENERIC_NUTS = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])(?:орех\\p{L}*|орешк\\p{L}*)(?![\\p{L}\\p{N}])");

    private static final Pattern NON_ITEM_CATEGORY = Pattern.compile(
            "(?iu)^(?:по(?:\\s+и)?|кто(?:-?то)?(?:\\s+на)?|мне|им(?:\\s+на)?|до\\s+ловлю|гараж|у\\s+нас|бомбы\\s+пиши|бомбо[чш]к\\p{L}*\\s+за|много\\s+лю|по\\s+сочнейшему|тима\\s+играть|очень\\s+много|за\\s+лк|фастом|фастиком|или\\s+обменяю)[.!?\\s]*$"
    );
    private static final Pattern DANGLING_CATEGORY_WORD = Pattern.compile(
            "(?iu)^.*\\s+(?:за|на|в|и|с|от|по|к|не|для|лс|личку)$"
    );

    private MarketCategories() {}

    public static String displayItem(String item, String rawText) {
        String value = normalizeForMatching(item);
        if (isCurrencyListing(value, rawText)) return "Игровая валюта";
        if (isCosmeticItem(value)) return "Косметика";
        if (AXOLOTL.matcher(value).find()) return "Аксолотль";
        if (GOLD_BAGS.matcher(value).find()) return "Мешочки с золотом";
        if (BUCKET_ITEM.matcher(value).matches()) return "Ведро опыта";
        if (DUNGEON_KEY_ITEM.matcher(value).find()) return "Ключи от данжей";
        if (INGREDIENT_ITEM.matcher(value).matches()) return "Ингры";
        if (LAVA_CRYSTALS.matcher(value).find()) return "Лавовые кристаллы";
        if (CAST_ARMOR.matcher(value).find()) return "Литые доспехи";
        if (HELL_EXPERIENCE_ITEM.matcher(value).find()) return "Адский опыт";
        if (ENDER_EXPERIENCE_ITEM.matcher(value).find()) return "Энд опыт";
        if (PET_EXPERIENCE_ITEM.matcher(value).find() || EXPERIENCE_ITEM.matcher(value).find()) return "Опыт";
        if (POTION_ITEM.matcher(value).find()) return "Зелья";
        if (FISHING_TREE.matcher(value).find() || TREE_ITEM.matcher(value).find()) return "Дерево";
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
        remaining = collectFull(remaining, HELL_BAT_TREASURE_SHORT, "АСЛМ", found);
        remaining = collectFull(remaining, ENDER_BAT_TREASURE_SHORT, "ЭСЛМ", found);
        remaining = collectFull(remaining, GOLD_BAGS, "Мешочки с золотом", found);

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
        boolean hellSlm = ASLM_CODE.matcher(lower).find() || HELL_BAT_TREASURE_SHORT.matcher(lower).find();
        boolean enderSlm = ESLM_CODE.matcher(lower).find() || ENDER_BAT_TREASURE_SHORT.matcher(lower).find();
        if (hellSlm) found.add("АСЛМ");
        if (enderSlm) found.add("ЭСЛМ");
        if (!hellSlm && !enderSlm && SLM_CODE.matcher(lower).find()) found.add("СЛМ");

        // Payment type never determines the traded item: currency sold for LK is still
        // game currency, whereas an item sold for LK remains that item's category.
        if (isCurrencyListing(normalizedItem, rawText)) found.add("Игровая валюта");
        if (isCosmeticItem(source)) found.add("Косметика");
        if (AXOLOTL.matcher(lower).find()) found.add("Питомцы");
        if (BUCKET_ITEM.matcher(source).matches()) found.add("Ведро опыта");
        if (DUNGEON_KEY_ITEM.matcher(source).find()) found.add("Ключи от данжей");
        if (INGREDIENT_ITEM.matcher(source).matches()) found.add("Ингры");
        if (LAVA_CRYSTALS.matcher(source).find()) found.add("Лавовые кристаллы");
        if (CAST_ARMOR.matcher(source).find()) found.add("Литые доспехи");
        if (HELL_EXPERIENCE_ITEM.matcher(source).find()) found.add("Адский опыт");
        else if (ENDER_EXPERIENCE_ITEM.matcher(source).find()) found.add("Энд опыт");
        else if (PET_EXPERIENCE_ITEM.matcher(source).find() || EXPERIENCE_ITEM.matcher(source).find()) found.add("Опыт");
        if (POTION_ITEM.matcher(source).find()) found.add("Зелья");
        if (lower.matches("(?s).*охотк\\p{L}*.*")) found.add("Охотки");
        if (RUNE_UPGRADE.matcher(source).find()) found.add("Руны");
        if (GOLD_BAGS.matcher(source).find()) found.add("Мешочки с золотом");
        if (LEG_UPGRADE.matcher(lower).find()) found.add("Лег апгрейд");
        if (BOARDS.matcher(lower).find()) found.add("Доски");
        if (FISHING_TREE.matcher(source).find() || TREE_ITEM.matcher(source).find()) found.add("Дерево");
        if (FLASKS_ITEM.matcher(source).matches()) found.add("Колбы");
        if (remaining.contains("душ")) found.add("Души");
        if (remaining.contains("рун")) found.add("Руны");
        if (hasToken(remaining, "лб")) found.add("ЛБ");
        boolean legendaryLb = LEGENDARY_LB.matcher(remaining).find()
                || LEGENDARY_LB_CODE.matcher(lower).find()
                || LEGENDARY_LK_CODE.matcher(lower).find();
        boolean rareLb = RARE_LB.matcher(remaining).find();
        if (legendaryLb) found.add("ЛЛК");
        if (rareLb) found.add("РЛБ");
        if (!legendaryLb && !rareLb && remaining.matches("(?s).*лаки\\s*[- ]?\\s*блок\\p{L}*.*")) found.add("ЛБ");
        if (remaining.contains("кейс")) found.add("Кейсы");
        if (remaining.contains("аспл") || hasToken(remaining, "аспл")) found.add("АСПЛ");
        if (remaining.contains("пепел")) found.add("Пепел");
        if (remaining.contains("незер") && remaining.contains("ядр")) found.add("Незер. ядра");
        if (remaining.contains("клубок паутин") || remaining.contains("клубки паутин")) found.add("Клубки паутины");
        if (GOLD_NUTS.matcher(remaining).find()) found.add("ЗО");
        if (RARE_NUTS.matcher(remaining).find()) found.add("РО");
        // Generic "орехи" is ambiguous on this server: show the offer in both nut tabs.
        if (!found.contains("ЗО") && !found.contains("РО") && GENERIC_NUTS.matcher(remaining).find()) {
            found.add("ЗО");
            found.add("РО");
        }

        if (found.isEmpty()) {
            String custom = inferCustomCategory(source);
            if (!custom.isBlank() && !isNonItemCategoryLabel(custom)) found.add(custom);
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
                || (!lk && (GAME_CURRENCY_AMOUNT_PAYMENT.matcher(text).find()
                || (!isCurrencyItem(itemValue) && CURRENCY_AMOUNT.matcher(text).find())));
        if (lk && gameCurrency) return "ЛК / игровая валюта";
        if (lk) return "ЛК";
        if (gameCurrency) return "Игровая валюта";
        return "";
    }

    private static boolean isCurrencyListing(String item, String rawText) {
        if (isCurrencyItem(item)) return true;
        // Re-evaluate the original message for old history entries as well. The offer
        // parser may previously have stored an adverb like "фастом" as the item; the
        // source message is the reliable context for recognizing a currency lot.
        return (isUnknown(item) || item.matches("(?iu)^(?:фастом|фастиком|оптом)$"))
                && isCurrencyOffer(rawText);
    }

    private static boolean isCurrencyItem(String item) {
        if (item == null || item.isBlank()) return false;
        String value = item.trim();
        if (isUnknown(value)) return false;
        String normalized = normalizeForMatching(value);
        return CURRENCY_PREFIX.matcher(normalized).find()
                || CURRENCY_ALIAS.matcher(normalized).matches()
                || CURRENCY_WORD_ALIAS.matcher(normalized).matches()
                || LEGACY_CURRENCY_ITEM.matcher(normalized).matches()
                || LK_ONLY_ITEM.matcher(normalized).matches()
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
                || LEGACY_CURRENCY_ITEM.matcher(clause).matches()
                || LK_ONLY_ITEM.matcher(clause).matches()
                || CURRENCY_AFTER_LK_PREFIX.matcher(clause).find()
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
        if (LEGENDARY_LB.matcher(lower).find() || LEGENDARY_LB_CODE.matcher(lower).find()
                || LEGENDARY_LK_CODE.matcher(lower).find()) return "ЛЛК";
        if (AXOLOTL.matcher(lower).find()) return "Аксолотль";
        if (LEG_UPGRADE.matcher(lower).find()) return "Лег апгрейд";
        if (BOARDS.matcher(lower).find()) return "Доски";
        if (HELL_BAT_TREASURE_FULL.matcher(lower).find() || HELL_BAT_TREASURE_SHORT.matcher(lower).find() || ASLM_CODE.matcher(lower).find()) return "АСЛМ";
        if (ENDER_BAT_TREASURE_FULL.matcher(lower).find() || ENDER_BAT_TREASURE_SHORT.matcher(lower).find()
                || ESLM_CODE.matcher(lower).find()) return "ЭСЛМ";
        if (BAT_TREASURE_FULL.matcher(lower).find() || SLM_CODE.matcher(lower).find()) return "СЛМ";
        if (GOLD_BAGS.matcher(lower).find()) return "Мешочки с золотом";
        if (BUCKET_ITEM.matcher(lower).matches()) return "Ведро опыта";
        if (DUNGEON_KEY_ITEM.matcher(lower).find()) return "Ключи от данжей";
        if (INGREDIENT_ITEM.matcher(lower).matches()) return "Ингры";
        if (LAVA_CRYSTALS.matcher(lower).find()) return "Лавовые кристаллы";
        if (CAST_ARMOR.matcher(lower).find()) return "Литые доспехи";
        if (isCosmeticItem(lower)) return "Косметика";
        if (HELL_EXPERIENCE_ITEM.matcher(lower).find()) return "Адский опыт";
        if (ENDER_EXPERIENCE_ITEM.matcher(lower).find()) return "Энд опыт";
        if (PET_EXPERIENCE_ITEM.matcher(lower).find() || EXPERIENCE_ITEM.matcher(lower).find()) return "Опыт";
        if (POTION_ITEM.matcher(lower).find()) return "Зелья";
        if (RUNE_UPGRADE.matcher(lower).find()) return "Руны";
        if (FISHING_TREE.matcher(lower).find() || TREE_ITEM.matcher(lower).find()) return "Дерево";
        if (FLASKS_ITEM.matcher(lower).matches()) return "Колбы";
        if (GENERIC_NUTS.matcher(lower).matches()) return "Орехи";
        if (lower.matches("(?iu)^кейс\\p{L}*$")) return "Кейсы";
        return null;
    }

    /** Normalize count prefixes and decorative punctuation before classifying an item. */
    private static String normalizeForMatching(String input) {
        if (input == null) return "";
        String value = input.trim().replace('ё', 'е').replace('Ё', 'Е');
        // Amounts are quantities, not part of category names: 3ЛЛБ -> ЛЛБ, 3КК ад рыбы -> КК ад рыбы.
        value = value.replaceFirst("(?iu)^\\s*\\d+(?:[.,]\\d+)?\\s*", "");
        value = value.replaceFirst("(?iu)^\\s*(?:(?:фастом|фастиком|fastom|fastikom|оптом)\\s+)+", "");
        value = value.replaceAll("(?iu)\\s*\\((?:не\\s+)?передаваем\\p{L}*\\)", "");
        value = value.replaceAll("(?iu)\\s+(?:не\\s+)?передаваем\\p{L}*\\s*$", "");
        value = value.replaceFirst("(?iu)^\\s*за\\s+(?:лк|lc|личн\\p{L}*\\s+кабинет\\p{L}*)(?:\\s+|$)", "");
        value = value.replaceAll("(?iu)\\s+(?:за\\s+(?:лк|lc|личн\\p{L}*\\s+кабинет\\p{L}*)|подробност\\p{L}*|если\\s+ч[ее]|больше\\s*[-—]?\\s*больше)\\s*[.!?]*$", "");
        value = value.replaceAll("(?iu)\\s+не\\s*$", "");
        value = value.replaceAll("[\\s.!?,;:()\\[\\]{}]+$", "");
        return value.replaceAll("\\s+", " ").trim();
    }

    /** These items are not tradeable on the target server and should never appear as offers. */
    public static boolean isNonTradableItem(String item) {
        if (item == null) return false;
        return Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])(?:посох\\p{L}*|фулл\\s+балик)(?![\\p{L}\\p{N}])")
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
                || value.matches("(?iu)^(?:подробност\\p{L}*|если\\s+ч[её]|больше\\s*[-—]?\\s*больше|или\\s+обменяю|можно)$")
                || value.matches("(?iu)^кто\\s+(?:за|по)\\s+(?:лк|lc|личн\\p{L}*\\s+кабинет\\p{L}*)\\s+много[.!?]*$")
                || value.matches("(?iu)^(?:мешки?|мешка|мешок|мешочек|мешочки)\\s+с[.!?]*$");
    }

    /** Canonicalize or reject a persisted user-hidden category from older buggy builds. */
    public static String normalizeHiddenCategory(String category) {
        if (category == null || category.isBlank()) return null;
        String value = normalizeForMatching(category);
        for (String known : ORDER) {
            if (known.equalsIgnoreCase(value)) return known;
        }
        if (value.isBlank() || isNonItemCategoryLabel(value)) return null;

        // LC/payment markers are metadata, never item categories. Keep the user's
        // explicitly requested legendary block alias (ЛЛК) after dropping trailing "не".
        if (LK_ONLY_ITEM.matcher(value).matches() && !LEGENDARY_LK_CODE.matcher(value).matches()) return null;

        List<String> inferred = categoriesFor(value, value);
        if (inferred.size() > 1) return null; // old bad labels such as "ЛЛБ РАКР" mixed two goods
        String canonical = canonicalNamedItem(value);
        if (canonical != null) return canonical;
        if (isCurrencyItem(value)) return "Игровая валюта";
        if (DANGLING_CATEGORY_WORD.matcher(value).matches()) return null;
        if (value.length() < 3 && !ORDER.stream().anyMatch(c -> c.equalsIgnoreCase(value))) return null;
        return title(value);
    }

    /** True for accidental sentence fragments that must never become dynamic category buttons. */
    public static boolean isNonItemCategoryLabel(String category) {
        if (category == null || category.isBlank()) return true;
        String value = normalizeForMatching(category);
        if (value.isBlank()) return true;
        if (LK_ONLY_ITEM.matcher(value).matches() && !LEGENDARY_LK_CODE.matcher(value).matches()) return true;
        // If the phrase already identifies a known tradable item, it isn't a sentence fragment
        // (for example "к опыта" is a server alias for regular experience).
        if (canonicalNamedItem(value) != null) return false;
        // A recognized in-game-currency phrase is a valid trade item, even when it ends
        // with the shorthand "к" (as in "валку к"); LC-only tokens remain payment metadata.
        if (CURRENCY_ALIAS.matcher(value).matches() || CURRENCY_WORD_ALIAS.matcher(value).matches()
                || CURRENCY_BULK_ALIAS.matcher(value).matches() || CURRENCY_PREFIX.matcher(value).find()
                || INGAME_CURRENCY.matcher(value).find()) return false;
        if (NON_ITEM_CATEGORY.matcher(value).matches()) return true;
        if (DANGLING_CATEGORY_WORD.matcher(value).matches()) return true;
        return value.matches("(?iu)^(?:к\\s+адского|к\\s+опыт\\p{L}*|опыт\\s*-|колб\\p{L}*\\s+для|мешк\\p{L}*\\s+с|дерево\\s+штук|валк\\p{L}*\\s+за\\s+лк|за\\s+лк|очень\\s+много)$");
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
        if (value.isBlank() || isNonItemCategoryLabel(value)) return "";
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
