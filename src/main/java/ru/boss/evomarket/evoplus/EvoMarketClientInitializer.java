package ru.boss.evomarket.evoplus;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registers receive hooks through Fabric's ordinary client entrypoint. This is
 * intentionally separate from EvoPlus's custom addon entrypoint so chat event
 * subscriptions are installed by Fabric during normal client initialization.
 * These listeners only observe messages; they never cancel or modify them.
 */
public final class EvoMarketClientInitializer implements ClientModInitializer {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    @Override
    public void onInitializeClient() {
        if (!REGISTERED.compareAndSet(false, true)) return;

        // ChatManager-Core cancels vanilla chat handling and forwards received messages
        // through its own event bus. Listening to Fabric receive callbacks in parallel
        // would be unreliable with channel mods, so the optional bridge handles that case.
        if (FabricLoader.getInstance().isModLoaded("chatmanager_core")) {
            System.out.println("[EvoMarket] ChatManager-Core detected; waiting for its message event bridge.");
            return;
        }

        ClientReceiveMessageEvents.CHAT.register((message, playerChatMessage, sender, boundChatType, timestamp) ->
                MarketScanner.receiveFromFabric("CHAT", message.getString(), senderName(sender), timestamp));

        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
                MarketScanner.receiveFromFabric(overlay ? "GAME / actionbar" : "GAME",
                        message.getString(), null, Instant.now()));

        // Observe messages cancelled by other mods as well. EvoMarket never
        // returns a cancellation decision and never alters the chat display.
        ClientReceiveMessageEvents.CHAT_CANCELED.register((message, playerChatMessage, sender, boundChatType, timestamp) ->
                MarketScanner.receiveFromFabric("CHAT_CANCELED", message.getString(), senderName(sender), timestamp));

        ClientReceiveMessageEvents.GAME_CANCELED.register((message, overlay) ->
                MarketScanner.receiveFromFabric(overlay ? "GAME_CANCELED / actionbar" : "GAME_CANCELED",
                        message.getString(), null, Instant.now()));

        System.out.println("[EvoMarket] Fabric client message listeners registered.");
    }

    private static String senderName(GameProfile sender) {
        return sender == null ? null : sender.name();
    }
}
