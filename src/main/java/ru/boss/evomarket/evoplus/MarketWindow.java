package ru.boss.evomarket.evoplus;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import ru.boss.evomarket.core.CategoryVisibilityStore;
import ru.boss.evomarket.core.MarketCategories;
import ru.boss.evomarket.core.MarketOffer;
import ru.boss.evomarket.core.MarketStore;
import ru.boss.evomarket.core.MarketOfferSorter;
import ru.boss.evomarket.core.MarketSortMode;
import ru.boss.evomarket.core.SellerListStore;
import ru.boss.evomarket.core.TradeAction;
import ru.dargen.evoplus.api.addon.Addon;
import ru.dargen.evoplus.api.ui.TextStyle;
import ru.dargen.evoplus.api.ui.Ui;
import ru.dargen.evoplus.api.ui.UiButton;
import ru.dargen.evoplus.api.ui.UiContainer;
import ru.dargen.evoplus.api.ui.UiText;
import ru.dargen.evoplus.api.ui.UiWindow;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Market UI with category management, seller controls and currency-aware price sorting. */
final class MarketWindow {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern CURRENCY_RATE = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])(\\d+(?:[.,]\\d+)?)\\s*([тtбbв])\\s*к\\s*(\\d+(?:[.,]\\d+)?)(?![\\p{L}\\p{N}])"
    );
    private static final Pattern COMPACT_LK_CURRENCY_RATE = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])(\\d+(?:[.,]\\d+)?)\\s*[кk]\\s*(\\d+(?:[.,]\\d+)?)(?![\\p{L}\\p{N}])(?=\\s+(?:(?:игров\\p{L}*|внутриигров\\p{L}*)\\s+)?валют\\p{L}*)"
    );
    private static final Pattern CURRENCY_LK_BUNDLE = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])(\\d+(?:[.,]\\d+)?)\\s*([тtбbв])\\s*,?\\s*по\\s*(\\d+(?:[.,]\\d+)?)\\s*([тtбbв])\\s*за\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:лк|личн\\p{L}*\\s+кабинет\\p{L}*)(?![\\p{L}])"
    );
    private static final Pattern CURRENCY_LK_SINGLE = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])(\\d+(?:[.,]\\d+)?)\\s*([тtбbв])\\s*(?:,?\\s*)за\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:лк|личн\\p{L}*\\s+кабинет\\p{L}*)(?![\\p{L}])"
    );
    private static final long AUTO_REFRESH_MS = 600;
    private static final int BUTTONS_PER_ROW = 4;
    private static final int MAX_RENDERED_OFFERS = 150;

    private static WindowView activeView;
    private static boolean tickHookRegistered;
    private static long lastAutoRefresh;

    private static final String HIDDEN_CATEGORIES_TAB = "Скрытые категории";

    private MarketWindow() {}

    static void open(Addon addon, MarketStore store, CategoryVisibilityStore categoryVisibility,
                     SellerListStore ignoredSellers, SellerListStore favoriteSellers, MarketScanner scanner) {
        if (activeView != null && activeView.window.isOpen()) {
            activeView.window.open();
            activeView.refreshAll();
            return;
        }

        Ui ui = addon.getUi();
        // Wider window gives the categories and controls room instead of making a tall wall of buttons.
        UiWindow window = ui.window("EvoMarket — рынок", 700, 630, true, true);
        WindowView view = new WindowView(store, categoryVisibility, ignoredSellers, favoriteSellers, scanner, ui, window);
        activeView = view;
        window.onClose(() -> {
            if (activeView == view) activeView = null;
        });

        window.getFooter().add(
                ui.button("Обновить", view::refreshAll),
                ui.button("Закрыть", window::close)
        );
        view.build();
        ensureTickHook();
        window.open();
    }

    private static void ensureTickHook() {
        if (tickHookRegistered) return;
        tickHookRegistered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            WindowView view = activeView;
            if (view == null || !view.window.isOpen()) return;

            long now = System.currentTimeMillis();
            if (now - lastAutoRefresh < AUTO_REFRESH_MS) return;
            lastAutoRefresh = now;
            view.refreshIfChanged();
        });
    }

    private static final class WindowView {
        private final MarketStore store;
        private final CategoryVisibilityStore categoryVisibility;
        private final SellerListStore ignoredSellers;
        private final SellerListStore favoriteSellers;
        private final MarketScanner scanner;
        private final Ui ui;
        private final UiWindow window;

        private UiText countText;
        private UiText scannerStatusText;
        private UiText sectionTitle;
        private UiText offersTitle;
        private UiText sortHintText;
        private UiContainer actionTabs;
        private UiContainer sortBar;
        private UiContainer categoryNavigationPanel;
        private UiContainer categoryPanel;
        private UiContainer offersList;
        private ActionFilter actionFilter = ActionFilter.ALL;
        private MarketSortMode sortMode = MarketSortMode.NEWEST;
        private String selectedCategory = MarketCategories.ALL;
        private int renderedSize = -1;
        private String renderedTopKey = "";

        private WindowView(MarketStore store, CategoryVisibilityStore categoryVisibility,
                           SellerListStore ignoredSellers, SellerListStore favoriteSellers,
                           MarketScanner scanner, Ui ui, UiWindow window) {
            this.store = store;
            this.categoryVisibility = categoryVisibility;
            this.ignoredSellers = ignoredSellers;
            this.favoriteSellers = favoriteSellers;
            this.scanner = scanner;
            this.ui = ui;
            this.window = window;
        }

        private void build() {
            UiContainer body = window.getContent();
            body.clear();

            body.add(ui.text("EvoMarket", TextStyle.TITLE));
            countText = ui.text("", TextStyle.SECONDARY)
                    .maxWidth(Math.max(180, window.getBodyWidth() - 28), true);
            body.add(countText);
            scannerStatusText = ui.text("", TextStyle.SECONDARY)
                    .maxWidth(Math.max(180, window.getBodyWidth() - 28), true);
            body.add(scannerStatusText);
            body.add(ui.spacer(1, 5));
            body.add(ui.divider());
            body.add(ui.spacer(1, 6));

            actionTabs = ui.row().spacing(5)
                    .width(Math.max(520, window.getBodyWidth() - 20)).height(31);
            body.add(actionTabs);
            // Visually separate primary navigation from sorting controls.
            body.add(ui.spacer(1, 4));
            body.add(ui.divider());
            body.add(ui.spacer(1, 6));

            sortBar = ui.row().spacing(5)
                    .width(Math.max(440, window.getBodyWidth() - 20)).height(30);
            body.add(sortBar);
            sortHintText = ui.text("", TextStyle.SECONDARY)
                    .maxWidth(Math.max(180, window.getBodyWidth() - 28), true);
            body.add(sortHintText);
            body.add(ui.spacer(1, 8));

            sectionTitle = ui.text("Категории", TextStyle.TITLE);
            body.add(sectionTitle);
            body.add(ui.spacer(1, 3));
            // Static navigation is deliberately separate from dynamically detected item categories.
            // This keeps "Все категории" and "Скрытые" from mixing with generated category names.
            categoryNavigationPanel = ui.row().spacing(6)
                    .width(Math.max(420, window.getBodyWidth() - 20)).height(29);
            body.add(categoryNavigationPanel);
            // Keep navigation tabs visually distinct from generated category buttons.
            body.add(ui.spacer(1, 5));
            body.add(ui.divider());
            body.add(ui.spacer(1, 7));
            categoryPanel = ui.column().spacing(2).fillWidth();
            body.add(categoryPanel);
            body.add(ui.spacer(1, 5));
            body.add(ui.divider());
            body.add(ui.spacer(1, 4));
            offersTitle = ui.text("Объявления", TextStyle.TITLE);
            body.add(offersTitle);
            body.add(ui.spacer(1, 5));
            offersList = ui.column().spacing(7).fillWidth();
            body.add(offersList);

            refreshAll();
        }

        private void chooseAction(ActionFilter next) {
            actionFilter = next;
            selectedCategory = MarketCategories.ALL;
            refreshAll();
        }

        private void chooseCategory(String next) {
            selectedCategory = next;
            renderCategories();
            renderOffers();
        }

        private void chooseSort(MarketSortMode next) {
            sortMode = next;
            renderSortBar();
            renderOffers();
        }

        private void hideCategory(String category) {
            if (categoryVisibility.hide(category)) {
                if (selectedCategory.equals(category)) selectedCategory = MarketCategories.ALL;
                refreshAll();
            }
        }

        private void restoreCategory(String category) {
            if (categoryVisibility.restore(category)) {
                selectedCategory = HIDDEN_CATEGORIES_TAB;
                refreshAll();
            }
        }

        private void toggleFavorite(String seller) {
            if (seller == null || seller.isBlank()) return;
            if (favoriteSellers.contains(seller)) favoriteSellers.remove(seller);
            else favoriteSellers.add(seller);
            refreshAll();
        }

        private void ignoreSeller(String seller) {
            if (seller == null || seller.isBlank()) return;
            ignoredSellers.add(seller);
            favoriteSellers.remove(seller);
            refreshAll();
        }

        private void restoreIgnoredSeller(String seller) {
            if (ignoredSellers.remove(seller)) refreshAll();
        }

        private void removeFavoriteSeller(String seller) {
            if (favoriteSellers.remove(seller)) refreshAll();
        }

        private void refreshAll() {
            refreshCount();
            renderActionTabs();
            renderSortBar();
            renderCategories();
            renderOffers();
            renderedSize = store.size();
            renderedTopKey = topOfferKey();
        }

        private void refreshIfChanged() {
            String topKey = topOfferKey();
            if (renderedSize != store.size() || !renderedTopKey.equals(topKey)) refreshAll();
            else refreshCount();
        }

        private void refreshCount() {
            countText.text("История: " + store.size()
                    + " · покупают: " + store.count(TradeAction.BUY)
                    + " · продают: " + store.count(TradeAction.SELL)
                    + " · избранных: " + favoriteSellers.names().size()
                    + " · в игноре: " + ignoredSellers.names().size());
            scannerStatusText.text(scanner.statusLine());
        }

        private void renderActionTabs() {
            actionTabs.clear();
            actionTabs.add(actionButton(ActionFilter.ALL, "Все"));
            actionTabs.add(actionButton(ActionFilter.BUY, "Покупают"));
            actionTabs.add(actionButton(ActionFilter.SELL, "Продают"));
            actionTabs.add(actionButton(ActionFilter.FAVORITES, "★ Избранные"));
            actionTabs.add(actionButton(ActionFilter.IGNORED, "Игнор"));
        }

        private UiButton actionButton(ActionFilter value, String label) {
            String display = actionFilter == value ? "§a✓ " + label : label;
            int width = switch (value) {
                case ALL -> 72;
                case BUY, SELL -> 96;
                case FAVORITES -> 105;
                case IGNORED -> 94;
            };
            return ui.button(display, () -> chooseAction(value)).width(width).height(26);
        }

        private void renderSortBar() {
            sortBar.clear();
            sortBar.add(ui.button(sortMode == MarketSortMode.NEWEST ? "§a✓ Новые" : "Новые",
                    () -> chooseSort(MarketSortMode.NEWEST)).width(86).height(26));
            sortBar.add(ui.button(sortMode == MarketSortMode.PRICE_ASC ? "§a✓ Цена ↑" : "Цена ↑",
                    () -> chooseSort(MarketSortMode.PRICE_ASC)).width(92).height(26));
            sortBar.add(ui.button(sortMode == MarketSortMode.PRICE_DESC ? "§a✓ Цена ↓" : "Цена ↓",
                    () -> chooseSort(MarketSortMode.PRICE_DESC)).width(92).height(26));
            sortHintText.text(sortMode == MarketSortMode.NEWEST ? "Сортировка по времени: сначала новые объявления."
                    : "Цены группируются отдельно по способу расчёта; ЛК и игровая валюта между собой не сравниваются.");
        }

        private void renderCategories() {
            categoryNavigationPanel.clear();
            categoryPanel.clear();

            if (actionFilter == ActionFilter.IGNORED) {
                sectionTitle.text("Игнор-лист игроков");
                offersTitle.text("Скрытые предложения");
                categoryNavigationPanel.add(ui.button("← К рынку", () -> chooseAction(ActionFilter.ALL)).width(105).height(26));
                List<String> ignored = ignoredSellers.names();
                if (!ignored.isEmpty()) {
                    categoryNavigationPanel.add(ui.button("§cОчистить всё", () -> {
                        ignoredSellers.clear();
                        refreshAll();
                    }).width(100).height(26));
                }
                if (ignored.isEmpty()) {
                    categoryPanel.add(ui.text("Игнор-лист пуст. Нажми «Игнорировать игрока» в карточке объявления, чтобы скрыть его предложения.", TextStyle.SECONDARY)
                            .maxWidth(Math.max(180, window.getBodyWidth() - 28), true));
                } else {
                    // Keep the action directly beside the player name, like category chips.
                    // The bulk action lives in the navigation row to avoid creating an extra line.
                    for (String seller : ignored) {
                        UiContainer row = ui.row().spacing(4).height(27);
                        row.add(ui.text(seller, TextStyle.BODY)
                                .maxWidth(Math.max(180, window.getBodyWidth() - 150), true));
                        row.add(ui.button("§c×", () -> restoreIgnoredSeller(seller)).width(22).height(25));
                        categoryPanel.add(row);
                    }
                }
                return;
            }

            if (actionFilter == ActionFilter.FAVORITES) {
                sectionTitle.text("Избранные продавцы");
                offersTitle.text("Объявления избранных продавцов");
                categoryNavigationPanel.add(ui.button("← К рынку", () -> chooseAction(ActionFilter.ALL)).width(105).height(26));
                List<String> favorites = favoriteSellers.names();
                if (favorites.isEmpty()) {
                    categoryPanel.add(ui.text("Пока нет избранных продавцов. Добавь игрока кнопкой «☆ В избранное» в любой карточке.", TextStyle.SECONDARY)
                            .maxWidth(Math.max(180, window.getBodyWidth() - 28), true));
                } else {
                    // Compact inline remove control: do not reserve a large blank label column.
                    for (String seller : favorites) {
                        UiContainer row = ui.row().spacing(4).height(27);
                        row.add(ui.text("★ " + seller, TextStyle.BODY)
                                .maxWidth(Math.max(180, window.getBodyWidth() - 150), true));
                        row.add(ui.button("§c×", () -> removeFavoriteSeller(seller)).width(22).height(25));
                        categoryPanel.add(row);
                    }
                }
                return;
            }

            boolean hiddenTab = HIDDEN_CATEGORIES_TAB.equals(selectedCategory);
            sectionTitle.text(hiddenTab ? "Скрытые категории" : "Категории рынка");
            offersTitle.text(hiddenTab ? "Управление скрытыми категориями" : "Объявления");

            List<MarketOffer> all = scopedOffers();
            List<String> detected = MarketCategories.available(all, null);
            List<String> hidden = categoryVisibility.hiddenCategories();
            List<String> visibleCategories = detected.stream()
                    .filter(category -> !categoryVisibility.isHidden(category))
                    .toList();

            // Fixed navigation row: these two controls never become auto-generated categories.
            categoryNavigationPanel.add(ui.button(
                    MarketCategories.ALL.equals(selectedCategory) ? "§a✓ Все категории" : "Все категории",
                    () -> chooseCategory(MarketCategories.ALL)).width(142).height(26));
            categoryNavigationPanel.add(ui.button(
                    hiddenTab ? "§6✓ Скрытые (" + hidden.size() + ")" : "Скрытые (" + hidden.size() + ")",
                    () -> chooseCategory(HIDDEN_CATEGORIES_TAB)).width(150).height(26));
            if (hiddenTab && !hidden.isEmpty()) {
                categoryNavigationPanel.add(ui.button("§cВернуть все", () -> {
                    categoryVisibility.restoreAll();
                    selectedCategory = MarketCategories.ALL;
                    refreshAll();
                }).width(96).height(26));
            }

            if (hiddenTab) {
                // Add breathing room after the fixed navigation + divider before the section header.
                categoryPanel.add(ui.spacer(1, 7));
                categoryPanel.add(ui.text("Скрытые категории", TextStyle.TITLE));
                categoryPanel.add(ui.spacer(1, 3));
                categoryPanel.add(ui.text("Нажми «Вернуть», чтобы снова показывать категорию в списке рынка. История объявлений не удаляется.", TextStyle.SECONDARY)
                        .maxWidth(Math.max(180, window.getBodyWidth() - 28), true));
                if (hidden.isEmpty()) {
                    categoryPanel.add(ui.spacer(1, 5));
                    categoryPanel.add(ui.text("Список пуст.", TextStyle.SECONDARY));
                    return;
                }

                // Hidden entries use a compact inline X control just like visible category chips.
                // Bulk restore is in the navigation row above, so it adds no extra row here.
                categoryPanel.add(ui.spacer(1, 4));
                for (String category : hidden) {
                    UiContainer row = ui.row().spacing(4).height(27);
                    row.add(ui.text(category, TextStyle.BODY)
                            .maxWidth(Math.max(180, window.getBodyWidth() - 150), true));
                    row.add(ui.button("§c×", () -> restoreCategory(category)).width(22).height(25));
                    categoryPanel.add(row);
                }
                return;
            }

            if (!visibleCategories.contains(selectedCategory)
                    && !MarketCategories.ALL.equals(selectedCategory)) {
                selectedCategory = MarketCategories.ALL;
            }
            if (visibleCategories.isEmpty()) {
                categoryPanel.add(ui.text("Категорий пока нет. Они появятся здесь, когда сканер распознает торговые предложения.", TextStyle.SECONDARY)
                        .maxWidth(Math.max(180, window.getBodyWidth() - 28), true));
                return;
            }

            for (int start = 0; start < visibleCategories.size(); start += BUTTONS_PER_ROW) {
                UiContainer row = ui.row().spacing(6)
                        .width(Math.max(520, window.getBodyWidth() - 20)).height(29);
                int end = Math.min(start + BUTTONS_PER_ROW, visibleCategories.size());
                for (int i = start; i < end; i++) {
                    String category = visibleCategories.get(i);
                    String label = selectedCategory.equals(category) ? "§a✓ " + category : category;
                    UiContainer cell = ui.row().spacing(2).width(158).height(27);
                    cell.add(ui.button(label, () -> chooseCategory(category)).width(132).height(25));
                    cell.add(ui.button("×", () -> hideCategory(category)).width(22).height(25));
                    row.add(cell);
                }
                categoryPanel.add(row);
                categoryPanel.add(ui.spacer(1, 2));
            }
        }

        private List<MarketOffer> scopedOffers() {
            List<MarketOffer> result = new ArrayList<>();
            for (MarketOffer offer : store.latest(500)) {
                if (MarketCategories.isFillerItem(offer.item())) continue;
                String seller = offer.seller();
                if (seller != null && ignoredSellers.contains(seller)) continue;
                if (actionFilter == ActionFilter.BUY && offer.action() != TradeAction.BUY) continue;
                if (actionFilter == ActionFilter.SELL && offer.action() != TradeAction.SELL) continue;
                if (actionFilter == ActionFilter.FAVORITES && (seller == null || !favoriteSellers.contains(seller))) continue;
                if (actionFilter == ActionFilter.IGNORED) continue;
                result.add(offer);
            }
            return result;
        }

        private void renderOffers() {
            offersList.clear();
            if (actionFilter == ActionFilter.IGNORED) {
                offersList.add(ui.text("Предложения игроков из игнор-листа скрыты только в EvoMarket. Обычный игровой чат модификация не меняет.", TextStyle.SECONDARY)
                        .maxWidth(Math.max(180, window.getBodyWidth() - 28), true));
                return;
            }
            if (HIDDEN_CATEGORIES_TAB.equals(selectedCategory)) {
                offersList.add(ui.text("Здесь можно восстановить категории. История объявлений при скрытии не удаляется.", TextStyle.SECONDARY)
                        .maxWidth(Math.max(180, window.getBodyWidth() - 28), true));
                return;
            }

            List<MarketOffer> offers = scopedOffers();
            if (actionFilter != ActionFilter.FAVORITES && !MarketCategories.ALL.equals(selectedCategory)) {
                offers.removeIf(offer -> !offer.categories().contains(selectedCategory));
            }
            List<MarketOffer> sortedOffers = MarketOfferSorter.sorted(offers, sortMode);

            int shown = 0;
            double bodyWidth = Math.max(180.0, window.getBodyWidth() - 28.0);
            for (MarketOffer offer : sortedOffers) {
                if (shown >= MAX_RENDERED_OFFERS) break;
                String actionLabel = offer.action() == TradeAction.BUY ? "ПОКУПАЮТ" : "ПРОДАЮТ";
                String color = offer.action() == TradeAction.BUY ? "§a" : "§c";
                String price = formatOfferPrice(offer);
                String time = TIME.format(offer.timestamp().atZone(ZoneId.systemDefault()));

                UiContainer card = ui.card(false).padding(10, 8).spacing(4).fillWidth();
                card.add(ui.text(color + actionLabel + " §8· " + time, TextStyle.SECONDARY));
                card.add(ui.text(offer.displayItem(), TextStyle.TITLE).maxWidth(bodyWidth, true));

                String paymentLabel = offer.paymentLabel();
                if (paymentLabel != null && !paymentLabel.isBlank()) {
                    card.add(ui.text("Расчёт: " + paymentLabel, TextStyle.SECONDARY).maxWidth(bodyWidth, true));
                }

                String categoryLabel = "Категории: " + String.join(", ", offer.categories());
                if (categoryLabel.length() > 96) categoryLabel = categoryLabel.substring(0, 93) + "…";
                if (!offer.categories().isEmpty()) card.add(ui.text(categoryLabel, TextStyle.SECONDARY).maxWidth(bodyWidth, true));

                String sourceMessage = chatMessage(offer.rawText());
                if (!sourceMessage.isBlank()) {
                    card.add(ui.text("Сообщение: " + sourceMessage, TextStyle.SECONDARY).maxWidth(bodyWidth, true));
                }
                card.add(ui.text("Цена: " + price + "  |  Игрок: " + offer.seller(), TextStyle.BODY)
                        .maxWidth(bodyWidth, true));

                String seller = offer.seller();
                if (seller != null && !seller.isBlank()) {
                    UiContainer sellerActions = ui.row().spacing(6)
                            .width(Math.max(520, window.getBodyWidth() - 28)).height(27);
                    boolean favorite = favoriteSellers.contains(seller);
                    sellerActions.add(ui.button(favorite ? "★ Избран" : "☆ Избранное",
                            () -> toggleFavorite(seller)).width(96).height(24));
                    sellerActions.add(ui.button("Игнор", () -> ignoreSeller(seller)).width(52).height(24));
                    card.add(sellerActions);
                }

                offersList.add(card);
                offersList.add(ui.spacer(1, 5));
                shown++;
            }

            if (shown == 0) {
                offersList.add(ui.text(actionFilter == ActionFilter.FAVORITES
                                ? "Пока нет объявлений от избранных продавцов. Их новые предложения появятся здесь автоматически."
                                : "Пока нет объявлений в этой категории. Новые предложения появятся автоматически, когда их напишут в чат.",
                        TextStyle.SECONDARY).maxWidth(Math.max(180, window.getBodyWidth() - 28), true));
            } else if (sortedOffers.size() > shown) {
                offersList.add(ui.text("Показаны первые " + shown + " из " + sortedOffers.size() + " предложений. Уточни категорию, чтобы сузить список.", TextStyle.SECONDARY)
                        .maxWidth(Math.max(180, window.getBodyWidth() - 28), true));
            }
        }

        private String topOfferKey() {
            List<MarketOffer> top = store.latest(1);
            if (top.isEmpty()) return "";
            MarketOffer first = top.get(0);
            return first.timestamp() + "|" + first.rawText();
        }
    }

    private static String chatMessage(String rawText) {
        if (rawText == null || rawText.isBlank()) return "";
        String cleaned = rawText.replaceAll("(?i)§[0-9A-FK-OR]", " ").replaceAll("\\s+", " ").trim();
        Matcher prefix = Pattern.compile("(?iu)\\s\\[\\d{1,4}]\\s*:\\s*").matcher(cleaned);
        if (prefix.find() && prefix.end() < cleaned.length()) return cleaned.substring(prefix.end()).trim();
        return cleaned;
    }

    private static String formatOfferPrice(MarketOffer offer) {
        if (offer.category().equals("Игровая валюта")) {
            Matcher bundle = CURRENCY_LK_BUNDLE.matcher(offer.rawText());
            if (bundle.find()) {
                String stockUnit = isTrillion(bundle.group(2)) ? "T" : "B";
                String rateUnit = isTrillion(bundle.group(4)) ? "T" : "B";
                return "лот " + bundle.group(1).replace(',', '.') + " " + stockUnit
                        + " · " + bundle.group(3).replace(',', '.') + " " + rateUnit
                        + " за " + bundle.group(5).replace(',', '.') + " ЛК";
            }
            Matcher single = CURRENCY_LK_SINGLE.matcher(offer.rawText());
            if (single.find()) {
                String unit = isTrillion(single.group(2)) ? "T" : "B";
                return single.group(1).replace(',', '.') + " " + unit
                        + " за " + single.group(3).replace(',', '.') + " ЛК";
            }
            Matcher rate = CURRENCY_RATE.matcher(offer.rawText());
            if (rate.find()) {
                String unit = isTrillion(rate.group(2)) ? "T" : "B";
                return "курс " + rate.group(1).replace(',', '.') + " " + unit + " к " + rate.group(3).replace(',', '.');
            }
            Matcher compactLkRate = COMPACT_LK_CURRENCY_RATE.matcher(offer.rawText());
            if (compactLkRate.find()) {
                return "курс " + compactLkRate.group(1).replace(',', '.') + " к "
                        + compactLkRate.group(2).replace(',', '.') + " (ЛК)";
            }
        }
        if (offer.hasPrice()) {
            String formatted = formatPrice(offer.price());
            String payment = offer.paymentLabel();
            if ("ЛК".equals(payment)) return formatted + " ЛК";
            if ("Игровая валюта".equals(payment)) return formatted + " игровой валюты";
            if ("ЛК / игровая валюта".equals(payment)) return formatted + " (ЛК / игровая валюта)";
            return formatted;
        }
        return "цена не распознана";
    }

    private static boolean isTrillion(String suffix) {
        return suffix != null && (suffix.toLowerCase(Locale.ROOT).startsWith("т")
                || suffix.equalsIgnoreCase("t"));
    }

    private static String formatPrice(BigDecimal value) {
        BigDecimal trillion = new BigDecimal("1000000000000");
        BigDecimal billion = new BigDecimal("1000000000");
        if (value.abs().compareTo(trillion) >= 0) {
            return value.divide(trillion, 6, java.math.RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString().replace('.', ',') + " T";
        }
        if (value.abs().compareTo(billion) >= 0) {
            return value.divide(billion, 6, java.math.RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString().replace('.', ',') + " B";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private enum ActionFilter { ALL, BUY, SELL, FAVORITES, IGNORED }
}
