package org.cneko.ledit.color;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;

import java.util.Optional;

/**
 * Maps Minecraft biomes to HSB color palettes for ambient LED effects.
 * Uses Minecraft's built-in biome colors (sky, foliage) as the primary source,
 * with manual overrides for special dimensions and biomes without meaningful colors.
 */
public final class BiomeColors {

    private BiomeColors() {
    }

    /**
     * Extract a color palette from the player's current biome.
     *
     * @param player the client player
     * @return a BiomePalette describing the base hue and saturation
     */
    public static BiomePalette getPalette(Player player) {
        Holder<Biome> holder = player.level().getBiome(player.blockPosition());
        Optional<ResourceLocation> key = holder.unwrapKey().map(k -> k.location());

        // Try built-in biome colors first
        Biome biome = holder.value();
        int skyColor = biome.getSkyColor();
        int foliageColor = biome.getFoliageColor();

        // For biomes with meaningful foliage, use it as the primary color source
        if (key.isPresent()) {
            BiomePalette palette = getPaletteByKey(key.get(), biome);
            if (palette != null) return palette;
        }

        // Fallback: extract hue from sky or foliage color
        return extractFromRgb(foliageColor);
    }

    /**
     * Manual palette mapping for specific biomes. Returns null if no match,
     * allowing fallback to built-in color extraction.
     */
    private static BiomePalette getPaletteByKey(ResourceLocation id, Biome biome) {
        String path = id.getPath();
        String ns = id.getNamespace();

        // === Nether biomes ===
        if (ns.equals("minecraft")) {
            return switch (path) {
                // Crimson forest — intense red-orange
                case "crimson_forest" -> new BiomePalette(10f, 0.95f);

                // Warped forest — eerie teal-cyan
                case "warped_forest" -> new BiomePalette(170f, 0.85f);

                // Soul sand valley — deep haunting blue
                case "soul_sand_valley" -> new BiomePalette(240f, 0.80f);

                // Basalt deltas — volcanic ash orange
                case "basalt_deltas" -> new BiomePalette(30f, 0.85f);

                // Nether wastes — classic hell red
                case "nether_wastes" -> new BiomePalette(15f, 0.95f);

                // === End ===
                case "the_end", "end_highlands", "end_midlands" -> new BiomePalette(270f, 0.85f);
                case "end_barrens" -> new BiomePalette(260f, 0.78f);
                case "small_end_islands" -> new BiomePalette(280f, 0.80f);

                // === Overworld ===
                // Desert series — warm amber
                case "desert" -> new BiomePalette(35f, 0.90f);
                case "badlands", "eroded_badlands", "wooded_badlands" -> new BiomePalette(25f, 0.92f);

                // Snowy series — icy blue; cold feeling from hue, not desaturation
                case "snowy_plains", "snowy_taiga", "snowy_beach",
                     "ice_spikes", "frozen_peaks", "jagged_peaks",
                     "frozen_river", "frozen_ocean", "deep_frozen_ocean",
                     "snowy_slopes", "grove" -> new BiomePalette(200f, 0.82f);

                // Plains / savanna — warm yellow-green
                case "plains", "sunflower_plains", "savanna",
                     "savanna_plateau", "windswept_savanna" -> new BiomePalette(55f, 0.85f);

                // Forests — vibrant green
                case "forest", "flower_forest", "birch_forest",
                     "old_growth_birch_forest", "dark_forest" -> new BiomePalette(120f, 0.88f);

                // Jungle — rich tropical green
                case "jungle", "sparse_jungle", "bamboo_jungle" -> new BiomePalette(130f, 0.90f);

                // Taiga — cool blue-green
                case "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga" ->
                        new BiomePalette(160f, 0.82f);

                // Swamp — murky brown-green
                case "swamp", "mangrove_swamp" -> new BiomePalette(80f, 0.80f);

                // Ocean / beach — teal blue
                case "ocean", "deep_ocean", "warm_ocean", "lukewarm_ocean",
                     "deep_lukewarm_ocean", "cold_ocean", "deep_cold_ocean",
                     "beach" -> new BiomePalette(190f, 0.87f);

                // Mountains — grey-blue; cold from hue
                case "stony_peaks", "stony_shore", "windswept_hills",
                     "windswept_gravelly_hills", "windswept_forest" ->
                        new BiomePalette(210f, 0.78f);

                // Cherry grove — soft pink
                case "cherry_grove" -> new BiomePalette(350f, 0.82f);

                // Mushroom fields — magenta dreamy
                case "mushroom_fields" -> new BiomePalette(300f, 0.88f);

                // Meadow — warm grass green
                case "meadow" -> new BiomePalette(70f, 0.85f);

                // Dripstone / lush caves
                case "dripstone_caves" -> new BiomePalette(30f, 0.85f);
                case "lush_caves" -> new BiomePalette(140f, 0.87f);
                case "deep_dark" -> new BiomePalette(240f, 0.75f);

                // River — gentle blue
                case "river" -> new BiomePalette(195f, 0.82f);

                default -> null; // fall through to built-in extraction
            };
        }

        return null;
    }

    /**
     * Extract a palette from an RGB color by converting to HSV and taking the hue.
     */
    private static BiomePalette extractFromRgb(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        float[] hsv = rgbToHsv(r, g, b);
        // Minecraft foliage colors are often desaturated — boost to LED-appropriate range
        return new BiomePalette(hsv[0], Math.max(hsv[1], 0.78f));
    }

    /**
     * Convert RGB (0-255 each) to HSV.
     * H: 0-360, S: 0-1, V: 0-1
     */
    static float[] rgbToHsv(int r, int g, int b) {
        float rf = r / 255f;
        float gf = g / 255f;
        float bf = b / 255f;

        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;

        float h = 0;
        if (delta > 0.0001f) {
            if (max == rf) {
                h = 60f * (((gf - bf) / delta) % 6f);
            } else if (max == gf) {
                h = 60f * (((bf - rf) / delta) + 2f);
            } else {
                h = 60f * (((rf - gf) / delta) + 4f);
            }
        }
        if (h < 0) h += 360f;

        float s = max < 0.0001f ? 0f : delta / max;
        float v = max;

        return new float[]{h, s, v};
    }

    /**
     * A simple HSB palette record.
     *
     * @param hue        0-360 degrees
     * @param saturation 0-1
     */
    public record BiomePalette(float hue, float saturation) {

        /**
         * Smoothly interpolate between two palettes. Hue wrap-around (0°↔360°) is handled correctly.
         *
         * @param t blend factor 0.0 = a, 1.0 = b
         */
        public static BiomePalette lerp(BiomePalette a, BiomePalette b, float t) {
            if (a == null) return b;
            if (b == null) return a;

            // Handle hue wrap: take the shortest path around the circle
            float hueA = a.hue;
            float hueB = b.hue;
            float delta = hueB - hueA;
            if (delta > 180f) delta -= 360f;
            else if (delta < -180f) delta += 360f;

            float hue = hueA + delta * t;
            if (hue < 0) hue += 360f;
            else if (hue >= 360f) hue -= 360f;

            float sat = a.saturation + (b.saturation - a.saturation) * t;

            return new BiomePalette(hue, sat);
        }
    }
}
