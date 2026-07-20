package org.cneko.ledit;

import org.cneko.ledit.config.LedItConfig;

public final class LedIt {
    public static final String MOD_ID = "ledit";

    public static void init() {
        LedItConfig.load();
    }
}
