package org.cneko.ledit.color;

/**
 * Generates per-LED RGB colors using time-based sine wave animation.
 * The algorithm creates an ambient, flowing feel by combining:
 * <ul>
 *   <li>A base hue from the biome palette with slow wandering</li>
 *   <li>A traveling brightness wave across the LED strip</li>
 *   <li>Health-based breathing pulse (faster/stronger at low HP)</li>
 *   <li>Hue shift toward red as health decreases</li>
 * </ul>
 */
public final class ColorAnimator {

    private static final double TAU = Math.PI * 2.0;

    public ColorAnimator() {
    }

    /**
     * Generate RGB colors for every LED on the strip.
     *
     * @param timeSeconds  animation time in seconds (1.0 = one second of animation)
     * @param ledCount     total number of LEDs
     * @param palette      biome color palette (hue and saturation)
     * @param healthRatio  player health ratio (1.0 = full, 0.0 = dead)
     * @return a 2D array of [ledIndex][R,G,B] where R,G,B are 0-255
     */
    public int[][] generate(double timeSeconds, int ledCount, BiomeColors.BiomePalette palette, float healthRatio) {
        int[][] colors = new int[ledCount][3];
        double t = timeSeconds;

        for (int i = 0; i < ledCount; i++) {
            double pos = ledCount == 1 ? 0.5 : (double) i / (ledCount - 1); // 0.0 .. 1.0

            // --- Hue ---
            // Gentle wander around the biome base hue
            double hueWander = Math.sin(t * 0.3) * 8.0;

            // Low HP shifts hue toward red (0°)
            double healthShift = (1.0 - healthRatio) * 30.0;

            double hue = palette.hue() + hueWander - healthShift;
            // Normalize to [0, 360)
            hue = ((hue % 360) + 360) % 360;

            // --- Saturation ---
            // Nearly static — pure color is paramount for RGB LEDs.
            // m = v * (1-s) is the white component; keep s ≥ 0.85 so m ≤ 15%.
            double satBreathe = Math.sin(t * 0.5) * 0.02 + 0.98; // 0.96 .. 1.00
            double sat = Math.max(0.85, palette.saturation() * satBreathe);
            sat = Math.min(sat, 1.0);

            // --- Brightness (Value) ---
            // Traveling wave — subtle shimmer across the strip
            double wave = Math.sin(pos * TAU + t * 1.2) * 0.10 + 0.90; // 0.80 .. 1.00

            // Health-based breathing pulse
            double breathFreq = 0.6 + (1.0 - healthRatio) * 2.0;  // 0.6 .. 2.6
            double breathAmp = 0.03 + (1.0 - healthRatio) * 0.22;  // 0.03 .. 0.25
            double breath = Math.sin(t * breathFreq) * breathAmp + (1.0 - breathAmp);

            double value = wave * breath;
            // Floor at 0.50 — dim but still visibly colored
            value = Math.max(0.50, Math.min(1.0, value));

            // Convert HSV → RGB
            int[] rgb = hsvToRgb((float) hue, (float) sat, (float) value);
            colors[i] = rgb;
        }

        return colors;
    }

    /**
     * HSV to RGB conversion.
     *
     * @param h hue 0-360
     * @param s saturation 0-1
     * @param v value/brightness 0-1
     * @return int[3] of R,G,B each 0-255
     */
    static int[] hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = v - c;

        float rf, gf, bf;
        if (h < 60) {
            rf = c; gf = x; bf = 0;
        } else if (h < 120) {
            rf = x; gf = c; bf = 0;
        } else if (h < 180) {
            rf = 0; gf = c; bf = x;
        } else if (h < 240) {
            rf = 0; gf = x; bf = c;
        } else if (h < 300) {
            rf = x; gf = 0; bf = c;
        } else {
            rf = c; gf = 0; bf = x;
        }

        return new int[]{
                Math.round((rf + m) * 255f),
                Math.round((gf + m) * 255f),
                Math.round((bf + m) * 255f)
        };
    }
}
