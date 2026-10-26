package ru.boss.evomarket.evoplus;

import ru.boss.evomarket.core.*;
import ru.dargen.evoplus.api.addon.Addon;
import ru.dargen.evoplus.api.ui.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** Headless contract test with proxies of the real EvoPlus API; not an in-game render test. */
public final class MarketWindowSelfTest {
    static int checks;
    static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    static final class Element implements InvocationHandler {
        List<Object> children = new ArrayList<>();
        String label = "";
        double width = 100;
        boolean open;
        Runnable action, onClose;
        Consumer<String> onChange;
        Object content, footer, proxy;
        int openCalls;
        @SuppressWarnings("unchecked")
        public Object invoke(Object p, Method m, Object[] a) {
            switch (m.getName()) {
                case "toString": return label;
                case "getLabel", "getText", "getValue": return label;
                case "label", "text", "value": label = (String) a[0]; break;
                case "getWidth", "getBodyWidth": return width;
                case "getHeight", "getBodyHeight": return 550.0;
                case "width": width = (double) a[0]; break;
                case "getChildren": return children;
                case "add": children.addAll(Arrays.asList((Object[]) a[0])); break;
                case "clear": children.clear(); break;
                case "onChange": onChange = (Consumer<String>) a[0]; break;
                case "onClose": onClose = (Runnable) a[0]; break;
                case "getContent": return content;
                case "getFooter": return footer;
                case "isOpen": return open;
                case "open": open = true; openCalls++; break;
                case "close": open = false; if (onClose != null) onClose.run(); return null;
                case "isFocused", "isHovered": return false;
                case "isEnabled", "isVisible": return true;
            }
            return m.getReturnType() == void.class ? null : p;
        }
    }
    static Element element(Object proxy) { return (Element) Proxy.getInvocationHandler(proxy); }
    static <T> T make(Class<T> type) {
        Element e = new Element();
        e.proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, e);
        return type.cast(e.proxy);
    }
    static Object active() throws Exception {
        var field = MarketWindow.class.getDeclaredField("activeView"); field.setAccessible(true); return field.get(null);
    }
    static Object field(Object view, String name) throws Exception {
        var f = view.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(view);
    }
    static void refresh(Object view) throws Exception {
        var m = view.getClass().getDeclaredMethod("refreshAll"); m.setAccessible(true); m.invoke(view);
    }
    static List<Element> descendants(Object parent) {
        List<Element> result = new ArrayList<>();
        Element e = element(parent); result.add(e);
        for (Object c : e.children) result.addAll(descendants(c));
        return result;
    }
    static void click(Object parent, String label) {
        descendants(parent).stream().filter(e -> e.action != null && e.label.equals(label))
                .findFirst().orElseThrow(() -> new AssertionError("Button missing: " + label)).action.run();
    }
    public static void main(String[] args) throws Exception {
        List<UiWindow> windows = new ArrayList<>();
        Ui ui = (Ui) Proxy.newProxyInstance(Ui.class.getClassLoader(), new Class<?>[]{Ui.class}, (p,m,a) -> {
            if (m.getName().equals("window")) {
                UiWindow w = make(UiWindow.class); Element e = element(w);
                e.width = 680; e.content = make(UiContainer.class); e.footer = make(UiContainer.class);
                windows.add(w); return w;
            }
            Object child = make(m.getReturnType()); Element e = element(child);
            if (a != null && a.length > 0 && a[0] instanceof String text) e.label = text;
            if (a != null) for (Object argument : a) if (argument instanceof Runnable r) e.action = r;
            return child;
        });
        Addon addon = (Addon) Proxy.newProxyInstance(Addon.class.getClassLoader(), new Class<?>[]{Addon.class}, (p,m,a) -> ui);
        var dir = Files.createTempDirectory("evomarket-ui-");
        var store = new MarketStore(dir.resolve("history"));
        var hidden = new CategoryVisibilityStore(dir.resolve("hidden"));
        var ignored = new SellerListStore(dir.resolve("ignored"));
        var favorites = new SellerListStore(dir.resolve("favorites"));
        var scanner = new MarketScanner(store, () -> true);
        // Tick registration belongs to Fabric; invoke the same refresh boundary explicitly here.
        var hook = MarketWindow.class.getDeclaredField("tickHookRegistered"); hook.setAccessible(true); hook.set(null, true);
        MarketWindow.open(addon, store, hidden, ignored, favorites, scanner);
        Object view = active(); UiWindow window = windows.getFirst();
        check(window.isOpen(), "Window opens");
        check(descendants(field(view,"offersList")).stream().anyMatch(e -> e.label.contains("Пока нет")), "Empty state");
        MarketWindow.open(addon, store, hidden, ignored, favorites, scanner);
        check(windows.size() == 1 && element(window).openCalls == 1, "Repeated hotkey does not reopen overlay");
        var offers = new ArrayList<MarketOffer>();
        for (int i=0;i<45;i++) offers.add(MarketParser.parse("Продам РКР за " + (i+1) + "б", "tester", Instant.EPOCH.plusSeconds(i)));
        store.addAll(offers); refresh(view);
        check(((Element)element(field(view,"offersList"))).children.size() < 45, "Page bounds rendered cards");
        click(field(view,"pagination"), "→"); check((int)field(view,"page") == 1, "Next page"); refresh(view);
        Object categories = field(view,"categoryPanel");
        int before = descendants(categories).size();
        click(categories,"×");
        check(hidden.isHidden("РКР"), "Cross changes visibility state");
        check(descendants(categories).size() == before, "Click does not mutate dispatched children");
        refresh(view);
        check(descendants(categories).stream().noneMatch(e -> e.label.equals("РКР")), "Hidden chip removed next refresh");
        click(field(view,"categoryNavigationPanel"), "Скрытые (1)"); refresh(view);
        click(categories,"Вернуть"); refresh(view); check(!hidden.isHidden("РКР"), "Restore category");
        click(field(view,"categoryNavigationPanel"), "Все категории"); refresh(view);
        Element input = element(field(view,"search")); input.onChange.accept("no-match"); refresh(view);
        check(descendants(field(view,"offersList")).stream().anyMatch(e -> e.label.contains("Ничего не найдено")), "Search empty state");
        input.onChange.accept("tester"); refresh(view);
        check((int)field(view,"page") == 0, "Search resets page");
        for (int width : new int[]{240,400,680}) {
            element(window).width=width; refresh(view);
            for (Element e : descendants(field(view,"categoryPanel"))) {
                if (e.action != null) check(e.width <= width-28, "Category fits width " + width);
            }
        }
        click(field(view,"offersList"), "☆ Избранное"); refresh(view);
        check(favorites.contains("tester"), "Favorite seller");
        click(field(view,"actionTabs"), "★ Избранные"); refresh(view);
        check(descendants(field(view,"offersList")).stream().anyMatch(e -> e.label.contains("РКР")), "Favorite offers visible");
        click(field(view,"offersList"), "Игнор"); refresh(view);
        check(ignored.contains("tester") && !favorites.contains("tester"), "Ignore removes favorite");
        click(field(view,"actionTabs"), "Игнор"); refresh(view);
        click(field(view,"categoryPanel"), "§c×"); refresh(view);
        check(!ignored.contains("tester"), "Restore ignored seller");
        click(field(view,"actionTabs"), "Все"); refresh(view);
        // Repeated events can arrive before the next client tick.
        Runnable buy = descendants(field(view,"actionTabs")).stream().filter(e -> e.label.equals("Покупают")).findFirst().orElseThrow().action;
        Runnable sell = descendants(field(view,"actionTabs")).stream().filter(e -> e.label.equals("Продают")).findFirst().orElseThrow().action;
        for (int i=0; i<20; i++) { buy.run(); sell.run(); }
        refresh(view);
        check(field(view,"actionFilter").toString().equals("SELL"), "Rapid switches keep last selection");
        check((int)field(view,"page") == 0, "Rapid switches reset page");
        click(window.getFooter(), "Закрыть"); check(active() == null && !window.isOpen(), "Close releases active view");
        MarketWindow.open(addon, store, hidden, ignored, favorites, scanner);
        check(windows.size() == 2 && active() != view, "Reopen creates fresh view");
        windows.getLast().close(); check(active() == null, "External close callback (cross/Escape contract)");
        System.out.println("MarketWindowSelfTest: " + checks + " checks passed (API contract only)");
    }
}
