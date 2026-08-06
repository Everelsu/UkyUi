package com.console.uky.client.sound;

import com.console.uky.client.death.DeathScene;
import com.console.uky.client.death.DeathTheme;
import com.console.uky.client.render.Ease;
import com.console.uky.config.UiConfig;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;

/**
 * The two loops under the death screen: a heart winding down and breathing that
 * does not settle.
 *
 * A {@link MovingSound} rather than a plain looping one because the sound engine
 * only re-reads volume and pitch for sounds that tick — and the whole point of
 * these two is that they move. The heart is loud and quick at the moment of the
 * hit and drags to a slow, distant thud; the breathing arrives a beat later, when
 * the picture has gone.
 *
 * <p>Each instance reads its own position off {@link DeathScene}, so nothing has to
 * push updates at them, and both stop themselves the moment the scene ends.
 */
public class DeathLoop extends MovingSound {

    public static final ResourceLocation HEARTBEAT = new ResourceLocation("uky", "heartbeat");
    public static final ResourceLocation BREATHING = new ResourceLocation("uky", "breathing");

    /** Seconds the heart takes to go from the hit to its resting drag. */
    private static final float HEART_DECAY = 9.0F;

    /**
     * Volume floor, and not a cosmetic one.
     *
     * {@code SoundManager.playSound} drops any sound whose volume is exactly zero at
     * the moment it is handed over — logs "volume was zero" and never registers it.
     * The breathing starts silent by design, so it was being thrown away every time
     * and simply never played. Inaudible is fine; zero is not.
     */
    private static final float MIN_VOLUME = 0.02F;

    private final boolean heart;
    private final float themeVolume;

    private DeathLoop(ResourceLocation sound, boolean heart, float themeVolume) {
        // 1.12.2's MovingSound takes a SoundEvent rather than a location. An
        // unregistered one is enough: all the constructor wants from it is the name,
        // and the sound itself is resolved from sounds.json by that name either way.
        super(new SoundEvent(sound), SoundCategory.MASTER);
        this.heart = heart;
        this.themeVolume = themeVolume;
        this.repeat = true;
        this.repeatDelay = 0;
        // Played at the listener: there is no world position for the inside of
        // someone's chest, and attenuating it would silence it the moment the camera
        // drifts away from where the body fell.
        this.attenuationType = ISound.AttenuationType.NONE;
        this.volume = 0.0F;
        this.pitch = 1.0F;
        update();
    }

    public static DeathLoop heartbeat(DeathTheme theme) {
        return new DeathLoop(HEARTBEAT, true, theme.heartbeat);
    }

    public static DeathLoop breathing(DeathTheme theme) {
        return new DeathLoop(BREATHING, false, theme.breathing);
    }

    @Override
    public void update() {
        if (!DeathScene.isActive()) {
            this.donePlaying = true;
            this.volume = 0.0F;
            return;
        }
        float t = DeathScene.elapsed();
        float master = (float) UiConfig.deathVolume * this.themeVolume;

        if (this.heart) {
            // Starts hard and immediate, then drags: both the level and the rate
            // come down together, which is what reads as a heart giving up rather
            // than a sound being faded out.
            float decay = Ease.outCubic(t / HEART_DECAY);
            this.volume = Math.max(MIN_VOLUME, master * (1.00F - 0.55F * decay));
            this.pitch = 1.08F - 0.30F * decay;
            return;
        }

        // Breathing holds off until the picture has gone, so the two are heard one
        // after the other rather than as one noise.
        float rise = Ease.outCubic((t - 0.8F) / 1.2F);
        this.volume = Math.max(MIN_VOLUME, master * 0.75F * rise);
        this.pitch = 0.96F;
    }
}
