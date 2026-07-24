package org.cneko.ledit.effect;

import net.minecraft.advancements.AdvancementType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import org.cneko.ledit.color.BiomeColors;
import org.cneko.ledit.color.ColorAnimator;
import org.cneko.ledit.config.LedItConfig;
import org.cneko.ledit.wled.E131Client;
import org.cneko.ledit.wled.WLEDClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main orchestrator for LED effects.
 * <p>
 * Uses wall-clock time for frame scheduling, independent of game tick rate.
 * Animation advances per sent frame, so higher FPS = smoother animation.
 * <p>
 * Effect priority (highest to lowest):
 * <ol>
 *   <li>Death screen — dark crimson, slowly dims</li>
 *   <li>Damage flash — instant red, fades over ~1.5s</li>
 *   <li>Advancement — golden burst, fades back</li>
 *   <li>Fire — rapid orange flicker while burning</li>
 *   <li>Cave — deep indigo when underground</li>
 *   <li>Biome — smooth ambient transitions</li>
 * </ol>
 */
public final class LedEffectManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ledit");

    // Damage flash
    private static final BiomeColors.BiomePalette DAMAGE_RED = new BiomeColors.BiomePalette(0f, 1.0f);
    private static final int FLASH_HOLD = 5;
    private static final int FLASH_FADE = 25;

    // Cave
    private static final BiomeColors.BiomePalette CAVE_PALETTE = new BiomeColors.BiomePalette(250f, 0.60f);
    private static final int CAVE_FADE_TICKS = 60;

    // Achievement — per-type palettes and timings
    private static final BiomeColors.BiomePalette ACH_TASK = new BiomeColors.BiomePalette(120f, 0.90f);      // green
    private static final BiomeColors.BiomePalette ACH_GOAL = new BiomeColors.BiomePalette(50f, 0.95f);       // gold
    private static final BiomeColors.BiomePalette ACH_CHALLENGE = new BiomeColors.BiomePalette(280f, 0.95f); // purple

    private static final int ACH_TASK_HOLD = 10, ACH_TASK_FADE = 30;
    private static final int ACH_GOAL_HOLD = 20, ACH_GOAL_FADE = 50;
    private static final int ACH_CHALLENGE_HOLD = 30, ACH_CHALLENGE_FADE = 70;

    // Death
    private static final int DEATH_HOLD = 20;
    private static final int DEATH_FADE_TICKS = 80;

    // Fire
    private static final int FIRE_FADE_TICKS = 40;

    private final WLEDClient jsonClient;
    private final E131Client e131Client;
    private final ColorAnimator animator;

    // Time-based frame scheduling
    private long lastFrameNanos;
    private long animationFrame;

    // Position tracking
    private long lastBiomeCheckPos = Long.MIN_VALUE;

    // Display palette
    private BiomeColors.BiomePalette displayPalette;

    // Biome transition
    private BiomeColors.BiomePalette biomeTarget;
    private BiomeColors.BiomePalette biomeFrom;
    private int biomeTransTick;
    private boolean paletteNeedsUpdate = true;

    // Damage flash
    private int lastHurtTime;
    private int flashTick;
    private BiomeColors.BiomePalette flashReturnTo;

    // Cave
    private boolean wasInCave;
    private int caveTransTick;
    private boolean caveExiting;
    private BiomeColors.BiomePalette caveReturnTo;

    // Achievement
    private int achievementTick;
    private BiomeColors.BiomePalette achievementReturnTo;
    private BiomeColors.BiomePalette achievementColor;
    private int achievementHold;
    private int achievementFade;
    private int achievementPhase; // 0=idle, 1=hold, 2=fade

    // Death
    private boolean wasOnDeathScreen;
    private int deathTick;
    private BiomeColors.BiomePalette deathReturnTo;

    // Fire
    private boolean wasOnFire;
    private int fireTick;
    private int fireExitTick;
    private BiomeColors.BiomePalette fireReturnTo;

    public LedEffectManager() {
        this.jsonClient = new WLEDClient();
        this.e131Client = new E131Client();
        this.animator = new ColorAnimator();
        this.lastFrameNanos = System.nanoTime();
    }

    // ===== Main Tick =====

    public void onClientTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.isPaused() || mc.level == null) {
            return;
        }

        // Per-tick: update effect states
        updateEffects(mc, player);

        // Time-based frame scheduling: send N frames based on elapsed wall time
        sendFrames(player);
    }

    private void updateEffects(Minecraft mc, LocalPlayer player) {
        // Check position for biome changes
        long packedPos = packPos(player.blockPosition().getX(), player.blockPosition().getZ());
        if (packedPos != lastBiomeCheckPos) {
            lastBiomeCheckPos = packedPos;
            paletteNeedsUpdate = true;
        }

        // Resolve display palette based on priority
        resolvePalette(mc, player);
    }

    private void sendFrames(LocalPlayer player) {
        int fps = LedItConfig.targetFPS;
        long frameIntervalNs = 1_000_000_000L / fps;
        long now = System.nanoTime();

        // Cap frames per tick to avoid burst after lag spikes
        int maxFrames = Math.max(1, fps / 10);
        int sent = 0;

        while (lastFrameNanos + frameIntervalNs <= now && sent < maxFrames) {
            lastFrameNanos += frameIntervalNs;
            animationFrame++;
            sent++;

            double timeSeconds = (double) animationFrame / fps;

            try {
                if (displayPalette == null) continue;

                float healthRatio = player.getMaxHealth() > 0
                        ? player.getHealth() / player.getMaxHealth()
                        : 1.0f;

                // Client-safe light calculation.
                // Outdoors: rawSky is always 15, so we use time-of-day directly.
                // Indoors/caves: block light dominates.
                int rawSky = player.level().getBrightness(
                        net.minecraft.world.level.LightLayer.SKY, player.blockPosition());
                int block = player.level().getBrightness(
                        net.minecraft.world.level.LightLayer.BLOCK, player.blockPosition());

                // Sky factor from time of day: 1.0 at noon, ~0.6 at midnight
                long dayTime = player.level().getDayTime() % 24000;
                float skyFactor;
                if (dayTime < 6000)        skyFactor = 0.6f + 0.4f * (dayTime / 6000f);          // sunrise
                else if (dayTime < 12000)   skyFactor = 1.0f;                                      // day
                else if (dayTime < 13000)   skyFactor = 1.0f - 0.4f * ((dayTime - 12000) / 1000f); // dusk
                else if (dayTime < 23000)   skyFactor = 0.6f;                                      // night
                else                        skyFactor = 0.6f + 0.4f * ((dayTime - 23000) / 1000f); // dawn

                // Rain & thunder further reduce light
                if (player.level().isThundering()) skyFactor = Math.max(0.3f, skyFactor - 0.3f);
                else if (player.level().isRaining()) skyFactor = Math.max(0.4f, skyFactor - 0.2f);

                int lightLevel;
                if (rawSky >= 14) {
                    // Open to sky — brightness from time/weather only
                    lightLevel = Math.round(15 * skyFactor);
                } else {
                    // Under cover — block light is the meaningful signal
                    lightLevel = Math.max(block, Math.round(rawSky * skyFactor));
                }

                float lightMult = 0.05f + (lightLevel / 15f) * 0.95f;
                int effectiveBrightness = Math.round(LedItConfig.brightness * lightMult);

                int[][] colors = animator.generate(
                        timeSeconds,
                        LedItConfig.ledCount,
                        displayPalette,
                        healthRatio);

                if (LedItConfig.useE131) {
                    e131Client.sendColors(colors, effectiveBrightness);
                } else {
                    jsonClient.sendColors(colors, effectiveBrightness);
                }
            } catch (Exception e) {
                LOGGER.warn("Error in LED frame: {}", e.getMessage());
            }
        }

        // If we fell way behind (e.g. after pause), resync
        if (now - lastFrameNanos > frameIntervalNs * 3L) {
            lastFrameNanos = now;
        }
    }

    // ===== Palette Resolution =====

    private void resolvePalette(Minecraft mc, LocalPlayer player) {
        if (updateDeathEffect(mc)) return;
        if (updateDamageFlash(player)) return;
        if (updateAchievementEffect()) return;
        if (updateFireEffect(player)) return;
        if (updateCaveEffect(player)) return;
        updateBiomeTransition(player);
    }

    // ===== 1. Damage Flash =====

    private boolean updateDamageFlash(LocalPlayer player) {
        int ht = player.hurtTime;
        if (ht > 0 && lastHurtTime == 0) {
            flashReturnTo = fireReturnTo != null ? fireReturnTo
                    : displayPalette != null ? displayPalette
                    : BiomeColors.getPalette(player);
            flashTick = 0;
        }
        lastHurtTime = ht;

        boolean active = flashReturnTo != null && flashTick < FLASH_HOLD + FLASH_FADE;
        if (!active) {
            if (flashTick >= FLASH_HOLD + FLASH_FADE) flashReturnTo = null;
            return false;
        }

        flashTick++;
        if (flashTick <= FLASH_HOLD) {
            displayPalette = DAMAGE_RED;
        } else if (flashTick < FLASH_HOLD + FLASH_FADE) {
            float raw = (float) (flashTick - FLASH_HOLD) / FLASH_FADE;
            float t = 1f - (1f - raw) * (1f - raw);
            displayPalette = BiomeColors.BiomePalette.lerp(DAMAGE_RED, flashReturnTo, t);
        } else {
            displayPalette = flashReturnTo;
            flashReturnTo = null;
            paletteNeedsUpdate = true;
            return false;
        }
        return true;
    }

    // ===== 2. Fire Effect =====

    private boolean updateFireEffect(LocalPlayer player) {
        boolean onFire = player.getRemainingFireTicks() > 0;

        if (onFire && !wasOnFire) {
            fireReturnTo = displayPalette != null ? displayPalette : BiomeColors.getPalette(player);
            fireTick = 0;
            fireExitTick = 0;
        }
        wasOnFire = onFire;

        if (onFire) {
            fireTick++;
            float hue = 15f + (float) Math.sin(fireTick * 1.7) * 10f
                    + (float) Math.sin(fireTick * 3.1) * 5f;
            float sat = 0.82f + (float) Math.sin(fireTick * 2.3) * 0.18f;
            if (hue < 0) hue += 360f;
            displayPalette = new BiomeColors.BiomePalette(hue, sat);
            return true;
        }

        if (fireReturnTo != null && !onFire) {
            fireExitTick++;
            if (fireExitTick >= FIRE_FADE_TICKS) {
                displayPalette = fireReturnTo;
                fireReturnTo = null;
                paletteNeedsUpdate = true;
                return false;
            }
            float t = (float) fireExitTick / FIRE_FADE_TICKS;
            BiomeColors.BiomePalette cur = displayPalette != null
                    ? displayPalette : new BiomeColors.BiomePalette(20f, 0.9f);
            displayPalette = BiomeColors.BiomePalette.lerp(cur, fireReturnTo, t);
            return true;
        }
        return false;
    }

    // ===== 1. Death Effect =====

    private boolean updateDeathEffect(Minecraft mc) {
        boolean onDeathScreen = mc.screen instanceof DeathScreen;

        // Entering death screen
        if (onDeathScreen && !wasOnDeathScreen) {
            deathReturnTo = displayPalette != null ? displayPalette : BiomeColors.getPalette(mc.player);
            deathTick = 0;
        }

        wasOnDeathScreen = onDeathScreen;

        if (onDeathScreen) {
            deathTick++;
            // Deep crimson, slowly dimming
            float sat = 0.8f + (float) Math.sin(deathTick * 0.3) * 0.1f;
            displayPalette = new BiomeColors.BiomePalette(0f, sat);
            return true;
        }

        // Just left death screen — fade back to normal
        if (deathReturnTo != null && !onDeathScreen) {
            deathTick++;
            if (deathTick >= DEATH_HOLD + DEATH_FADE_TICKS) {
                displayPalette = deathReturnTo;
                deathReturnTo = null;
                paletteNeedsUpdate = true;
                return false;
            }
            if (deathTick < DEATH_HOLD + DEATH_FADE_TICKS) {
                float raw = (float) (deathTick - DEATH_HOLD) / DEATH_FADE_TICKS;
                float t = raw < 0.5f ? 2f * raw * raw : -1f + (4f - 2f * raw) * raw;
                BiomeColors.BiomePalette deathRed = new BiomeColors.BiomePalette(0f, 0.8f);
                displayPalette = BiomeColors.BiomePalette.lerp(deathRed, deathReturnTo, t);
                return true;
            }
        }

        return false;
    }

    // ===== 2. Achievement Effect =====

    /** Called from mixin when an advancement toast appears. */
    public void triggerAdvancement(AdvancementType type) {
        switch (type) {
            case CHALLENGE -> {
                achievementColor = ACH_CHALLENGE;
                achievementHold = ACH_CHALLENGE_HOLD;
                achievementFade = ACH_CHALLENGE_FADE;
            }
            case GOAL -> {
                achievementColor = ACH_GOAL;
                achievementHold = ACH_GOAL_HOLD;
                achievementFade = ACH_GOAL_FADE;
            }
            default -> {
                achievementColor = ACH_TASK;
                achievementHold = ACH_TASK_HOLD;
                achievementFade = ACH_TASK_FADE;
            }
        }
        achievementReturnTo = displayPalette != null ? displayPalette : null;
        achievementTick = 0;
        achievementPhase = 1;
    }

    private boolean updateAchievementEffect() {
        if (achievementPhase == 0) return false;

        achievementTick++;
        if (achievementPhase == 1) {
            if (achievementTick <= achievementHold) {
                displayPalette = achievementColor;
                return true;
            }
            achievementPhase = 2;
            achievementTick = 0;
        }

        if (achievementPhase == 2) {
            BiomeColors.BiomePalette returnTo = achievementReturnTo != null
                    ? achievementReturnTo
                    : displayPalette;

            if (achievementTick >= achievementFade) {
                displayPalette = returnTo;
                achievementPhase = 0;
                achievementReturnTo = null;
                paletteNeedsUpdate = true;
                return false;
            }
            float t = (float) achievementTick / achievementFade;
            t = 1f - (1f - t) * (1f - t); // ease-out
            displayPalette = BiomeColors.BiomePalette.lerp(achievementColor, returnTo, t);
            return true;
        }

        return false;
    }

    // ===== 4. Fire Effect =====

    private boolean updateCaveEffect(LocalPlayer player) {
        // Only count as a cave when underground: below Y threshold AND no sky visible.
        // Trees and buildings at surface level won't trigger this.
        boolean inCave = player.blockPosition().getY() < 50
                && !player.level().canSeeSky(player.blockPosition());

        // Entering cave
        if (inCave && !wasInCave && !caveExiting) {
            caveReturnTo = displayPalette != null ? displayPalette : BiomeColors.getPalette(player);
            caveTransTick = 0;
            caveExiting = false;
        }
        // Leaving cave
        if (!inCave && wasInCave && !caveExiting) {
            caveReturnTo = BiomeColors.getPalette(player);
            caveTransTick = 0;
            caveExiting = true;
        }

        wasInCave = inCave;

        if (inCave && !caveExiting) {
            // Inside cave — depth affects the hue slightly (deeper = more purple)
            int y = player.blockPosition().getY();
            float depthFactor = Math.max(0f, Math.min(1f, (-y) / 64f)); // 0 at surface, 1 at Y=-64
            float hue = CAVE_PALETTE.hue() + depthFactor * 20f; // 250° → 270° (blue → indigo)
            float sat = CAVE_PALETTE.saturation() + depthFactor * 0.15f;

            // Gentle shimmer so it's not totally static
            float shimmer = (float) Math.sin(System.nanoTime() * 0.000000001) * 0.03f;
            sat = Math.min(1f, sat + shimmer);

            displayPalette = new BiomeColors.BiomePalette(hue, sat);
            return true;
        }

        // Transitioning out of cave
        if (caveExiting) {
            caveTransTick++;
            if (caveTransTick >= CAVE_FADE_TICKS) {
                displayPalette = caveReturnTo;
                caveReturnTo = null;
                caveExiting = false;
                paletteNeedsUpdate = true;
                return false;
            }
            float t = (float) caveTransTick / CAVE_FADE_TICKS;
            t = t < 0.5f ? 2f * t * t : -1f + (4f - 2f * t) * t; // ease-in-out
            BiomeColors.BiomePalette cur = displayPalette != null
                    ? displayPalette : CAVE_PALETTE;
            displayPalette = BiomeColors.BiomePalette.lerp(cur, caveReturnTo, t);
            return true;
        }

        return false;
    }

    // ===== 4. Biome Transition =====

    private void updateBiomeTransition(LocalPlayer player) {
        if (paletteNeedsUpdate || displayPalette == null) {
            BiomeColors.BiomePalette np = BiomeColors.getPalette(player);
            paletteNeedsUpdate = false;

            if (displayPalette == null) {
                displayPalette = np;
            } else {
                biomeFrom = displayPalette;
                biomeTarget = np;
                biomeTransTick = 0;
            }
        }

        if (biomeFrom != null && biomeTarget != null) {
            biomeTransTick++;
            int dur = Math.max(1, LedItConfig.transitionTicks);
            if (biomeTransTick >= dur) {
                displayPalette = biomeTarget;
                biomeFrom = null;
                biomeTarget = null;
            } else {
                float raw = (float) biomeTransTick / dur;
                float t = raw < 0.5f ? 2f * raw * raw : -1f + (4f - 2f * raw) * raw;
                displayPalette = BiomeColors.BiomePalette.lerp(biomeFrom, biomeTarget, t);
            }
        }
    }

    public void markPaletteStale() {
        this.paletteNeedsUpdate = true;
    }

    private static long packPos(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
