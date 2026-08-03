package com.console.uky.client.death;

import net.minecraft.client.resources.I18n;

import java.util.Random;

/**
 * What a death looks and sounds like, keyed by what did the killing.
 *
 * The scene is the same in every case — the same fade, the same tape artefacts,
 * the same slowing heartbeat — but drowning and burning have no business being
 * the same colour, and a fall should not sound like an explosion. Each cause
 * therefore carries its own palette, its own weighting on the audio, and its own
 * pool of lines.
 *
 * <p>Colours are {@code 0xRRGGBB}; alpha is applied per use.
 */
public enum DeathTheme {

    /** Anything unattributed: starvation, cactus, a mod's own damage source. */
    GENERIC("generic", 6, 0x2A0C0C, 0xC8A24B, 0xFF4438, 0x5FD2FF, 1.00F, 1.00F, 1.00F, 1.00F),
    /** Ground, at speed. Low, bruised, and short on tape noise — this one is silence. */
    FALL("fall", 6, 0x161320, 0xB9A98C, 0xFF6A5A, 0x6AB6FF, 1.15F, 0.85F, 0.75F, 0.85F),
    /** Fire and lava: the loudest palette, and the busiest picture. */
    FIRE("fire", 6, 0x431602, 0xFF9A3C, 0xFF7A20, 0xFFD07A, 1.05F, 1.10F, 1.20F, 1.25F),
    /** Water and suffocation. Cold, slow, and heavy on the breathing. */
    DROWN("drown", 6, 0x06222E, 0x54B7D8, 0x3FA8FF, 0x9FF0FF, 0.80F, 1.30F, 0.70F, 0.95F),
    /** Blast damage: the picture takes the beating, the heart barely notices. */
    EXPLOSION("explosion", 6, 0x3A2408, 0xFFC24A, 0xFF8A2A, 0xFFE08A, 1.10F, 0.90F, 1.45F, 1.35F),
    /** Out of the world. Almost no colour at all — there is nothing down there. */
    VOID("void", 6, 0x08070E, 0x8A6BD8, 0xB05CFF, 0x7FE6FF, 0.75F, 0.80F, 0.60F, 0.70F),
    /** Something killed you on purpose. */
    MOB("mob", 6, 0x2E0A12, 0xD8506A, 0xFF3355, 0x66E0D8, 1.20F, 1.05F, 1.10F, 1.00F);

    private static final Random RANDOM = new Random();

    private final String key;
    private final int messageCount;
    /** The colour vision fades out through, before the scene settles to black. */
    public final int bleed;
    /** Rules, the tape band, the underline — this theme's version of the accent. */
    public final int accent;
    /** The two halves of the chromatic split on the title. */
    public final int chromaRed;
    public final int chromaCyan;
    public final float heartbeat;
    public final float breathing;
    /** How much the picture shakes. */
    public final float jitter;
    /** How much tape damage there is: band, noise, tracking errors. */
    public final float vhs;

    DeathTheme(String key, int messageCount, int bleed, int accent, int chromaRed, int chromaCyan,
               float heartbeat, float breathing, float jitter, float vhs) {
        this.key = key;
        this.messageCount = messageCount;
        this.bleed = bleed;
        this.accent = accent;
        this.chromaRed = chromaRed;
        this.chromaCyan = chromaCyan;
        this.heartbeat = heartbeat;
        this.breathing = breathing;
        this.jitter = jitter;
        this.vhs = vhs;
    }

    /** Index of a line to show, picked once per death and then held. */
    public int pickMessage() {
        return RANDOM.nextInt(this.messageCount);
    }

    /**
     * One of this theme's lines.
     *
     * Falls back to the generic pool if a translation is missing, so a partially
     * translated pack shows a sentence rather than a raw key.
     */
    public String message(int index) {
        String key = "uky.death." + this.key + "." + (index % this.messageCount + 1);
        String line = I18n.format(key);
        if (!line.equals(key)) {
            return line;
        }
        String fallback = "uky.death.generic." + (index % GENERIC.messageCount + 1);
        String generic = I18n.format(fallback);
        return generic.equals(fallback) ? "" : generic;
    }
}
