package ru.boss.evomarket.core;

import java.math.BigDecimal;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Run manually if desired; keeps parser checks dependency-free. */
public final class MarketParserSelfTest {
    public static void main(String[] args) throws Exception {
        MarketOffer buy = MarketParser.parse("GODFATHER | player [NexCore] [432]: Скупают золотые орешки 3в", null, Instant.EPOCH);
        check(buy != null && buy.action() == TradeAction.BUY, "buy direction");
        check("золотые орешки".equalsIgnoreCase(buy.item()), "buy item: " + buy.item());
        check(new BigDecimal("3000000000").compareTo(buy.price()) == 0, "3в price");
        check("player".equals(buy.seller()), "seller extraction: " + buy.seller());

        MarketOffer sell = MarketParser.parse("player [NexCore] [432]: продам алмазы за 16т-5б", null, Instant.EPOCH);
        check(sell != null && sell.action() == TradeAction.SELL, "sell direction");
        check(new BigDecimal("16005000000000").compareTo(sell.price()) == 0, "compound price");
        check("алмазы".equalsIgnoreCase(sell.item()), "sell item cleanup: " + sell.item());

        MarketOffer currency = MarketParser.parse("[GODFATHER] b0br1xa [NexCore] [421]: Продам 10т за 1000 лк в лс /m b0br1xa", null, Instant.EPOCH);
        check(currency != null, "currency offer detected");
        check("Игровая валюта".equals(currency.displayItem()), "currency display: " + currency.displayItem());
        check("Игровая валюта".equals(currency.category()), "currency category: " + currency.category());
        check(new BigDecimal("1000").compareTo(currency.price()) == 0, "LC payment price parsed separately from currency lot");
        check("ЛК".equals(currency.paymentLabel()), "game currency lot is sold for LK");

        MarketOffer multiple = MarketParser.parse("покупаю голд души(35в) и обычные(35в 5шт)", "tester", Instant.EPOCH);
        check(multiple != null, "multiple-item message");
        check(new BigDecimal("35000000000").compareTo(multiple.price()) == 0, "only first price cluster");

        List<MarketOffer> clauses = MarketParser.parseAll("Куплю руны ап за лк/валюту. Продам РКР/АДКР, души, кейсы, пепел и незер.ядра в лс", "tester", Instant.EPOCH);
        check(clauses.size() == 2, "separate buy and sell clauses: " + clauses.size());
        check(clauses.get(0).action() == TradeAction.BUY, "first clause buying");
        check(clauses.get(1).action() == TradeAction.SELL, "second clause selling");
        check(clauses.get(1).categories().containsAll(List.of("РКР", "АДКР", "Души", "Кейсы", "Пепел", "Незер. ядра")),
                "multi-category listing: " + clauses.get(1).categories());

        // Canonical aliases should be grouped together, even when old chat wording varies.
        MarketOffer goldNuts = MarketParser.parse("игрок [NexCore] [123]: продаю золотые орешки", "tester", Instant.EPOCH);
        check(goldNuts.categories().contains("ЗО"), "gold nuts category: " + goldNuts.categories());
        check(!goldNuts.categories().contains("Орешки"), "gold nuts should not be generic nuts");

        MarketOffer rareNuts = MarketParser.parse("игрок [NexCore] [123]: покупаю РО от 5 шт", "tester", Instant.EPOCH);
        check(rareNuts.categories().contains("РО"), "rare nuts abbreviation category: " + rareNuts.categories());

        MarketOffer rareLuckyBlock = MarketParser.parse("игрок [NexCore] [123]: продам редкий лаки блок", "tester", Instant.EPOCH);
        check(rareLuckyBlock.categories().contains("РЛБ"), "rare lucky block category: " + rareLuckyBlock.categories());
        check(!rareLuckyBlock.categories().contains("ЛБ"), "rare lucky block should not also be generic LB");

        MarketOffer legendaryLuckyBlock = MarketParser.parse("игрок [NexCore] [123]: продаю ЛЛБ", "tester", Instant.EPOCH);
        check(legendaryLuckyBlock.categories().contains("ЛЛБ"), "legendary lucky block category: " + legendaryLuckyBlock.categories());

        MarketOffer petCase = MarketParser.parse("игрок [NexCore] [123]: куплю кейс с питомцами", "tester", Instant.EPOCH);
        check(petCase.categories().contains("КСП"), "pet case category: " + petCase.categories());
        check(!petCase.categories().contains("Кейсы"), "pet case should be separate from generic cases");

        MarketOffer currencyAndKsp = MarketParser.parse("игрок [NexCore] [123]: куплю валюту КСП", "tester", Instant.EPOCH);
        check(currencyAndKsp.categories().containsAll(List.of("Игровая валюта", "КСП")),
                "currency and KSP should be separate categories: " + currencyAndKsp.categories());

        MarketOffer cosmetic = MarketParser.parse("игрок [NexCore] [123]: продам косметику драконьи крылья", "tester", Instant.EPOCH);
        check("Косметика".equals(cosmetic.displayItem()), "cosmetic title: " + cosmetic.displayItem());
        check(cosmetic.categories().contains("Косметика"), "cosmetic category: " + cosmetic.categories());

        MarketOffer currencyRate = MarketParser.parse("игрок [NexCore] [123]: продаю валюту 20бк1", "tester", Instant.EPOCH);
        check("Игровая валюта".equals(currencyRate.displayItem()), "currency rate display: " + currencyRate.displayItem());
        check(currencyRate.categories().contains("Игровая валюта"), "currency rate category: " + currencyRate.categories());
        check(new BigDecimal("20000000000").compareTo(currencyRate.price()) == 0, "currency rate numeric part: " + currencyRate.price());

        MarketOffer compactTrillionRate = MarketParser.parse("игрок [NexCore] [123]: продаю 1тк10", "tester", Instant.EPOCH);
        check("Игровая валюта".equals(compactTrillionRate.displayItem()), "compact trillion rate display");
        check(new BigDecimal("1000000000000").compareTo(compactTrillionRate.price()) == 0,
                "compact trillion rate numeric part: " + compactTrillionRate.price());

        MarketOffer currencyForItem = MarketParser.parse("игрок [NexCore] [123]: продам руны за валюту", "tester", Instant.EPOCH);
        check(!currencyForItem.categories().contains("Игровая валюта"),
                "selling an item for currency should remain item-first: " + currencyForItem.categories());

        MarketOffer bulkCurrency = MarketParser.parse("player [NexCore] [123]: Продам оптом 10т за 1000 лк в лс", "tester", Instant.EPOCH);
        check(bulkCurrency != null, "bulk currency offer parsed");
        check("Игровая валюта".equals(bulkCurrency.displayItem()), "bulk sale display: " + bulkCurrency.displayItem());
        check(bulkCurrency.categories().contains("Игровая валюта"), "bulk sale category: " + bulkCurrency.categories());
        MarketOffer bulkCurrencyWithoutUnit = MarketParser.parse("player [NexCore] [123]: Продам оптом валюту за 1000 лк", "tester", Instant.EPOCH);
        check(bulkCurrencyWithoutUnit.categories().contains("Игровая валюта"), "bulk explicit currency without T/B unit: " + bulkCurrencyWithoutUnit.categories());

        MarketOffer cosmeticsList = MarketParser.parse("player [NexCore] [123]: продам демонические крылья, улей, гроб, монеты за ЛК дешево, в лс", "tester", Instant.EPOCH);
        check(cosmeticsList.categories().contains("Косметика"), "cosmetic markers category: " + cosmeticsList.categories());
        check(!cosmeticsList.categories().contains("Демонические крылья"), "cosmetics shouldn't get a custom category: " + cosmeticsList.categories());
        check("Косметика".equals(cosmeticsList.displayItem()), "cosmetics canonical label: " + cosmeticsList.displayItem());

        MarketOffer legUpgrade = MarketParser.parse("player [NexCore] [123]: продам 2 лег апгрейда за лк в лс", "tester", Instant.EPOCH);
        check(legUpgrade.categories().contains("Лег апгрейд"), "leg upgrade canonical category: " + legUpgrade.categories());
        check("Лег апгрейд".equals(legUpgrade.displayItem()), "leg upgrade canonical display: " + legUpgrade.displayItem());

        MarketOffer mixedCabinets = MarketParser.parse("player [NexCore] [123]: Куплю АРКРы и АКРы за личные кабинеты/валюту !!", "tester", Instant.EPOCH);
        check(mixedCabinets.categories().containsAll(List.of("АРКР", "АКР")), "ARKR/AKR variants: " + mixedCabinets.categories());
        check(!mixedCabinets.categories().contains("Аркры и"), "no accidental custom category for ARKR conjunction");

        MarketOffer fishingBoards = MarketParser.parse("player [NexCore] [123]: Продам доски с рыбалки", "tester", Instant.EPOCH);
        check(fishingBoards.categories().contains("Доски"), "fishing boards category: " + fishingBoards.categories());
        check("Доски".equals(fishingBoards.displayItem()), "fishing boards display: " + fishingBoards.displayItem());

        MarketOffer bloodKrs = MarketParser.parse("player [NexCore] [123]: Продам 48 Кров за лк! Писать в ЛС!", "tester", Instant.EPOCH);
        check(bloodKrs.categories().contains("КР"), "Кров alias should map to KR: " + bloodKrs.categories());
        check(!bloodKrs.categories().contains("Кров за"), "Кров should not generate a custom category");

        MarketOffer currencyTypo = MarketParser.parse("player [NexCore] [123]: Куплю валку к за лк", "tester", Instant.EPOCH);
        check("Игровая валюта".equals(currencyTypo.displayItem()), "currency typo should normalize: " + currencyTypo.displayItem());
        check(currencyTypo.categories().contains("Игровая валюта"), "currency typo category: " + currencyTypo.categories());

        MarketOffer rkrPluralTypo = MarketParser.parse("player [NexCore] [123]: продаю РКРки в ЛС", "tester", Instant.EPOCH);
        check("РКР".equals(rkrPluralTypo.displayItem()), "RKR plural typo should normalize: " + rkrPluralTypo.displayItem());
        check(rkrPluralTypo.categories().contains("РКР"), "RKR plural typo category: " + rkrPluralTypo.categories());

        String mixedCurrencyAndCases = "podnarkotikami [MMM] [403]: ПРОДАМ 10t, по 3 t за 299 лк, БОЛЬШЕ-БОЛЬШЕ, кейсы ТОЖ ПРОДАЮ ЕСЛИ ЧЕ";
        List<MarketOffer> mixedOffers = MarketParser.parseAll(mixedCurrencyAndCases, "podnarkotikami", Instant.EPOCH);
        check(mixedOffers.size() == 2, "mixed currency/cases line should yield exactly two real offers: " + mixedOffers.size());
        check(mixedOffers.stream().anyMatch(offer -> offer.categories().contains("Игровая валюта")),
                "currency amount at start should be a currency offer");
        check(mixedOffers.stream().anyMatch(offer -> offer.categories().contains("Кейсы")),
                "reversed 'кейсы тож продаю' should be recovered");
        check(mixedOffers.stream().filter(offer -> offer.categories().contains("Кейсы"))
                .allMatch(offer -> offer.paymentLabel().isBlank()),
                "LK price for currency offer must not leak onto separate case offer");
        check(mixedOffers.stream().noneMatch(offer -> offer.item().equalsIgnoreCase("если че")),
                "filler 'если че' must not become an item");
        check(MarketCategories.isFillerItem("Если че"), "filler history entry should be hideable");
        check(MarketCategories.categoriesFor("Если че", mixedCurrencyAndCases).isEmpty(),
                "old filler offers should not create categories");


        // Payment units are separate from item categories: LK is like a separate currency.
        MarketOffer currencyForLk = MarketParser.parse("player [NexCore] [123]: Куплю валку к за лк", "tester", Instant.EPOCH);
        check("Игровая валюта".equals(currencyForLk.displayItem()), "legacy 'валку к' must not become a separate category");
        check("ЛК".equals(currencyForLk.paymentLabel()), "legacy 'валку к' should signal payment in LK: " + currencyForLk.paymentLabel());

        MarketOffer itemForLk = MarketParser.parse("player [NexCore] [123]: Продам АРКР за 299 лк", "tester", Instant.EPOCH);
        check(itemForLk.categories().contains("АРКР"), "item paid in LK remains in its item category");
        check("ЛК".equals(itemForLk.paymentLabel()), "item payment is LK");
        check(new BigDecimal("299").compareTo(itemForLk.price()) == 0, "LK numeric price parsed");

        MarketOffer itemForGameCurrency = MarketParser.parse("player [NexCore] [123]: Куплю АРКР за 3т", "tester", Instant.EPOCH);
        check("Игровая валюта".equals(itemForGameCurrency.paymentLabel()), "game currency used as payment");

        MarketOffer gameCurrencyForLk = MarketParser.parse("player [NexCore] [123]: Продам 10t за 1000 лк", "tester", Instant.EPOCH);
        check("Игровая валюта".equals(gameCurrencyForLk.displayItem()), "game currency is the traded item");
        check("ЛК".equals(gameCurrencyForLk.paymentLabel()), "game currency lot paid in LK");
        check(new BigDecimal("1000").compareTo(gameCurrencyForLk.price()) == 0, "currency lot LK price parsed");

        MarketOffer bothCurrencies = MarketParser.parse("player [NexCore] [123]: Куплю руны за лк/валюту", "tester", Instant.EPOCH);
        check("ЛК / игровая валюта".equals(bothCurrencies.paymentLabel()), "both payment options are retained");

        MarketOffer lkRate = MarketParser.parse("player [NexCore] [123]: Продам 10t за 1000 лк", "tester", Instant.EPOCH);
        check("Игровая валюта".equals(lkRate.displayItem()), "selling game currency for LK is still a currency listing");
        check("ЛК".equals(lkRate.paymentLabel()), "payment method is LK");
        check(new BigDecimal("1000").compareTo(lkRate.price()) == 0, "LK price amount is parsed");

        MarketOffer rkrTypo = MarketParser.parse("player [NexCore] [123]: Куплю ркрки в лс", "tester", Instant.EPOCH);
        check("РКР".equals(rkrTypo.displayItem()), "RKR plural typo canonicalization");
        check(rkrTypo.paymentLabel().isBlank(), "in-LS message does not mean LK payment");

        MarketOffer mixedPayment = MarketParser.parse("player [NexCore] [123]: Куплю АРКР за лк/валюту", "tester", Instant.EPOCH);
        check("ЛК / игровая валюта".equals(mixedPayment.paymentLabel()), "mixed payment options stay distinct");

        MarketOffer axolotl = MarketParser.parse("player [NexCore] [123]: Продам аксолотля подробности", "tester", Instant.EPOCH);
        check(axolotl != null, "axolotl listing should survive filler removal");
        check("Аксолотль".equals(axolotl.displayItem()), "details word should be removed: " + axolotl.displayItem());
        check(axolotl.categories().contains("Питомцы"), "axolotl should be in pets category: " + axolotl.categories());

        MarketOffer legUpgradeVariant = MarketParser.parse("player [NexCore] [123]: Продам лег апгрейда за лк", "tester", Instant.EPOCH);
        check(legUpgradeVariant != null, "leg upgrade variant parsed");
        check("Лег апгрейд".equals(legUpgradeVariant.displayItem()), "leg upgrade variants use one label: " + legUpgradeVariant.displayItem());
        check(legUpgradeVariant.categories().contains("Лег апгрейд"), "leg upgrade variants share one category");

        MarketOffer plainCurrencyAlias = MarketParser.parse("player [NexCore] [123]: Куплю валку", "tester", Instant.EPOCH);
        check(plainCurrencyAlias != null && "Игровая валюта".equals(plainCurrencyAlias.displayItem()),
                "plain 'валку' should be game currency, not a custom category");
        check(plainCurrencyAlias.paymentLabel().isBlank(), "plain 'валку' should not imply LK payment without an LK marker");

        MarketOffer contactOnly = MarketParser.parse("player [NexCore] [123]: Куплю в ЛС", "tester", Instant.EPOCH);
        check(contactOnly == null, "contact-only message should not create a market listing");

        String helperAdvertisement = "[GAMEPRODUCER] _Foghost_ [440]: анализирует сообщения в чате людей, которые хотят продать что-то или купить, и добавляет в удобную панельку, где всё прекрасно видно";
        check(MarketParser.parseAll(helperAdvertisement, "_Foghost_", Instant.EPOCH).isEmpty(),
                "meta-advertisement about a chat panel must not be treated as a real offer");

        Path tempDir = Files.createTempDirectory("evomarket-dedupe-test");
        Path historyFile = tempDir.resolve("market-history.tsv");
        MarketStore store = new MarketStore(historyFile);
        String repeatedLine = "player [NexCore] [123]: Продам золотые орешки за 3в";
        MarketOffer repeatedOne = MarketParser.parse(repeatedLine, "player", Instant.EPOCH);
        MarketOffer repeatedTwo = MarketParser.parse(repeatedLine, "player", Instant.EPOCH.plusSeconds(180));
        check(store.add(repeatedOne), "first copy of listing should be stored");
        check(!store.add(repeatedTwo), "same repost should be deduplicated after several minutes");
        check(store.size() == 1, "duplicate listing should appear once in history");
        MarketStore reloaded = new MarketStore(historyFile);
        check(reloaded.size() == 1, "history should reload without duplicate copies");

        // DiamondWorld-specific case and Bat Treasure taxonomy. Each special subtype
        // must have its own category and must not leak into the generic cases/runes tabs.
        String[][] caseAliases = {
                {"кейс с рунами", "КР"},
                {"редкий кейс с рунами", "РКР"},
                {"кейс DP", "Кейс DP"},
                {"кейс с питомцами", "КСП"},
                {"золотой кейс с питомцами", "ЗКСП"},
                {"адский кейс с рунами", "АКР"},
                {"редкий адский кейс с рунами", "РАКР"},
                {"эндер кейс с рунами", "ЭКР"},
                {"редкий эндер кейс с рунами", "РЭКР"},
                {"золотой кейс с рунами", "ЗКР"},
                {"золотой адский кейс с рунами", "ЗАКР"},
                {"золотой эндер кейс с рунами", "ЗЭКР"},
                {"сокровища летучей мыши", "СЛМ"},
                {"адские сокровища летучей мыши", "АСЛМ"},
                {"эндер сокровища летучей мыши", "ЭСЛМ"},
                {"ЗКСП", "ЗКСП"},
                {"РАКР", "РАКР"},
                {"ЗАКР", "ЗАКР"},
                {"РЭКР", "РЭКР"},
                {"ЗЭКР", "ЗЭКР"},
                {"ЗКР", "ЗКР"},
                {"ЭКР", "ЭКР"},
                {"РКРки", "РКР"},
                {"КСП", "КСП"},
                {"АСЛМ", "АСЛМ"},
                {"ЭСЛМ", "ЭСЛМ"},
                {"СЛМ", "СЛМ"}
        };
        for (String[] testCase : caseAliases) {
            List<String> found = MarketCategories.categoriesFor(testCase[0], testCase[0]);
            check(found.contains(testCase[1]), "case alias '" + testCase[0] + "' should map to " + testCase[1] + ", got " + found);
            check(!found.contains("Кейсы") && !found.contains("Руны"),
                    "specific case should not leak into generic categories: " + testCase[0] + " -> " + found);
        }
        MarketOffer rareHellRuneCase = MarketParser.parse("player [NexCore] [123]: Продам редкий адский кейс с рунами за 3в", "tester", Instant.EPOCH);
        check(rareHellRuneCase.categories().equals(List.of("РАКР")), "rare hell rune case must remain a single specific category: " + rareHellRuneCase.categories());
        MarketOffer goldEnderRuneCase = MarketParser.parse("player [NexCore] [123]: Куплю золотой эндер кейс с рунами", "tester", Instant.EPOCH);
        check(goldEnderRuneCase.categories().equals(List.of("ЗЭКР")), "golden ender rune case must remain a single specific category: " + goldEnderRuneCase.categories());

        // DiamondWorld-specific case and Bat Treasure categories provided by the user.
        String[][] caseTaxonomy = {
                {"кейс", "Кейсы"},
                {"кейс с рунами", "КР"},
                {"редкий кейс с рунами", "РКР"},
                {"кейс DP", "Кейс DP"},
                {"кейс с питомцами", "КСП"},
                {"золотой кейс с питомцами", "ЗКСП"},
                {"адский кейс с рунами", "АКР"},
                {"редкий адский кейс с рунами", "РАКР"},
                {"эндер кейс с рунами", "ЭКР"},
                {"редкий эндер кейс с рунами", "РЭКР"},
                {"золотой кейс с рунами", "ЗКР"},
                {"золотой адский кейс с рунами", "ЗАКР"},
                {"золотой эндер кейс с рунами", "ЗЭКР"},
                {"сокровища летучей мыши", "СЛМ"},
                {"адские сокровища летучей мыши", "АСЛМ"},
                {"эндер сокровища летучей мыши", "ЭСЛМ"},
                {"ЗКСП", "ЗКСП"}, {"РАКР", "РАКР"}, {"ЗАКР", "ЗАКР"},
                {"РЭКР", "РЭКР"}, {"ЗЭКР", "ЗЭКР"}, {"ЗКР", "ЗКР"},
                {"ЭКР", "ЭКР"}, {"РКРки", "РКР"}, {"КСП", "КСП"},
                {"АСЛМ", "АСЛМ"}, {"ЭСЛМ", "ЭСЛМ"}, {"СЛМ", "СЛМ"}
        };
        for (String[] entry : caseTaxonomy) {
            List<String> categories = MarketCategories.categoriesFor(entry[0], entry[0]);
            check(categories.contains(entry[1]), "case taxonomy '" + entry[0] + "' -> " + entry[1] + ", got " + categories);
            if (!entry[1].equals("Кейсы")) {
                check(!categories.contains("Кейсы") && !categories.contains("Руны"),
                        "specific case must not fall into generic cases/runes: " + entry[0] + " -> " + categories);
            }
        }
        check("РАКР".equals(MarketCategories.displayItem("редкий адский кейс с рунами", "")), "canonical rare hell case label");
        check("ЗЭКР".equals(MarketCategories.displayItem("золотой эндер кейс с рунами", "")), "canonical golden ender case label");

        // Social clan-seeking is not a market purchase.
        check(MarketParser.parseAll("player [NexCore] [155]: Ищу клан где смогу показать свою силу", "tester", Instant.EPOCH).isEmpty(),
                "looking for a clan should not create a market listing");

        // Compact 1k15 / 1к15 notation followed by a currency word is an LK exchange rate.
        MarketOffer compactLkRate = MarketParser.parse("player [NexCore] [123]: Продам 1k15 валюту пацанский курс для пацанов", "tester", Instant.EPOCH);
        check(compactLkRate != null, "compact LK rate listing should parse");
        check("Игровая валюта".equals(compactLkRate.displayItem()), "compact LK rate is game currency, not a custom 'K валюту' item: " + compactLkRate.displayItem());
        check(compactLkRate.categories().contains("Игровая валюта"), "compact LK rate category: " + compactLkRate.categories());
        check("ЛК".equals(compactLkRate.paymentLabel()), "compact 1k15 currency rate is paid in LK: " + compactLkRate.paymentLabel());
        MarketOffer compactCyrillicLkRate = MarketParser.parse("player [NexCore] [123]: Продам 1к15 валюту", "tester", Instant.EPOCH);
        check(compactCyrillicLkRate != null && "ЛК".equals(compactCyrillicLkRate.paymentLabel()),
                "Cyrillic compact 1к15 rate should be recognized as LK");

        // The scanner must not turn ordinary chat into a listing without an explicit trade verb.
        check(MarketParser.parseAll("player [NexCore] [155]: опыт 1-11 левл пета в лс", "tester", Instant.EPOCH).isEmpty(),
                "no explicit buy/sell verb means no listing");
        check(MarketParser.parseAll("player [NexCore] [155]: клан где смогу показать свою силу", "tester", Instant.EPOCH).isEmpty(),
                "ordinary clan message must not be listed");

        MarketOffer regularExperience1 = MarketParser.parse("player [NexCore] [155]: Продам к обыч за лк", "tester", Instant.EPOCH);
        MarketOffer regularExperience2 = MarketParser.parse("player [NexCore] [155]: Куплю к опыта за лк", "tester", Instant.EPOCH);
        MarketOffer regularExperience3 = MarketParser.parse("player [NexCore] [155]: Продам опыт 1-11 левл пета в лс", "tester", Instant.EPOCH);
        check(regularExperience1 != null && regularExperience1.categories().contains("Опыт"), "k obych maps to regular experience: " + (regularExperience1 == null ? null : regularExperience1.categories()));
        check(regularExperience2 != null && regularExperience2.categories().contains("Опыт"), "k opyta maps to regular experience: " + (regularExperience2 == null ? null : regularExperience2.categories()));
        check(regularExperience3 != null && regularExperience3.categories().contains("Опыт"), "numbered pet experience maps to regular experience: " + (regularExperience3 == null ? null : regularExperience3.categories()));
        check(!regularExperience3.categories().contains("Опыт -"), "experience dash category should not be created");

        MarketOffer hellExperience1 = MarketParser.parse("player [NexCore] [155]: Продам КК ад рыбы в лс", "tester", Instant.EPOCH);
        MarketOffer hellExperience2 = MarketParser.parse("player [NexCore] [155]: Продам АД рыбу 1 М", "tester", Instant.EPOCH);
        check(hellExperience1 != null && hellExperience1.categories().contains("Адский опыт"), "KK ad ryby maps to hell experience: " + (hellExperience1 == null ? null : hellExperience1.categories()));
        check(hellExperience2 != null && hellExperience2.categories().contains("Адский опыт"), "AD rybu maps to hell experience: " + (hellExperience2 == null ? null : hellExperience2.categories()));

        MarketOffer tree = MarketParser.parse("player [NexCore] [155]: Продам дерево с рыбалки 5в", "tester", Instant.EPOCH);
        check(tree != null && tree.displayItem().equals("Дерево") && tree.categories().contains("Дерево"), "tree from fishing maps to tree: " + (tree == null ? null : tree.categories()));
        MarketOffer katana = MarketParser.parse("player [NexCore] [155]: Продам катана за 5в", "tester", Instant.EPOCH);
        check(katana != null && katana.categories().contains("Косметика"), "katana maps to cosmetics");
        MarketOffer flasks = MarketParser.parse("player [NexCore] [155]: Продам колбы!", "tester", Instant.EPOCH);
        check(flasks != null && flasks.displayItem().equals("Колбы") && flasks.categories().contains("Колбы"), "flasks trailing punctuation normalized: " + (flasks == null ? null : flasks.categories()));
        MarketOffer legUpgradeShort = MarketParser.parse("player [NexCore] [155]: Продам лег апп", "tester", Instant.EPOCH);
        check(legUpgradeShort != null && legUpgradeShort.categories().contains("Лег апгрейд"), "leg app typo maps to one category");
        MarketOffer kspExtra = MarketParser.parse("player [NexCore] [155]: Продам КСП за лк", "tester", Instant.EPOCH);
        check(kspExtra != null && kspExtra.categories().contains("КСП") && !kspExtra.categories().contains("Ксп за"), "KSP with trailing payment marker stays canonical");

        // Regression fixes from the latest real-chat screenshots.
        MarketOffer numberedHellExperience = MarketParser.parse("player [NexCore] [155]: Продам 3кк ад рыбы", "tester", Instant.EPOCH);
        check(numberedHellExperience != null && numberedHellExperience.displayItem().equals("Адский опыт"),
                "leading quantity must not break hell experience classification: " + (numberedHellExperience == null ? null : numberedHellExperience.displayItem()));
        check(numberedHellExperience.categories().contains("Адский опыт"), "numbered hell experience category");

        MarketOffer endExperience = MarketParser.parse("player [NexCore] [155]: Продам энд опыт за лк", "tester", Instant.EPOCH);
        MarketOffer endFish = MarketParser.parse("player [NexCore] [155]: Продам энд рыбу за лк", "tester", Instant.EPOCH);
        check(endExperience != null && endFish != null, "both end-experience variants parse");
        check(endExperience.displayItem().equals("Энд опыт") && endFish.displayItem().equals("Энд опыт"),
                "end experience and end fish use the same display category");
        check(endFish.categories().contains("Энд опыт"), "end fish maps to end experience category");

        MarketOffer numberedLlb = MarketParser.parse("player [NexCore] [155]: Продам 3ллб за лк", "tester", Instant.EPOCH);
        check(numberedLlb != null && numberedLlb.displayItem().equals("ЛЛБ"),
                "leading quantity and lowercase should canonicalize LLB: " + (numberedLlb == null ? null : numberedLlb.displayItem()));
        check(numberedLlb.categories().contains("ЛЛБ"), "numbered LLB remains in canonical LLB category");
        MarketOffer separateLlbRakr = MarketParser.parse("player [NexCore] [155]: Продам ЛЛБ РАКР за лк", "tester", Instant.EPOCH);
        check(separateLlbRakr != null && separateLlbRakr.categories().containsAll(List.of("ЛЛБ", "РАКР")),
                "LLB and RAKR must be classified as separate item categories: " + (separateLlbRakr == null ? null : separateLlbRakr.categories()));

        MarketOffer flasksPunctuated = MarketParser.parse("player [NexCore] [155]: Продам колбы !", "tester", Instant.EPOCH);
        check(flasksPunctuated != null && flasksPunctuated.displayItem().equals("Колбы"),
                "flasks punctuation normalized: " + (flasksPunctuated == null ? null : flasksPunctuated.displayItem()));

        MarketOffer wardenSkin = MarketParser.parse("player [NexCore] [155]: Продам скин варена за лк", "tester", Instant.EPOCH);
        MarketOffer comets = MarketParser.parse("player [NexCore] [155]: Продам кометы за лк", "tester", Instant.EPOCH);
        check(wardenSkin != null && wardenSkin.categories().contains("Косметика"), "warden skin maps to cosmetics");
        check(comets != null && comets.categories().contains("Косметика"), "comets map to cosmetics");

        MarketOffer lotsOfCurrency = MarketParser.parse("player [NexCore] [155]: Покупаю ОЧЕНЬ много валки по курсу 1к500м спамьте в лс мне", "tester", Instant.EPOCH);
        check(lotsOfCurrency != null && lotsOfCurrency.categories().contains("Игровая валюта"),
                "large currency request should be currency, not a custom Очень много category: " + (lotsOfCurrency == null ? null : lotsOfCurrency.categories()));
        check(!lotsOfCurrency.categories().contains("Очень много"), "bulk currency does not create custom category");

        check(MarketParser.parseAll("player [NexCore] [155]: Продает кто за ЛК много?", "tester", Instant.EPOCH).isEmpty(),
                "question asking who wants many LK must not create a category");
        check(MarketParser.parseAll("player [NexCore] [155]: Продам фото своих ножек за 1000лк", "tester", Instant.EPOCH).isEmpty(),
                "joke about photos of feet must be ignored");
        check(MarketParser.parseAll("player [NexCore] [155]: Продам посох молний за 10в", "tester", Instant.EPOCH).isEmpty(),
                "non-tradable staff item must be ignored");
        check(MarketParser.parseAll("player [NexCore] [155]: так посох молний нельзя же продать и в целом все посохи", "tester", Instant.EPOCH).isEmpty(),
                "joke about untradable staffs must be ignored");

        // 0.1.15 regressions collected from the newest screenshots.
        MarketOffer fastBulkCurrency = MarketParser.parse(
                "player [NexCore] [155]: Продам фастом 5.5T за пак 299лк", "tester", Instant.EPOCH);
        check(fastBulkCurrency != null && "Игровая валюта".equals(fastBulkCurrency.displayItem()),
                "fastom currency sale must be currency, not a Fastom item");
        check(fastBulkCurrency.categories().equals(List.of("Игровая валюта")),
                "fastom currency sale has a single currency category: " + fastBulkCurrency.categories());
        check(new BigDecimal("299").compareTo(fastBulkCurrency.price()) == 0,
                "price after 'за пак' should be parsed as 299 LK: " + fastBulkCurrency.price());
        check("ЛК".equals(fastBulkCurrency.paymentLabel()), "fastom lot payment should be LK");

        MarketOffer fastikCurrency = MarketParser.parse(
                "player [NexCore] [155]: Продам фастиком 15т за пак 950лк", "tester", Instant.EPOCH);
        check(fastikCurrency != null && "Игровая валюта".equals(fastikCurrency.displayItem()),
                "fastikom currency sale should be in the currency category");
        check(new BigDecimal("950").compareTo(fastikCurrency.price()) == 0,
                "fastikom price after 'за пак' should be parsed");

        MarketOffer hellBatAbbreviation = MarketParser.parse(
                "player [NexCore] [155]: Скупает адские СЛМ по 3в", "tester", Instant.EPOCH);
        check(hellBatAbbreviation != null && hellBatAbbreviation.categories().contains("АСЛМ"),
                "'адские СЛМ' should map to ASLM: " + (hellBatAbbreviation == null ? null : hellBatAbbreviation.categories()));
        check(!hellBatAbbreviation.categories().contains("Адские"), "ASLM must not create a custom category");

        List<MarketOffer> seekCosmetic = MarketParser.parseAll(
                "player [NexCore] [155]: Ищу косметику \"Кошачьи ушки\" (Куплю или обменяю)", "tester", Instant.EPOCH);
        check(seekCosmetic.size() == 1, "seek cosmetic with 'buy or exchange' should create one offer only: " + seekCosmetic.size());
        check(seekCosmetic.get(0).categories().contains("Косметика"),
                "cat ears offer should go to Cosmetics: " + seekCosmetic.get(0).categories());
        check(seekCosmetic.stream().noneMatch(o -> o.categories().contains("Или обменяю")),
                "'или обменяю' must not create a category");

        MarketOffer wendigoSkin = MarketParser.parse("player [NexCore] [155]: Продам скин вендиго за лк", "tester", Instant.EPOCH);
        check(wendigoSkin != null && wendigoSkin.categories().contains("Косметика"),
                "Wendigo skin should be Cosmetics: " + (wendigoSkin == null ? null : wendigoSkin.categories()));
        check("Косметика".equals(wendigoSkin.displayItem()), "Wendigo skin should have canonical display label");
        check(MarketParser.parseAll("player [NexCore] [155]: Продам фулл балик", "tester", Instant.EPOCH).isEmpty(),
                "full balic is not a market item");

        MarketOffer petHellFish = MarketParser.parse(
                "player [NexCore] [155]: Куплю рыбы для ада пета с 1 до 11 лвла", "tester", Instant.EPOCH);
        check(petHellFish != null && petHellFish.categories().contains("Опыт"),
                "pet level fish should go into regular Experience: " + (petHellFish == null ? null : petHellFish.categories()));
        check(!petHellFish.categories().contains("Рыбы для"), "pet fish must not create a custom category");

        MarketOffer bulkCurrencyWord = MarketParser.parse(
                "player [NexCore] [155]: Покупаю ОЧЕНЬ много валюты по курсу 1к500м", "tester", Instant.EPOCH);
        check(bulkCurrencyWord != null && bulkCurrencyWord.categories().contains("Игровая валюта"),
                "very-much currency rate should be game currency");
        check(bulkCurrencyWord.categories().stream().noneMatch(c -> c.equalsIgnoreCase("Очень много")),
                "currency adverb should not create a category");

        String[][] goldBagAliases = {
                {"мешочки с золотом", "Мешочки с золотом"},
                {"мешок с золотом", "Мешочки с золотом"},
                {"мешка с золотом", "Мешочки с золотом"},
                {"мешка", "Мешочки с золотом"},
                {"мешочки золотта", "Мешочки с золотом"},
                {"мешки с золотом", "Мешочки с золотом"}
        };
        int bagAliasIndex = 0;
        for (String[] testCase : goldBagAliases) {
            MarketOffer bag = MarketParser.parse("player [NexCore] [155]: Скупаю " + testCase[0] + " по 10б", "tester", Instant.EPOCH);
            check(bag != null && bag.categories().contains(testCase[1]),
                    "gold bag alias index=" + bagAliasIndex + " -> " + (bag == null ? null : bag.categories()));
            check(bag.categories().stream().noneMatch(c -> c.matches("(?iu)мешк.*")),
                    "no duplicate bag category: " + bag.categories());
            bagAliasIndex++;
        }
        check(MarketParser.parseAll("player [NexCore] [155]: Продаю мешки с", "tester", Instant.EPOCH).isEmpty(),
                "partial 'мешки с' is not a complete item");
        check(MarketParser.parseAll("player [NexCore] [155]: Продаю мешок с", "tester", Instant.EPOCH).isEmpty(),
                "partial 'мешок с' is not a complete item");

        MarketOffer potionOfLoot = MarketParser.parse("player [NexCore] [155]: Продам зелья добычи за 5в", "tester", Instant.EPOCH);
        check(potionOfLoot != null && potionOfLoot.categories().contains("Зелья"),
                "potion of loot belongs to potions: " + (potionOfLoot == null ? null : potionOfLoot.categories()));
        MarketOffer masterPotionAndHunts = MarketParser.parse("player [NexCore] [155]: Продам охотки и зелье мастера за 5в", "tester", Instant.EPOCH);
        check(masterPotionAndHunts != null && masterPotionAndHunts.categories().containsAll(List.of("Охотки", "Зелья")),
                "a listing with potions should also populate the Potions category: " + (masterPotionAndHunts == null ? null : masterPotionAndHunts.categories()));

        check(MarketParser.parseAll("player [NexCore] [155]: а лег апгрейд лопаты продать можно?", "tester", Instant.EPOCH).isEmpty(),
                "a question asking if an item can be sold is not an offer");
        check(MarketParser.parseAll("player [NexCore] [155]: дп кейсы покупает кто-то на ауке?", "tester", Instant.EPOCH).isEmpty(),
                "question about someone else buying at auction is not an offer");

        MarketOffer runicUpgradeForCurrency = MarketParser.parse(
                "player [NexCore] [155]: [Рунический апгрейд] продаю за 10т", "tester", Instant.EPOCH);
        check(runicUpgradeForCurrency != null && runicUpgradeForCurrency.categories().contains("Руны"),
                "bracketed runic upgrade before a sell verb belongs to Runes: " + (runicUpgradeForCurrency == null ? null : runicUpgradeForCurrency.categories()));
        check(!runicUpgradeForCurrency.categories().contains("За"), "currency preposition must not become item category");

        MarketOffer countedTree = MarketParser.parse(
                "player [NexCore] [155]: Куплю дерево 30 штуки, проклятый жемчуг 7 шт по хорошей цене писать в лс", "tester", Instant.EPOCH);
        check(countedTree != null && countedTree.categories().contains("Дерево"),
                "tree with count and extra text belongs to Tree: " + (countedTree == null ? null : countedTree.categories()));
        check("Дерево".equals(countedTree.displayItem()), "counted fishing tree should display simply as Tree");

        MarketOffer currencyPrefixedByLk = MarketParser.parse(
                "player [NexCore] [155]: Продаю за ЛК 5T, курс: 1в к 1", "tester", Instant.EPOCH);
        check(currencyPrefixedByLk != null && currencyPrefixedByLk.categories().contains("Игровая валюта"),
                "LK-first currency offer should be categorized as game currency: " + (currencyPrefixedByLk == null ? null : currencyPrefixedByLk.categories()));
        check("ЛК".equals(currencyPrefixedByLk.paymentLabel()), "LK-first currency quote uses LK as payment");
        check(!currencyPrefixedByLk.categories().contains("За лк"), "payment phrase must never become a category");

        MarketOffer genericNuts = MarketParser.parse("player [NexCore] [155]: Продам орехи за 5в", "tester", Instant.EPOCH);
        check(genericNuts != null && genericNuts.categories().containsAll(List.of("ЗО", "РО")),
                "unspecified nuts should appear in both ZO and RO: " + (genericNuts == null ? null : genericNuts.categories()));
        check(!genericNuts.categories().contains("Орешки"), "generic nuts should not get their own category");

        System.out.println("MarketParserSelfTest: all checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
