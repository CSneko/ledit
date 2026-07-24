package org.cneko.ledit.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class LedItConfigScreen {
    private LedItConfigScreen() {
    }

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.ledit.title"))
                .setSavingRunnable(LedItConfig::save);

        ConfigCategory wled = builder.getOrCreateCategory(
                Component.translatable("config.ledit.category.wled")
        );
        ConfigEntryBuilder eb = builder.entryBuilder();

        // WLED Device Address
        wled.addEntry(eb.startStrField(
                        Component.translatable("config.ledit.wledAddress"),
                        LedItConfig.wledAddress
                ).setDefaultValue("127.0.0.1")
                .setTooltip(Component.translatable("config.ledit.wledAddress.tooltip"))
                .setSaveConsumer(v -> LedItConfig.wledAddress = v)
                .build()
        );

        // WLED Port
        wled.addEntry(eb.startIntField(
                        Component.translatable("config.ledit.wledPort"),
                        LedItConfig.wledPort
                ).setDefaultValue(80)
                .setMin(1)
                .setMax(65535)
                .setTooltip(Component.translatable("config.ledit.wledPort.tooltip"))
                .setSaveConsumer(v -> LedItConfig.wledPort = v)
                .build()
        );

        // LED Count
        wled.addEntry(eb.startIntField(
                        Component.translatable("config.ledit.ledCount"),
                        LedItConfig.ledCount
                ).setDefaultValue(30)
                .setMin(1)
                .setMax(1000)
                .setTooltip(Component.translatable("config.ledit.ledCount.tooltip"))
                .setSaveConsumer(v -> LedItConfig.ledCount = v)
                .build()
        );

        // Target FPS
        wled.addEntry(eb.startIntField(
                        Component.translatable("config.ledit.targetFPS"),
                        LedItConfig.targetFPS
                ).setDefaultValue(2)
                .setMin(1)
                .setMax(1000)
                .setTooltip(Component.translatable("config.ledit.targetFPS.tooltip"))
                .setSaveConsumer(v -> LedItConfig.targetFPS = v)
                .build()
        );

        // Transition Ticks
        wled.addEntry(eb.startIntField(
                        Component.translatable("config.ledit.transitionTicks"),
                        LedItConfig.transitionTicks
                ).setDefaultValue(60)
                .setMin(0)
                .setMax(200)
                .setTooltip(Component.translatable("config.ledit.transitionTicks.tooltip"))
                .setSaveConsumer(v -> LedItConfig.transitionTicks = v)
                .build()
        );

        // Brightness
        wled.addEntry(eb.startIntField(
                        Component.translatable("config.ledit.brightness"),
                        LedItConfig.brightness
                ).setDefaultValue(255)
                .setMin(0)
                .setMax(255)
                .setTooltip(Component.translatable("config.ledit.brightness.tooltip"))
                .setSaveConsumer(v -> LedItConfig.brightness = v)
                .build()
        );

        // ===== E1.31 Transport Category =====
        ConfigCategory transport = builder.getOrCreateCategory(
                Component.translatable("config.ledit.category.transport")
        );

        // Use E1.31 toggle
        transport.addEntry(eb.startBooleanToggle(
                        Component.translatable("config.ledit.useE131"),
                        LedItConfig.useE131
                ).setDefaultValue(false)
                .setTooltip(Component.translatable("config.ledit.useE131.tooltip"))
                .setSaveConsumer(v -> LedItConfig.useE131 = v)
                .build()
        );

        // E1.31 Port
        transport.addEntry(eb.startIntField(
                        Component.translatable("config.ledit.e131Port"),
                        LedItConfig.e131Port
                ).setDefaultValue(5568)
                .setMin(1)
                .setMax(65535)
                .setTooltip(Component.translatable("config.ledit.e131Port.tooltip"))
                .setSaveConsumer(v -> LedItConfig.e131Port = v)
                .build()
        );

        // E1.31 Universe
        transport.addEntry(eb.startIntField(
                        Component.translatable("config.ledit.e131Universe"),
                        LedItConfig.e131Universe
                ).setDefaultValue(1)
                .setMin(1)
                .setMax(63999)
                .setTooltip(Component.translatable("config.ledit.e131Universe.tooltip"))
                .setSaveConsumer(v -> LedItConfig.e131Universe = v)
                .build()
        );

        return builder.build();
    }
}
