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

        System.out.println("MarketParserSelfTest: all checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
