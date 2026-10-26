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
import ru.dargen.evoplus.api.ui.UiInput;
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
            "(?iu)(?<![\\p{L}\\p{N}])(\\d+(?:[.,]\\d+)?)\\s*([тtбbв])\\s*,?\\s*по\\s*(\\d+(?:[.,]\\d+)?)\\s*([тtбbв])\\s*за\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:лк|lc|личн\\p{L}*\\s+кабинет\\p{L}*)(?![\\p{L}])"
    );
    private static final Pattern CURRENCY_LK_SINGLE = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])(\\d+(?:[.,]\\d+)?)\\s*([тtбbв])\\s*(?:,?\\s*)за\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:лк|lc|личн\\p{L}*\\s+кабинет\\p{L}*)(?![\\p{L}])"
    );
    private static final long AUTO_REFRESH_MS = 600;
    private static final int PAGE_SIZE = 20;
    

    private static WindowView activeView;
    private static boolean tickHookRegistered;
    private static long lastAutoRefresh;

    private static final String HIDDEN_CATEGORIES_TAB = "Скрытые категории";

    private MarketWindow() {}

    static boolean isOpen() { return activeView != null && activeView.window.isOpen(); }

    static void open(Addon addon, MarketStore store, CategoryVisibilityStore categoryVisibility,
                     SellerListStore ignoredSellers, SellerListStore favoriteSellers, MarketScanner scanner) {
        if (activeView != null && activeView.window.isOpen()) {
            activeView.requestRefresh();
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
                ui.button("Обновить", view::requestRefresh),
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
            if (view == null) return;
            if (!view.window.isOpen()) {
                if (activeView == view) activeView = null;
                return;
            }
            if (view.dirty || view.renderedWidth != view.contentWidth()) {
                view.refreshAll();
                return;
            }

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
        private long renderedRevision = -1;
        private double renderedWidth = -1;
        private boolean dirty;
        private int page;
        private String query = "";
        private UiInput search;
        private UiContainer pagination;
        private List<MarketOffer> snapshot = List.of();
        private UiContainer navigationRow;
        private double navigationUsed;

        private double contentWidth() { return Math.max(80, window.getBodyWidth() - 28); }
        private void requestRefresh() { dirty = true; }
        private void resetPage() { page = 0; requestRefresh(); }
        private void addNavigation(UiButton button) {
            double width = Math.min(button.getWidth(), contentWidth());
            if (navigationRow == null || navigationUsed + width > contentWidth()) {
                navigationRow = ui.row().spacing(6).height(29).fillWidth();
                categoryNavigationPanel.add(navigationRow);
                navigationUsed = 0;
            }
            navigationRow.add(button.width(width));
            navigationUsed += width + 6;
        }

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
                    .maxWidth(contentWidth(), true);
            body.add(countText);
            scannerStatusText = ui.text("", TextStyle.SECONDARY)
                    .maxWidth(contentWidth(), true);
            body.add(scannerStatusText);
            body.add(ui.spacer(1, 5));
            body.add(ui.divider());
            body.add(ui.spacer(1, 6));

            actionTabs = ui.column().spacing(4).fillWidth();
            body.add(actionTabs);
            // Visually separate primary navigation from sorting controls.
            body.add(ui.spacer(1, 4));
            body.add(ui.divider());
            body.add(ui.spacer(1, 6));

            sortBar = ui.row().spacing(5).fillWidth().height(30);
            body.add(sortBar);
            sortHintText = ui.text("", TextStyle.SECONDARY)
                    .maxWidth(contentWidth(), true);
            body.add(sortHintText);
            body.add(ui.spacer(1, 8));

            search = ui.input(query, "Поиск: предмет, игрок, текст сообщения")
                    .maxLength(120).fillWidth().onChange(value -> {
                        query = value.trim().toLowerCase(Locale.ROOT);
                        resetPage();
                    });
            body.add(search);
            body.add(ui.button("Сбросить поиск", () -> {
                search.value("");
                query = "";
                resetPage();
            }).width(120).height(24));
            sectionTitle = ui.text("Категории", TextStyle.TITLE);
            body.add(sectionTitle);
            body.add(ui.spacer(1, 3));
            // Static navigation is deliberately separate from dynamically detected item categories.
            // This keeps "Все категории" and "Скрытые" from mixing with generated category names.
            categoryNavigationPanel = ui.column().spacing(3).fillWidth();
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
            pagination = ui.row().spacing(6).height(28).fillWidth();
            body.add(pagination);
            offersList = ui.column().spacing(7).fillWidth();
            body.add(offersList);

            refreshAll();
        }

        private void chooseAction(ActionFilter next) {
            actionFilter = next;
            selectedCategory = MarketCategories.ALL;
            resetPage();
        }

        private void chooseCategory(String next) {
            selectedCategory = next;
            resetPage();
        }

        private void chooseSort(MarketSortMode next) {
            sortMode = next;
            resetPage();
        }

        private void hideCategory(String category) {
            if (categoryVisibility.hide(category)) {
                if (selectedCategory.equals(category)) selectedCategory = MarketCategories.ALL;
                resetPage();
            }
        }

        private void restoreCategory(String category) {
            if (categoryVisibility.restore(category)) {
                selectedCategory = HIDDEN_CATEGORIES_TAB;
                resetPage();
            }
        }

        private void toggleFavorite(String seller) {
            if (seller == null || seller.isBlank()) return;
            if (favoriteSellers.contains(seller)) favoriteSellers.remove(seller);
            else favoriteSellers.add(seller);
            resetPage();
        }

        private void ignoreSeller(String seller) {
            if (seller == null || seller.isBlank()) return;
            ignoredSellers.add(seller);
            favoriteSellers.remove(seller);
            resetPage();
        }

        private void restoreIgnoredSeller(String seller) {
            if (ignoredSellers.remove(seller)) resetPage();
        }

        private void removeFavoriteSeller(String seller) {
            if (favoriteSellers.remove(seller)) resetPage();
        }

        private void refreshAll() {
            dirty = false;
            renderedRevision = store.revision();
            snapshot = store.latest(500);
            renderedWidth = contentWidth();
            countText.maxWidth(contentWidth(), true);
            scannerStatusText.maxWidth(contentWidth(), true);
            sortHintText.maxWidth(contentWidth(), true);
            refreshCount();
            renderActionTabs();
            renderSortBar();
            renderCategories();
            renderOffers();

        }

        private void refreshIfChanged() {
            if (renderedRevision != store.revision()) refreshAll();
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
            UiContainer row = null;
            double used = 0;
            String[] labels = {"Все", "Покупают", "Продают", "★ Избранные", "Игнор"};
            for (ActionFilter filter : ActionFilter.values()) {
                UiButton button = actionButton(filter, labels[filter.ordinal()]);
                double width = Math.min(button.getWidth(), contentWidth());
                if (row == null || used + width > contentWidth()) {
                    row = ui.row().spacing(5).height(28).fillWidth();
                    actionTabs.add(row);
                    used = 0;
                }
                row.add(button.width(width));
                used += width + 5;
            }
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
                    () -> chooseSort(MarketSortMode.NEWEST)).width((contentWidth() - 10) / 3).height(26));
            sortBar.add(ui.button(sortMode == MarketSortMode.PRICE_ASC ? "§a✓ Цена ↑" : "Цена ↑",
                    () -> chooseSort(MarketSortMode.PRICE_ASC)).width((contentWidth() - 10) / 3).height(26));
            sortBar.add(ui.button(sortMode == MarketSortMode.PRICE_DESC ? "§a✓ Цена ↓" : "Цена ↓",
                    () -> chooseSort(MarketSortMode.PRICE_DESC)).width((contentWidth() - 10) / 3).height(26));
            sortHintText.text(sortMode == MarketSortMode.NEWEST ? "Сортировка по времени: сначала новые объявления."
                    : "Цена указанного лота; ЛК и игровая валюта отдельно. Курсы и смешанная оплата — в конце группы.");
        }

        private void renderCategories() {
            categoryNavigationPanel.clear();
            navigationRow = null;
            navigationUsed = 0;
            categoryPanel.clear();

            if (actionFilter == ActionFilter.IGNORED) {
                sectionTitle.text("Игнор-лист игроков");
                offersTitle.text("Скрытые предложения");
                addNavigation(ui.button("← К рынку", () -> chooseAction(ActionFilter.ALL)).width(105).height(26));
                List<String> ignored = ignoredSellers.names();
                if (!ignored.isEmpty()) {
                    addNavigation(ui.button("§cОчистить всё", () -> {
                        ignoredSellers.clear();
                        resetPage();
                    }).width(100).height(26));
                }
                if (ignored.isEmpty()) {
                    categoryPanel.add(ui.text("Игнор-лист пуст. Нажми «Игнорировать игрока» в карточке объявления, чтобы скрыть его предложения.", TextStyle.SECONDARY)
                            .maxWidth(contentWidth(), true));
                } else {
                    // Keep the action directly beside the player name, like category chips.
                    // The bulk action lives in the navigation row to avoid creating an extra line.
                    for (String seller : ignored) {
                        UiContainer row = ui.row().spacing(4).height(27);
                        row.add(ui.text(seller, TextStyle.BODY).tooltip(seller)
                                .maxWidth(Math.max(40, contentWidth() - 90), false));
                        row.add(ui.button("§c×", () -> restoreIgnoredSeller(seller)).width(22).height(25));
                        categoryPanel.add(row);
                    }
                }
                return;
            }

            if (actionFilter == ActionFilter.FAVORITES) {
                sectionTitle.text("Избранные продавцы");
                offersTitle.text("Объявления избранных продавцов");
                addNavigation(ui.button("← К рынку", () -> chooseAction(ActionFilter.ALL)).width(105).height(26));
                List<String> favorites = favoriteSellers.names();
                if (favorites.isEmpty()) {
                    categoryPanel.add(ui.text("Пока нет избранных продавцов. Добавь игрока кнопкой «☆ В избранное» в любой карточке.", TextStyle.SECONDARY)
                            .maxWidth(contentWidth(), true));
                } else {
                    // Compact inline remove control: do not reserve a large blank label column.
                    for (String seller : favorites) {
                        UiContainer row = ui.row().spacing(4).height(27);
                        row.add(ui.text("★ " + seller, TextStyle.BODY).tooltip(seller)
                                .maxWidth(Math.max(40, contentWidth() - 90), false));
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
            addNavigation(ui.button(
                    MarketCategories.ALL.equals(selectedCategory) ? "§a✓ Все категории" : "Все категории",
                    () -> chooseCategory(MarketCategories.ALL)).width(142).height(26));
            addNavigation(ui.button(
                    hiddenTab ? "§6✓ Скрытые (" + hidden.size() + ")" : "Скрытые (" + hidden.size() + ")",
                    () -> chooseCategory(HIDDEN_CATEGORIES_TAB)).width(150).height(26));
            if (hiddenTab && !hidden.isEmpty()) {
                addNavigation(ui.button("§cВернуть все", () -> {
                    categoryVisibility.restoreAll();
                    selectedCategory = MarketCategories.ALL;
                    resetPage();
                }).width(96).height(26));
            }

            if (hiddenTab) {
                // Add breathing room after the fixed navigation + divider before the section header.
                categoryPanel.add(ui.spacer(1, 7));
                categoryPanel.add(ui.text("Скрытые категории", TextStyle.TITLE));
                categoryPanel.add(ui.spacer(1, 3));
                categoryPanel.add(ui.text("Нажми «Вернуть», чтобы снова показывать категорию в списке рынка. История объявлений не удаляется.", TextStyle.SECONDARY)
                        .maxWidth(contentWidth(), true));
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
                    row.add(ui.text(category, TextStyle.BODY).tooltip(category)
                            .maxWidth(Math.max(40, contentWidth() - 90), false));
                    row.add(ui.button("Вернуть", () -> restoreCategory(category)).width(65).height(25));
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
                        .maxWidth(contentWidth(), true));
                return;
            }

            int columns = Math.max(1, Math.min(4, (int) ((contentWidth() + 6) / 164)));
            double cellWidth = (contentWidth() - (columns - 1) * 6) / columns;
            for (int start = 0; start < visibleCategories.size(); start += columns) {
                UiContainer row = ui.row().spacing(6)
                        .width(contentWidth()).height(29);
                int end = Math.min(start + columns, visibleCategories.size());
                for (int i = start; i < end; i++) {
                    String category = visibleCategories.get(i);
                    String label = selectedCategory.equals(category) ? "§a✓ " + category : category;
                    UiContainer cell = ui.row().spacing(2).width(cellWidth).height(27);
                    cell.add(ui.button(shortLabel(label, cellWidth - 30), () -> chooseCategory(category))
                            .tooltip(category).width(cellWidth - 30).height(25));
                    cell.add(ui.button("×", () -> hideCategory(category)).tooltip("Скрыть категорию: " + category).width(26).height(25));
                    row.add(cell);
                }
                categoryPanel.add(row);
                categoryPanel.add(ui.spacer(1, 2));
            }
        }

        private List<MarketOffer> scopedOffers() {
            List<MarketOffer> result = new ArrayList<>();
            for (MarketOffer offer : snapshot) {
                if (MarketCategories.isFillerItem(offer.item())) continue;
                String seller = offer.seller();
                if (seller != null && ignoredSellers.contains(seller)) continue;
                if (actionFilter == ActionFilter.BUY && offer.action() != TradeAction.BUY) continue;
                if (actionFilter == ActionFilter.SELL && offer.action() != TradeAction.SELL) continue;
                if (actionFilter == ActionFilter.FAVORITES && (seller == null || !favoriteSellers.contains(seller))) continue;
                if (actionFilter == ActionFilter.IGNORED) continue;
                if (!query.isEmpty() && !(offer.item() + " " + offer.displayItem() + " "
                        + seller + " " + offer.rawText()).toLowerCase(Locale.ROOT).contains(query)) continue;
                result.add(offer);
            }
            return result;
        }

        private void renderOffers() {
            offersList.clear();
            pagination.clear();
            if (actionFilter == ActionFilter.IGNORED) {
                offersList.add(ui.text("Предложения игроков из игнор-листа скрыты только в EvoMarket. Обычный игровой чат модификация не меняет.", TextStyle.SECONDARY)
                        .maxWidth(contentWidth(), true));
                return;
            }
            if (HIDDEN_CATEGORIES_TAB.equals(selectedCategory)) {
                offersList.add(ui.text("Здесь можно восстановить категории. История объявлений при скрытии не удаляется.", TextStyle.SECONDARY)
                        .maxWidth(contentWidth(), true));
                return;
            }

            List<MarketOffer> offers = scopedOffers();
            if (actionFilter != ActionFilter.FAVORITES && !MarketCategories.ALL.equals(selectedCategory)) {
                offers.removeIf(offer -> !offer.categories().contains(selectedCategory));
            }
            List<MarketOffer> sortedOffers = MarketOfferSorter.sorted(offers, sortMode);

            int pages = Math.max(1, (sortedOffers.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            page = Math.max(0, Math.min(page, pages - 1));
            offersTitle.text("Объявления: " + sortedOffers.size());
            if (page > 0) pagination.add(ui.button("←", () -> { page--; requestRefresh(); }).width(30).height(24));
            pagination.add(ui.text((page + 1) + " / " + pages, TextStyle.SECONDARY));
            if (page + 1 < pages) pagination.add(ui.button("→", () -> { page++; requestRefresh(); }).width(30).height(24));
            int shown = 0;
            double bodyWidth = Math.max(40, contentWidth() - 20);
            for (MarketOffer offer : sortedOffers.subList(page * PAGE_SIZE, Math.min(sortedOffers.size(), (page + 1) * PAGE_SIZE))) {
                String actionLabel = offer.action() == TradeAction.BUY ? "ПОКУПАЮТ" : "ПРОДАЮТ";
                String color = offer.action() == TradeAction.BUY ? "§a" : "§c";
                String price = formatOfferPrice(offer);
                String time = TIME.format(offer.timestamp().atZone(ZoneId.systemDefault()));

                UiContainer card = ui.card(false).padding(10, 8).spacing(4).fillWidth();
                card.add(ui.text(color + actionLabel + " §8· " + time, TextStyle.SECONDARY));
                card.add(ui.text(offer.item(), TextStyle.TITLE).maxWidth(bodyWidth, true));

                String paymentLabel = offer.paymentLabel();
                if (paymentLabel != null && !paymentLabel.isBlank()) {
                    card.add(ui.text("Расчёт: " + paymentLabel, TextStyle.SECONDARY).maxWidth(bodyWidth, true));
                }

                String categoryLabel = "Категории: " + String.join(", ", offer.categories());
                if (categoryLabel.length() > 96) categoryLabel = categoryLabel.substring(0, 93) + "…";
                if (!offer.categories().isEmpty()) card.add(ui.text(categoryLabel, TextStyle.SECONDARY).maxWidth(bodyWidth, true));

                String sourceMessage = chatMessage(offer.rawText());
                if (sourceMessage.length() > 260) sourceMessage = sourceMessage.substring(0, 257) + "…";
                if (!sourceMessage.isBlank()) {
                    card.add(ui.text("Сообщение: " + sourceMessage, TextStyle.SECONDARY).maxWidth(bodyWidth, true));
                }
                card.add(ui.text("Цена: " + price + "  |  Игрок: " + offer.seller(), TextStyle.BODY)
                        .maxWidth(bodyWidth, true));

                String seller = offer.seller();
                if (seller != null && !seller.isBlank()) {
                    UiContainer sellerActions = ui.row().spacing(6)
                            .width(contentWidth()).height(27);
                    boolean favorite = favoriteSellers.contains(seller);
                    sellerActions.add(ui.button(favorite ? "★ Избран" : "☆ Избранное",
                            () -> toggleFavorite(seller)).width(96).height(24));
                    sellerActions.add(ui.button("Игнор", () -> ignoreSeller(seller)).width(52).height(24));
                    card.add(sellerActions);
                }

                card.add(ui.button("Копировать объявление", () -> ui.copyToClipboard(offer.rawText()))
                        .width(Math.min(155, bodyWidth)).height(24));
                offersList.add(card);
                offersList.add(ui.spacer(1, 5));
                shown++;
            }

            if (shown == 0) {
                offersList.add(ui.text(actionFilter == ActionFilter.FAVORITES
                                ? "Пока нет объявлений от избранных продавцов. Их новые предложения появятся здесь автоматически."
                                : (query.isEmpty() ? "Пока нет объявлений в этой категории. Новые предложения появятся автоматически." : "Ничего не найдено. Сбрось поиск или выбери другую категорию."),
                        TextStyle.SECONDARY).maxWidth(contentWidth(), true));
            } else if (sortedOffers.size() > shown) {
                offersList.add(ui.text("Показаны " + (page * PAGE_SIZE + 1) + "–" + (page * PAGE_SIZE + shown) + " из " + sortedOffers.size() + ". Стрелки над списком переключают страницы.", TextStyle.SECONDARY)
                        .maxWidth(contentWidth(), true));
            }
        }

    }

    private static String shortLabel(String label, double width) {
        int limit = Math.max(3, (int) (width / 7));
        String plain = label.replaceAll("(?i)§[0-9A-FK-OR]", "");
        return plain.length() <= limit ? label : plain.substring(0, limit - 1) + "…";
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
            Matcher bundle = CURRENCY_LK_BUNDLE.matcher(offer.tradeContext());
            if (bundle.find()) {
                String stockUnit = isTrillion(bundle.group(2)) ? "T" : "B";
                String rateUnit = isTrillion(bundle.group(4)) ? "T" : "B";
                return "лот " + bundle.group(1).replace(',', '.') + " " + stockUnit
                        + " · " + bundle.group(3).replace(',', '.') + " " + rateUnit
                        + " за " + bundle.group(5).replace(',', '.') + " ЛК";
            }
            Matcher single = CURRENCY_LK_SINGLE.matcher(offer.tradeContext());
            if (single.find()) {
                String unit = isTrillion(single.group(2)) ? "T" : "B";
                return single.group(1).replace(',', '.') + " " + unit
                        + " за " + single.group(3).replace(',', '.') + " ЛК";
            }
            Matcher rate = CURRENCY_RATE.matcher(offer.tradeContext());
            if (rate.find()) {
                String unit = isTrillion(rate.group(2)) ? "T" : "B";
                return "курс " + rate.group(1).replace(',', '.') + " " + unit + " к " + rate.group(3).replace(',', '.');
            }
            Matcher compactLkRate = COMPACT_LK_CURRENCY_RATE.matcher(offer.tradeContext());
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
            if ("ЛК / игровая валюта".equals(payment)) return "ЛК / игровая валюта — см. сообщение";
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
