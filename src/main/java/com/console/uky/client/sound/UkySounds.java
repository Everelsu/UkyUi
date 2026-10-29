package com.console.uky.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

import java.lang.reflect.Field;

/**
 * The mod's own sound events, wired to {@code assets/uky/sounds.json}.
 *
 * All of these are UI sounds: they play at the listener with no attenuation, so
 * they work with no world loaded.
 */
public final class UkySounds {

    public static final ResourceLocation INTRO_IMPACT = ResourceLocation.fromNamespaceAndPath("uky", "intro_impact");
    /** The build-up under the intro's gather; ends in silence right where the impact lands. */
    public static final ResourceLocation INTRO_RISER = ResourceLocation.fromNamespaceAndPath("uky", "intro_riser");
    public static final ResourceLocation MENU_MUSIC = ResourceLocation.fromNamespaceAndPath("uky", "menu_music");
    public static final ResourceLocation BUTTON = ResourceLocation.fromNamespaceAndPath("uky", "button");
    public static final ResourceLocation DELETE_HOLD = ResourceLocation.fromNamespaceAndPath("uky", "delete_hold");
    public static final ResourceLocation DELETE_BREAK = ResourceLocation.fromNamespaceAndPath("uky", "delete_break");
    /**
     * The toast's own hit. Named after the file, typo included: renaming the ogg
     * would be a resource-pack-breaking change for the sake of one letter nobody
     * reads, and the sound event above it is what the rest of the mod refers to.
     */
    public static final ResourceLocation ACHIEVEMENT = ResourceLocation.fromNamespaceAndPath("uky", "achievement");

    /**
     * Length of {@code delete_hold.ogg}, in seconds.
     *
     * The hold gesture is timed to this rather than the other way round: the sound is
     * one rising take that ends where the break begins, so a hold shorter than the file
     * would cut it off mid-rise and a longer one would leave silence before the world
     * broke. Read off the file — recut the sound and this number moves with it.
     */
    public static final float DELETE_HOLD_SECONDS = 4.52F;

    /** The looping menu track, kept so it can be stopped when leaving the menus. */
    private static LoopingSound music;
    /** The hold take, kept so letting go early can cut it off. */
    private static UiSound deleteHold;
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
     * Deliberately not a {@code SimpleSoundInstance}: its public constructor
     * forces linear attenuation, and a UI sound has nowhere sensible to be. At (0,
     * 0, 0) that worked by accident until the player had been in a world — the sound
     * engine's listener stays wherever they last stood, so afterwards every click
     * was played a few hundred blocks away and attenuated to nothing. That is the
     * whole reason the button sounds went quiet after entering and leaving a world.
     * Attenuation NONE plays it at the listener, wherever that happens to be.
     */
    public static SoundInstance play(ResourceLocation sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) {
            return null;
        }
        SoundInstance handle = new UiSound(sound, volume, pitch);
        mc.getSoundManager().play(handle);
        return handle;
    }

    public static void stop(SoundInstance sound) {
        Minecraft mc = Minecraft.getInstance();
        if (sound != null && mc.getSoundManager() != null) {
            mc.getSoundManager().stop(sound);
        }
    }

    private static Field managerField;
    private static Field loadedField;
    private static boolean readinessUnknown;

    /**
     * Whether the sound engine can play anything right now.
     *
     * The resource reload at the end of start-up tears the engine down and brings it
     * back up on a thread of its own, and {@code SoundManager.playSound} silently drops
     * whatever arrives in between — which is exactly when the title intro wants its
     * hit. Answers true when the fields cannot be found, so a renamed field costs a
     * wait, never a sound.
     */
    public static boolean isReady() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) {
            return false;
        }
        if (readinessUnknown) {
            return true;
        }
        try {
            if (managerField == null) {
                managerField = field(SoundManager.class, "soundEngine");
                loadedField = field(SoundEngine.class, "loaded");
            }
            Object manager = managerField.get(mc.getSoundManager());
            return manager != null && loadedField.getBoolean(manager);
        } catch (Throwable t) {
            readinessUnknown = true;
            return true;
        }
    }

    /** NeoForge runs on Mojang's names in production too, so one name is enough. */
    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        return f;
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) {
            return;
        }
        if (music != null && mc.getSoundManager().isActive(music)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextRestartAttempt) {
            return;
        }
        nextRestartAttempt = now + RESTART_INTERVAL_MS;

        music = new LoopingSound(MENU_MUSIC, volume);
        mc.getSoundManager().play(music);
    }

    public static void stopMusic() {
        Minecraft mc = Minecraft.getInstance();
        if (music == null || mc.getSoundManager() == null) {
            return;
        }
        mc.getSoundManager().stop(music);
        music = null;
    }

    /**
     * Starts the hold take, if it is not already running.
     *
     * Called every frame the bin is held, like the menu track, because the gesture has
     * no single moment to hook: it begins the first frame the button is down over the
     * bin and that frame is not distinguishable from any other without keeping state
     * the caller should not have to keep.
     */
    public static void startDeleteHold() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null || deleteHold != null) {
            return;
        }
        deleteHold = new UiSound(DELETE_HOLD, 1.0F, 1.0F);
        mc.getSoundManager().play(deleteHold);
    }

    /**
     * Cuts the hold take off, whether the gesture was completed or abandoned.
     *
     * Both endings want it stopped: letting go halfway must not leave the rise playing
     * on into nothing, and finishing hands over to {@link #DELETE_BREAK}, which is a
     * different sound and should not have the tail of this one under it.
     */
    public static void stopDeleteHold() {
        Minecraft mc = Minecraft.getInstance();
        if (deleteHold == null) {
            return;
        }
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().stop(deleteHold);
        }
        deleteHold = null;
    }

    /**
     * Drops the cached handle. Called when the sound system reloads: the old
     * reference then refers to a track in an engine that no longer exists, and
     * keeping it would convince {@link #startMusic} there is nothing to do.
     */
    public static void onSoundSystemReloaded() {
        music = null;
        deleteHold = null;
        nextRestartAttempt = 0L;
    }

    /**
     * Non-attenuated one-shot; {@link AbstractSoundInstance}'s fields are protected.
     * Relative to the listener, at its own position, so it is heard there in or out of
     * a world.
     */
    private static final class UiSound extends AbstractSoundInstance {
        private UiSound(ResourceLocation location, float volume, float pitch) {
            super(location, SoundSource.MASTER, SoundInstance.createUnseededRandom());
            this.volume = volume;
            this.pitch = pitch;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = true;
        }
    }

    /** Non-attenuated looping sound, for the menu track. */
    private static final class LoopingSound extends AbstractSoundInstance {
        private LoopingSound(ResourceLocation location, float volume) {
            super(location, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.volume = volume;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = true;
        }
    }
}
