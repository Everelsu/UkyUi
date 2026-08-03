package com.console.uky.client.death;

import com.console.uky.client.sound.DeathLoop;
import com.console.uky.config.UiConfig;
import net.minecraft.client.Minecraft;

/**
 * One death, from the hit to the respawn.
 *
 * State lives here rather than on the screen because the screen does not survive
 * the death it belongs to. Pressing Respawn sends a packet and closes the screen,
 * but the player stays dead until the server answers — and vanilla's
 * {@code displayGuiScreen(null)} opens a fresh death screen for as long as that is
 * true. A scene held on the screen would restart the whole cinematic, sounds and
 * all, in the gap. Held here, the new screen simply picks up where the old one
 * left off.
 *
 * <p>{@link DeathTracker} is what ends it: the scene is over when the player has
 * health again, or when there is no player.
 */
public final class DeathScene {

    private static boolean active;
    /**
     * Set once the player has asked to come back, and cleared only by a new death.
     *
     * The respawn is a round trip: the packet goes, the screen closes, and vanilla
     * immediately opens another death screen because the player is still dead. That
     * new screen must not ask again — without this it would, on the very first tick,
     * and go on asking every tick until the server answered.
     */
    private static boolean returning;
    private static long startedAt;
    private static DeathTheme theme = DeathTheme.GENERIC;
    private static int messageIndex;
    private static DeathLoop heartbeat;
    private static DeathLoop breathing;

    private DeathScene() {
    }

    /** Starts a scene, or does nothing if one is already running. */
    public static void begin() {
        if (active) {
            return;
        }
        active = true;
        returning = false;
        startedAt = System.nanoTime();
        theme = DeathTracker.detect();
        messageIndex = theme.pickMessage();
        startSounds();
    }

    /** Ends the scene and silences it. Safe to call when nothing is running. */
    public static void end() {
        if (!active) {
            return;
        }
        active = false;
        returning = false;
        stopSounds();
    }

    public static boolean isActive() {
        return active;
    }

    /** Records that the player has asked to come back; see {@link #returning}. */
    public static void requestReturn() {
        returning = true;
    }

    public static boolean isReturning() {
        return returning;
    }

    /** Seconds since the death. */
    public static float elapsed() {
        return active ? (System.nanoTime() - startedAt) / 1_000_000_000.0F : 0.0F;
    }

    public static DeathTheme theme() {
        return theme;
    }

    public static String message() {
        return theme.message(messageIndex);
    }

    // ------------------------------------------------------------------ sound --

    private static void startSounds() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!UiConfig.deathSounds || mc.getSoundHandler() == null) {
            return;
        }
        heartbeat = DeathLoop.heartbeat(theme);
        breathing = DeathLoop.breathing(theme);
        mc.getSoundHandler().playSound(heartbeat);
        mc.getSoundHandler().playSound(breathing);
    }

    private static void stopSounds() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getSoundHandler() != null) {
            if (heartbeat != null) {
                mc.getSoundHandler().stopSound(heartbeat);
            }
            if (breathing != null) {
                mc.getSoundHandler().stopSound(breathing);
            }
        }
        heartbeat = null;
        breathing = null;
    }

    /**
     * Drops the handles without touching the sound engine.
     *
     * For a sound system that has been torn down and rebuilt — the old handles then
     * name tracks in an engine that no longer exists, and stopping them is at best
     * pointless.
     */
    public static void onSoundSystemReloaded() {
        heartbeat = null;
        breathing = null;
    }
}
