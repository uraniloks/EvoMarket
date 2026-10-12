package ru.boss.evomarket.evoplus;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import ru.boss.evomarket.core.CategoryVisibilityStore;
import ru.boss.evomarket.core.MarketCategories;
import ru.boss.evomarket.core.MarketOffer;
import ru.boss.evomarket.core.MarketStore;
import ru.boss.evomarket.core.TradeAction;
import ru.dargen.evoplus.api.addon.Addon;
import ru.dargen.evoplus.api.ui.TextStyle;
import ru.dargen.evoplus.api.ui.Ui;
import ru.dargen.evoplus.api.ui.UiContainer;
import ru.dargen.evoplus.api.ui.UiButton;
import ru.dargen.evoplus.api.ui.UiText;
import ru.dargen.evoplus.api.ui.UiWindow;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Main EvoMarket UI: action tabs, automatically inferred item categories and offers. */
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
    private static final int BUTTONS_PER_ROW = 3;

    private static WindowView activeView;
    private static boolean tickHookRegistered;
    private static long lastAutoRefresh;

    private MarketWindow() {}

    private static final String HIDDEN_CATEGORIES_TAB = "Скрытые";

    static void open(Addon addon, MarketStore store, CategoryVisibilityStore categoryVisibility) {
        if (activeView != null && activeView.window.isOpen()) {
            activeView.window.open();
            activeView.refreshAll();
            return;
        }

        Ui ui = addon.getUi();
        UiWindow window = ui.window("EvoMarket — рынок чата", 500, 540, true, true);
        WindowView view = new WindowView(store, categoryVisibility, ui, window);
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
        private final Ui ui;
        private final UiWindow window;

        private UiText countText;
        private UiContainer actionTabs;
        private UiContainer categoryPanel;
        private UiContainer offersList;
        private ActionFilter actionFilter = ActionFilter.ALL;
        private String selectedCategory = MarketCategories.ALL;
        private int renderedSize = -1;
        private String renderedTopKey = "";
        private String renderedCategoryKey = "";

        private WindowView(MarketStore store, CategoryVisibilityStore categoryVisibility, Ui ui, UiWindow window) {
            this.store = store;
            this.categoryVisibility = categoryVisibility;
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
            body.add(ui.spacer(1, 7));
            body.add(ui.divider());
            body.add(ui.spacer(1, 7));

            // Keep the action controls in a standalone fixed-height row.
            actionTabs = ui.row()
                    .spacing(6)
                    .width(Math.max(360, window.getBodyWidth() - 20))
                    .height(36);
            body.add(actionTabs);
            body.add(ui.spacer(1, 8));

            body.add(ui.text("Категории", TextStyle.TITLE));
            body.add(ui.spacer(1, 3));
            categoryPanel = ui.column().spacing(2).fillWidth();
            body.add(categoryPanel);
            body.add(ui.spacer(1, 8));
            body.add(ui.divider());
            body.add(ui.spacer(1, 6));
            body.add(ui.text("Объявления", TextStyle.TITLE));
            body.add(ui.spacer(1, 5));

            // Each card is independent and separated by a fixed spacer. There are no
            // multiline debug labels above this list anymore, which used to overlap it.
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

        private void refreshAll() {
            refreshCount();
            renderActionTabs();
            renderCategories();
            renderOffers();
            renderedSize = store.size();
            renderedTopKey = topOfferKey();
        }

        private void refreshIfChanged() {
            String topKey = topOfferKey();
            if (renderedSize != store.size() || !renderedTopKey.equals(topKey)) {
                refreshAll();
            } else {
                refreshCount();
            }
        }

        private void refreshCount() {
            countText.text("Объявлений: " + store.size()
                    + " · покупают: " + store.count(TradeAction.BUY)
                    + " · продают: " + store.count(TradeAction.SELL));
        }

        private void renderActionTabs() {
            actionTabs.clear();
            actionTabs.add(actionButton(ActionFilter.ALL, "Все"));
            actionTabs.add(actionButton(ActionFilter.BUY, "Покупают"));
            actionTabs.add(actionButton(ActionFilter.SELL, "Продают"));
        }

        private UiButton actionButton(ActionFilter value, String label) {
            String display = actionFilter == value ? "✓ " + label : label;
            return ui.button(display, () -> chooseAction(value)).width(128).height(30);
        }

        private void renderCategories() {
            List<MarketOffer> all = store.latest(500);
            List<String> detected = MarketCategories.available(all, selectedTradeAction());
            List<String> categories = detected.stream()
                    .filter(category -> !categoryVisibility.isHidden(category))
                    .toList();
            String key = String.join("|", categories) + "|hidden=" + String.join("|", categoryVisibility.hiddenCategories());
            renderedCategoryKey = key;
            if (!HIDDEN_CATEGORIES_TAB.equals(selectedCategory)
                    && !categories.contains(selectedCategory)
                    && !MarketCategories.ALL.equals(selectedCategory)) {
                selectedCategory = MarketCategories.ALL;
            }

            categoryPanel.clear();
            if (HIDDEN_CATEGORIES_TAB.equals(selectedCategory)) {
                List<String> hidden = categoryVisibility.hiddenCategories();
                if (hidden.isEmpty()) {
                    categoryPanel.add(ui.text("Скрытых категорий пока нет. Нажми × рядом с ошибочной категорией, чтобы убрать её сюда.", TextStyle.SECONDARY)
                            .maxWidth(Math.max(180, window.getBodyWidth() - 28), true));
                    return;
                }
                for (String category : hidden) {
                    UiContainer row = ui.row().spacing(6)
                            .width(Math.max(360, window.getBodyWidth() - 20)).height(33);
                    row.add(ui.text(category, TextStyle.BODY)
                            .maxWidth(Math.max(180, window.getBodyWidth() - 150), true));
                    row.add(ui.button("Вернуть", () -> restoreCategory(category)).width(88).height(29));
                    categoryPanel.add(row);
                    categoryPanel.add(ui.spacer(1, 2));
                }
                categoryPanel.add(ui.button("Вернуть все категории", () -> {
                    categoryVisibility.restoreAll();
                    selectedCategory = MarketCategories.ALL;
                    refreshAll();
                }).width(180).height(29));
                return;
            }

            List<String> buttons = new ArrayList<>();
            buttons.add(MarketCategories.ALL);
            buttons.add(HIDDEN_CATEGORIES_TAB);
            buttons.addAll(categories);

            for (int start = 0; start < buttons.size(); start += BUTTONS_PER_ROW) {
                UiContainer row = ui.row()
                        .spacing(6)
                        .width(Math.max(360, window.getBodyWidth() - 20))
                        .height(35);
                int end = Math.min(start + BUTTONS_PER_ROW, buttons.size());
                for (int i = start; i < end; i++) {
                    String category = buttons.get(i);
                    String label = selectedCategory.equals(category) ? "✓ " + category : category;
                    UiContainer cell = ui.row().spacing(3).width(134).height(31);
                    if (MarketCategories.ALL.equals(category) || HIDDEN_CATEGORIES_TAB.equals(category)) {
                        cell.add(ui.button(label, () -> chooseCategory(category)).width(128).height(29));
                    } else {
                        cell.add(ui.button(label, () -> chooseCategory(category)).width(104).height(29));
                        cell.add(ui.button("×", () -> hideCategory(category)).width(24).height(29));
                    }
                    row.add(cell);
                }
                categoryPanel.add(row);
                categoryPanel.add(ui.spacer(1, 3));
            }
        }

        private TradeAction selectedTradeAction() {
            return switch (actionFilter) {
                case ALL -> null;
                case BUY -> TradeAction.BUY;
                case SELL -> TradeAction.SELL;
            };
        }

        private void renderOffers() {
            offersList.clear();
            if (HIDDEN_CATEGORIES_TAB.equals(selectedCategory)) {
                offersList.add(ui.text("Здесь можно восстановить скрытые категории. Они не удаляются из истории объявлений.", TextStyle.SECONDARY)
                        .maxWidth(Math.max(180, window.getBodyWidth() - 28), true));
                return;
            }
            List<MarketOffer> offers = store.latest(500);
            int shown = 0;
            double bodyWidth = Math.max(180.0, window.getBodyWidth() - 28.0);

            for (MarketOffer offer : offers) {
                if (MarketCategories.isFillerItem(offer.item())) continue;
                if (actionFilter == ActionFilter.BUY && offer.action() != TradeAction.BUY) continue;
                if (actionFilter == ActionFilter.SELL && offer.action() != TradeAction.SELL) continue;
                if (!MarketCategories.ALL.equals(selectedCategory)
                        && !offer.categories().contains(selectedCategory)) continue;

                String actionLabel = offer.action() == TradeAction.BUY ? "ПОКУПАЮТ" : "ПРОДАЮТ";
                String color = offer.action() == TradeAction.BUY ? "§a" : "§c";
                String price = formatOfferPrice(offer);
                String time = TIME.format(offer.timestamp().atZone(ZoneId.systemDefault()));

                UiContainer card = ui.card(false).padding(9, 7).spacing(4).fillWidth();
                card.add(ui.text(color + actionLabel + " §8· " + time, TextStyle.SECONDARY));

                card.add(ui.text(offer.displayItem(), TextStyle.TITLE).maxWidth(bodyWidth, true));

                String paymentLabel = offer.paymentLabel();
                if (paymentLabel != null && !paymentLabel.isBlank()) {
                    card.add(ui.text("Расчёт: " + paymentLabel, TextStyle.SECONDARY).maxWidth(bodyWidth, true));
                }

                String categoryLabel = "Категории: " + String.join(", ", offer.categories());
                if (categoryLabel.length() > 78) categoryLabel = categoryLabel.substring(0, 75) + "…";
                if (!offer.categories().isEmpty()) {
                    card.add(ui.text(categoryLabel, TextStyle.SECONDARY).maxWidth(bodyWidth, true));
                }

                // Always show the actual player's message, not just the normalized item label,
                // so ambiguous abbreviations and price context remain visible in every tab.
                String sourceMessage = chatMessage(offer.rawText());
                if (!sourceMessage.isBlank()) {
                    card.add(ui.text("Сообщение: " + sourceMessage, TextStyle.SECONDARY).maxWidth(bodyWidth, true));
                }
                card.add(ui.text("Цена: " + price + "  |  Игрок: " + offer.seller(), TextStyle.BODY)
                        .maxWidth(bodyWidth, true));
                offersList.add(card);
                offersList.add(ui.spacer(1, 5));
                shown++;
            }

            if (shown == 0) {
                offersList.add(ui.text("Пока нет объявлений в этой категории. Новые предложения появятся автоматически, когда их напишут в чат.", TextStyle.SECONDARY)
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
        // DiamondWorld chat lines commonly end the sender prefix with [level]:.
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
                String unit = rate.group(2).toLowerCase(java.util.Locale.ROOT).startsWith("т")
                        || rate.group(2).equalsIgnoreCase("t") ? "T" : "B";
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
            if (payment != null && payment.equals("ЛК")) return formatted + " ЛК";
            if (payment != null && payment.equals("Игровая валюта")) return formatted + " игровой валюты";
            if (payment != null && payment.equals("ЛК / игровая валюта")) return formatted + " (ЛК / игровая валюта)";
            return formatted;
        }
        return "цена не распознана";
    }

    private static boolean isTrillion(String suffix) {
        return suffix != null && (suffix.toLowerCase(java.util.Locale.ROOT).startsWith("т")
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

    private enum ActionFilter { ALL, BUY, SELL }
}
