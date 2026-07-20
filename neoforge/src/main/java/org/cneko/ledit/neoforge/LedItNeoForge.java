package org.cneko.ledit.neoforge;

import net.neoforged.fml.loading.FMLLoader;
import org.cneko.ledit.LedIt;
import org.cneko.ledit.client.LedItClient;
import net.neoforged.fml.common.Mod;

@Mod(LedIt.MOD_ID)
public final class LedItNeoForge {
    public LedItNeoForge() {
        // Run our common setup.
        LedIt.init();

        // Register config screen and LED effects on client side
        if (FMLLoader.getDist().isClient()) {
            org.cneko.ledit.neoforge.client.LedItNeoForgeClient.registerConfigScreen();
            LedItClient.init();
        }
    }
}
