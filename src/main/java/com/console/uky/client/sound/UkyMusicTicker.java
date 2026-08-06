package com.console.uky.client.sound;

import com.console.uky.UkyUI;
import com.console.uky.config.UiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MusicTicker;

import java.lang.reflect.Field;

/**
 * Keeps Minecraft's own music out of the menus.
 *
 * Vanilla's ticker plays {@code music.menu} whenever no world is loaded, which is
 * precisely when this mod is playing its own track — so the two ran on top of each
 * other. There is no hook for that: {@code Minecraft.mcMusicTicker} is private and
 * its {@code update()} is called unconditionally every tick. So the field is
 * swapped for this subclass, which simply declines to tick while the menus belong
 * to us.
 *
 * <p>Turning off {@code mainmenu.music} hands the menus back to vanilla, which is
 * the behaviour a pack author would expect from that switch.
 */
public class UkyMusicTicker extends MusicTicker {

    /**
     * The one {@link ISound} field on {@code MusicTicker} — whatever it currently
     * has playing. Located by type rather than by name, because the MCP name only
     * exists in a development environment.
     */
    private static Field currentTrack;
    private static boolean currentTrackResolved;

    private final Minecraft mc;

    public UkyMusicTicker(Minecraft mc) {
        super(mc);
        this.mc = mc;
    }

    /** Logged once, so "the swap happened" and "the swap has any effect" stay separable. */
    private static boolean loggedSuppression;

    @Override
    public void update() {
        if (!ownsTheMenus()) {
            super.update();
            return;
        }
        if (!loggedSuppression) {
            loggedSuppression = true;
            UkyUI.LOGGER.info("Menu music tick suppressed; the replacement ticker is live");
        }
        // Whatever vanilla has going belongs to the world that was just left; it
        // would normally be stopped by the very update() being skipped here, so it
        // has to be stopped explicitly or a game track plays on over the menus.
        stopVanillaTrack();
    }

    private boolean ownsTheMenus() {
        return UiConfig.menuMusic && this.mc.world == null;
    }

    private void stopVanillaTrack() {
        Field field = resolveCurrentTrack();
        if (field == null || this.mc.getSoundHandler() == null) {
            return;
        }
        try {
            ISound playing = (ISound) field.get(this);
            if (playing == null) {
                return;
            }
            this.mc.getSoundHandler().stopSound(playing);
            field.set(this, null);
        } catch (IllegalAccessException e) {
            // Nothing to be done about it; at worst one leftover track finishes.
            currentTrack = null;
        }
    }

    private static Field resolveCurrentTrack() {
        if (currentTrackResolved) {
            return currentTrack;
        }
        currentTrackResolved = true;
        for (Field candidate : MusicTicker.class.getDeclaredFields()) {
            if (ISound.class.isAssignableFrom(candidate.getType())) {
                candidate.setAccessible(true);
                currentTrack = candidate;
                return currentTrack;
            }
        }
        UkyUI.LOGGER.warn("Could not find the music ticker's current track; a vanilla "
                + "track may play over the menu music once");
        return null;
    }

    /**
     * Swaps the game's music ticker for this one.
     *
     * Done by field type for the same reason as above. Failure is not fatal — the
     * menus just get vanilla's music underneath ours, which is what happened before.
     */
    public static void install(Minecraft mc) {
        try {
            Field field = resolveTickerField();
            if (field == null) {
                UkyUI.LOGGER.warn("No music ticker field found; vanilla menu music will play");
                return;
            }
            if (field.get(mc) instanceof UkyMusicTicker) {
                return;
            }
            field.set(mc, new UkyMusicTicker(mc));
            UkyUI.LOGGER.info("Music ticker replaced; vanilla menu music suppressed");
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not replace the music ticker", t);
        }
    }

    /**
     * Re-asserts the swap if something has replaced the ticker since.
     *
     * Cheap enough to call whenever a screen opens, and it is the backstop for the
     * ordering problem that made the first attempt at this silently ineffective:
     * being installed is worth verifying rather than assuming.
     */
    public static void ensureInstalled(Minecraft mc) {
        install(mc);
    }

    private static Field tickerField;
    private static boolean tickerFieldResolved;

    private static Field resolveTickerField() {
        if (tickerFieldResolved) {
            return tickerField;
        }
        tickerFieldResolved = true;
        for (Field candidate : Minecraft.class.getDeclaredFields()) {
            if (MusicTicker.class.isAssignableFrom(candidate.getType())) {
                candidate.setAccessible(true);
                tickerField = candidate;
                break;
            }
        }
        return tickerField;
    }
}
