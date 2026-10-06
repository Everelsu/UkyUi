package com.console.uky.client.mods;

import java.lang.reflect.Field;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

/**
 * Where ukygraves put the black box after this death, if it did, and what killed the
 * player as the server told it.
 *
 * <p>Read through reflection: ukygraves is optional, and neither mod should need the
 * other to build. The public fields on {@code GraveClient} (and its {@code Death}) are
 * the whole contract.
 */
public final class GravesLink {

    private static Field grave;
    private static Field at;
    private static boolean looked;
    private static Field death;
    private static Field deathCause;
    private static Field deathAt;
    private static boolean lookedDeath;

    private GravesLink() {}

    /** {x, y, z, dim} of a box recorded at or after {@code sinceMillis}, or null. */
    public static int[] graveSince(long sinceMillis) {
        if (!looked) {
            looked = true;
            try {
                Class<?> c = Class.forName("com.uky.graves.client.GraveClient");
                grave = c.getField("lastGrave");
                at = c.getField("lastGraveAt");
            } catch (ReflectiveOperationException | LinkageError e) {
                grave = null; // not installed
            }
        }
        if (grave == null) {
            return null;
        }
        try {
            int[] g = (int[]) grave.get(null);
            return g != null && at.getLong(null) >= sinceMillis ? g : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * The translation key of the death message ukygraves received at or after
     * {@code sinceMillis} ("death.attack.lava", "death.fell.accident.generic", ...), or
     * null: not installed, no such death, or a message that isn't a translation.
     */
    public static String causeSince(long sinceMillis) {
        if (!lookedDeath) {
            lookedDeath = true;
            try {
                Class<?> c = Class.forName("com.uky.graves.client.GraveClient");
                Class<?> d = Class.forName("com.uky.graves.client.GraveClient$Death");
                death = c.getField("lastDeath");
                deathCause = d.getField("cause");
                deathAt = d.getField("at");
            } catch (ReflectiveOperationException | LinkageError e) {
                death = null; // not installed, or an older ukygraves
            }
        }
        if (death == null) {
            return null;
        }
        try {
            Object d = death.get(null);
            if (d == null || deathAt.getLong(d) < sinceMillis) {
                return null;
            }
            IChatComponent c = IChatComponent.Serializer.func_150699_a((String) deathCause.get(d));
            return c instanceof ChatComponentTranslation ? ((ChatComponentTranslation) c).getKey() : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null; // a message that doesn't parse: back to guessing
        }
    }
}
