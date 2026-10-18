package ru.boss.evomarket.integration;

import ru.boss.evomarket.evoplus.MarketScanner;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Optional reflection-based bridge for ChatManager-Core / DiamondChat.
 * ChatManager-Core replaces vanilla packet handling and publishes a unified
 * MessageReceivedEvent before routing messages into channel windows. Reflection
 * keeps ChatManager-Core optional and prevents its classes from being loaded when
 * the mod is absent. The bridge only observes text and never edits/cancels events.
 */
public final class ChatManagerCoreIntegration {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
    private static final AtomicBoolean HANDLER_ERROR_LOGGED = new AtomicBoolean(false);

    private ChatManagerCoreIntegration() {}

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        try {
            Class<?> chatManagerCore = Class.forName("ru.fozeton.chatmanager.ChatManagerCore");
            Object eventBus = chatManagerCore.getField("EVENT_BUS").get(null);
            Class<?> messageReceivedEvent = Class.forName("ru.fozeton.chatmanager.events.MessageReceivedEvent");
            Class<?> lambdaListener = Class.forName("com.ferra13671.megaevents.eventbus.impl.LambdaListener");
            Constructor<?> listenerConstructor = lambdaListener.getConstructor(Class.class, Consumer.class);
            Consumer<Object> handler = ChatManagerCoreIntegration::onMessageReceived;
            Object listener = listenerConstructor.newInstance(messageReceivedEvent, handler);
            Class<?> eventBusType = Class.forName("com.ferra13671.megaevents.eventbus.IEventBus");
            Method register = eventBusType.getMethod("register", Object.class);
            register.invoke(eventBus, listener);
            System.out.println("[EvoMarket] ChatManager-Core message listener registered.");
        } catch (ReflectiveOperationException | LinkageError exception) {
            REGISTERED.set(false);
            throw new IllegalStateException("Could not register ChatManager-Core message listener", exception);
        }
    }

    private static void onMessageReceived(Object event) {
        try {
            if (event == null) return;
            Object message = event.getClass().getMethod("getMessage").invoke(event);
            if (message == null) return;

            String raw = (String) message.getClass().getMethod("getFullPlain").invoke(message);
            if (raw == null || raw.isBlank()) {
                Object component = message.getClass().getMethod("getContent").invoke(message);
                raw = component == null ? "" : (String) component.getClass().getMethod("getString").invoke(component);
            }
            String author = (String) message.getClass().getMethod("getAuthor").invoke(message);
            Instant timestamp = (Instant) message.getClass().getMethod("getTimestamp").invoke(message);
            MarketScanner.receiveFromChatManager(raw, author, timestamp);
        } catch (ReflectiveOperationException | LinkageError exception) {
            if (HANDLER_ERROR_LOGGED.compareAndSet(false, true)) {
                System.err.println("[EvoMarket] Could not read a ChatManager-Core message: "
                        + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            }
        }
    }
}
