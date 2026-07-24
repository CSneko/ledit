package org.cneko.ledit.neoforge.client;

import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.cneko.ledit.config.LedItConfigScreen;

public final class LedItNeoForgeClient {
    private LedItNeoForgeClient() {
    }

    public static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(
                IConfigScreenFactory.class,
                () -> (modContainer, parent) -> LedItConfigScreen.create(parent)
        );
    }
}
