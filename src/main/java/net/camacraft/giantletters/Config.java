package net.camacraft.giantletters;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * The server config ({@code serverconfig/giantletters-server.toml} in the world, synced to clients): what a
 * glowing letter's light is, and how big a letter may get. Both sides read the same values, so a dye applied to a
 * letter on the server colours exactly the square the client draws as one letter.
 */
public final class Config {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.IntValue GLOW_LIGHT_LEVEL;
    private static final ForgeConfigSpec.IntValue MAX_LETTER_SIZE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("letters");
        GLOW_LIGHT_LEVEL = b.comment(
                "The light a glow-inked letter gives off, 0 to 15 (0: none, as a glowing sign gives none).",
                "Every block of a big letter gives this light. A change applies to letters as they are placed or dyed again.")
                .defineInRange("glowLightLevel", 7, 0, 15);
        MAX_LETTER_SIZE = b.comment(
                "The biggest square of one letter that becomes one bigger letter, in blocks across (2 to 64).",
                "A region of one letter with more than 4096 blocks never merges at all.")
                .defineInRange("maxLetterSize", 32, 2, 64);
        b.pop();
        SPEC = b.build();
    }

    private Config() {
    }

    /** The light level of a glowing letter (7 until a world's config is loaded). */
    public static int glowLightLevel() {
        return SPEC.isLoaded() ? GLOW_LIGHT_LEVEL.get() : 7;
    }

    /** The biggest letter, in blocks across (32 until a world's config is loaded). */
    public static int maxLetterSize() {
        return SPEC.isLoaded() ? MAX_LETTER_SIZE.get() : 32;
    }
}
