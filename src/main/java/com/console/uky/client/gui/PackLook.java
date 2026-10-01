package com.console.uky.client.gui;

import com.console.uky.UkyUI;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import com.console.uky.core.PackMode;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.IOException;

/**
 * How the menus look in Easy or Standard, and the change from one to the other.
 *
 * <p>The menus follow the mode the player has <em>chosen</em> — pending or current —
 * not the one the mods are in this session. The mods only change on the next launch,
 * but the choice is made now, and a menu that waited for a restart to acknowledge it
 * would feel like it had not heard.
 *
 * <p>The change is kept quiet: the palette crossfades — interface and disk together —
 * under one thin ring leaving the point that was clicked. Once it has played, if the
 * mods still need a restart to follow, the player is offered one.
 */
public final class PackLook {

    /** How long the wave takes to cross the screen and the palette to settle. */
    private static final float SECONDS = 0.8F;

    private static boolean initialised;
    private static boolean available;
    /** Shown palette, 0 Standard .. 1 Easy. */
    private static float shown;
    private static float from;
    private static float to;
    /** Seconds into a change, or negative when none is running. */
    private static float age = -1.0F;
    private static float originX;
    /** Set when a change finishes and the mods still need a restart; taken by the menu. */
    private static boolean promptOwed;
    private static float originY;

    private PackLook() {
    }

    private static File gameDir() {
        return Minecraft.getMinecraft().mcDataDir;
    }

    private static void ensure() {
        if (initialised) {
            return;
        }
        initialised = true;
        available = PackMode.current(gameDir()) != null;
        shown = available && PackMode.EASY.equals(chosen()) ? 1.0F : 0.0F;
        Theme.setEasy(shown);
    }

    /** Whether this pack has an Easy mode at all. */
    public static boolean available() {
        ensure();
        return available;
    }

    /** What the player picked: waiting for the next launch if anything is, else what is installed. */
    public static String chosen() {
        String pending = PackMode.pending(gameDir());
        return pending != null ? pending : PackMode.current(gameDir());
    }

    /** Whether the pick differs from what is installed, so a restart is still owed. */
    public static boolean restartPending() {
        String pending = PackMode.pending(gameDir());
        return pending != null && !pending.equals(PackMode.current(gameDir()));
    }

    /** 0 Standard .. 1 Easy, as shown this frame. */
    public static float easy() {
        ensure();
        return shown;
    }

    /**
     * Picks a mode and plays the change from where the click was.
     *
     * {@code x}, {@code y}: the click, in the screen's units.
     */
    public static void choose(String mode, float x, float y) {
        ensure();
        if (!available) {
            return;
        }
        try {
            PackMode.request(gameDir(), mode);
        } catch (IOException e) {
            UkyUI.LOGGER.warn("Could not save the pack mode", e);
            return;
        }
        float target = PackMode.EASY.equals(mode) ? 1.0F : 0.0F;
        if (target == to && age >= 0.0F || target == shown && age < 0.0F) {
            return;
        }
        from = shown;
        to = target;
        age = 0.0F;
        originX = x;
        originY = y;
        MenuScreen.flareHole(0.35F);
    }

    /** True once per finished change that left a restart owed. */
    static boolean takePrompt() {
        boolean owed = promptOwed;
        promptOwed = false;
        return owed;
    }

    /** Advances the change; called once a frame by every menu. */
    static void update(float delta) {
        ensure();
        if (age < 0.0F) {
            return;
        }
        age += delta;
        float t = Ease.inOutCubic(age / SECONDS);
        shown = from + (to - from) * t;
        Theme.setEasy(shown);
        if (age >= SECONDS) {
            age = -1.0F;
            shown = to;
            Theme.setEasy(shown);
            promptOwed = restartPending();
        }
    }

    /**
     * The wave: one thin ring leaving the click, gone by the time it reaches the far
     * corner. Everything else is the palette itself moving underneath it.
     */
    static void draw(float width, float height) {
        if (age < 0.0F) {
            return;
        }
        float t = age / SECONDS;
        float r = Ease.outCubic(t) * farthestCorner(width, height);
        float fade = 1.0F - Ease.inCubic(t);
        Draw.ring(originX, originY, r, 1.5F + 2.5F * fade,
                Draw.withAlpha(Theme.accent, 0.45F * fade));
    }

    private static float farthestCorner(float w, float h) {
        float dx = Math.max(originX, w - originX);
        float dy = Math.max(originY, h - originY);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
