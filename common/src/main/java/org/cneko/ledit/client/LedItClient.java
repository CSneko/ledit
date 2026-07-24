package org.cneko.ledit.client;

import dev.architectury.event.events.client.ClientTickEvent;
import org.cneko.ledit.effect.LedEffectManager;

/**
 * Client-side initialization for LEDIt.
 * Must only be called from platform client entry points
 * (Fabric: ClientModInitializer, NeoForge: FMLLoader.getDist().isClient() guard).
 */
public final class LedItClient {
    private static LedEffectManager effectManager;

    private LedItClient() {
    }

    public static void init() {
        effectManager = new LedEffectManager();
        ClientTickEvent.CLIENT_POST.register(mc -> {
            if (effectManager != null) {
                effectManager.onClientTick(mc);
            }
        });
    }

    public static LedEffectManager getEffectManager() {
        return effectManager;
    }
}
