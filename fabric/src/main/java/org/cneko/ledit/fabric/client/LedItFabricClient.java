package org.cneko.ledit.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import org.cneko.ledit.client.LedItClient;

public final class LedItFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LedItClient.init();
    }
}
