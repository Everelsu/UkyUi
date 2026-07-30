package com.console.uky.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.util.ResourceLocation;

/**
 * The mod's own sound events, wired to {@code assets/uky/sounds.json}.
 *
 * All of these are UI sounds: they play at the listener with no attenuation, so
 * they work with no world loaded.
 */
public final class UkySounds {

    public static final ResourceLocation INTRO_IMPACT = new ResourceLocation("uky", "intro_impact");
    public static final ResourceLocation MENU_MUSIC = new ResourceLocation("uky", "menu_music");
    public static final ResourceLocation BUTTON = new ResourceLocation("uky", "button");

    /** The looping menu track, kept so it can be stopped when leaving the menus. */
    private static LoopingSound music;
    /**
     * Guards the restart poll. The menu asks to start the music every frame, so a
     * handle that has gone stale — which is what happens when the sound system is
     * torn down and rebuilt around a world load — would otherwise spawn a fresh
     * track sixty times a second.
     */
    private static long nextRestartAttempt;
    private static final long RESTART_INTERVAL_MS = 1000L;

    private UkySounds() {
    }

    /**
     * Fires a one-shot UI sound. Safe to call before a world exists.
     *
     * Deliberately not a {@link PositionedSoundRecord}: its public constructor
     * forces linear attenuation, and a UI sound has nowhere sensible to be. At (0,
     * 0, 0) that worked by accident until the player had been in a world — the sound
     * engine's listener stays wherever they last stood, so afterwards every click
     * was played a few hundred blocks away and attenuated to nothing. That is the
     * whole reason the button sounds went quiet after entering and leaving a world.
     * Attenuation NONE plays it at the listener, wherever that happens to be.
     */
    public static void play(ResourceLocation sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getSoundHandler() == null) {
            return;
        }
        mc.getSoundHandler().playSound(new UiSound(sound, volume, pitch));
    }

    public static void play(ResourceLocation sound) {
        play(sound, 1.0F, 1.0F);
    }

    /**
     * Starts the menu loop, or restarts it if it has stopped.
     *
     * Called every frame the title screen is up. Loading a world tears the sound
     * system down and rebuilds it, which silently invalidates whatever was playing
     * before — so rather than starting the track once and trusting it, this checks
     * that it is still running and revives it if not. The rate limit is what keeps
     * that check from turning into sixty overlapping tracks a second when the
     * sound never manages to start at all.
     */
    public static void startMusic(float volume) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getSoundHandler() == null) {
            return;
        }
        if (music != null && mc.getSoundHandler().isSoundPlaying(music)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextRestartAttempt) {
            return;
        }
        nextRestartAttempt = now + RESTART_INTERVAL_MS;

        music = new LoopingSound(MENU_MUSIC, volume);
        mc.getSoundHandler().playSound(music);
    }

    public static void stopMusic() {
        Minecraft mc = Minecraft.getMinecraft();
        if (music == null || mc.getSoundHandler() == null) {
            return;
        }
        mc.getSoundHandler().stopSound(music);
        music = null;
    }

    /**
     * Drops the cached handle. Called when the sound system reloads: the old
     * reference then refers to a track in an engine that no longer exists, and
     * keeping it would convince {@link #startMusic} there is nothing to do.
     */
    public static void onSoundSystemReloaded() {
        music = null;
        nextRestartAttempt = 0L;
    }

    /** Non-attenuated one-shot; {@link PositionedSound}'s fields are protected. */
    private static final class UiSound extends PositionedSound {
        private UiSound(ResourceLocation location, float volume, float pitch) {
            super(location);
            this.volume = volume;
            this.field_147663_c = pitch;
            this.field_147666_i = ISound.AttenuationType.NONE;
        }
    }

    /** Non-attenuated looping sound, for the menu track. */
    private static final class LoopingSound extends PositionedSound {
        private LoopingSound(ResourceLocation location, float volume) {
            super(location);
            this.volume = volume;
            this.repeat = true;
            this.field_147665_h = 0;
            this.field_147666_i = ISound.AttenuationType.NONE;
        }
    }
}
