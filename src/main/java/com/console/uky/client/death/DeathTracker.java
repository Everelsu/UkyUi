package com.console.uky.client.death;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraftforge.client.event.sound.PlaySoundEvent;

/**
 * Works out what killed the player, from the client's side of the wire.
 *
 * The {@code DamageSource} that did it never leaves the server: the client is told
 * a number, not a reason. So instead of asking, this watches — one sample per tick
 * of the handful of states that outlive the hit that caused them (burning, no air
 * left, a long fall, the bottom of the world) and one listener for the sound an
 * explosion makes. When the death screen opens it asks what was true most
 * recently, which is right often enough for something whose only job is to choose
 * a colour and a sentence.
 *
 * <p>Deliberately not clever about it. Guessing wrong costs a slightly-off palette;
 * guessing at all is what makes the screen feel like it noticed.
 */
public class DeathTracker {

    /** How far back a sample still counts, in client ticks. */
    private static final int WINDOW = 60;
    /** Fall far enough to be worth attributing a death to. */
    private static final float FALL_THRESHOLD = 3.5F;
    /** Blocks from the player an explosion has to be to have plausibly hit them. */
    private static final double BLAST_RANGE = 12.0D;

    private static int now;
    private static int burning = Integer.MIN_VALUE;
    private static int drowning = Integer.MIN_VALUE;
    private static int falling = Integer.MIN_VALUE;
    private static int voiding = Integer.MIN_VALUE;
    private static int blast = Integer.MIN_VALUE;
    private static int struck = Integer.MIN_VALUE;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null || mc.world == null) {
            DeathScene.end();
            reset();
            return;
        }

        // The open death screen is what says a death is in progress, not the health
        // value. 1.7.10 could trust the health: Minecraft.runTick put the screen up
        // only once getHealth() had reached zero, so the two could never disagree.
        // 1.12.2 opens it from the combat-event packet instead, which arrives on its
        // own schedule — for a tick or more the screen is up while the client still
        // holds the old health, and reading that as "alive again" ended the scene on
        // its very first tick. The clock then read zero forever: nothing faded in and
        // the way out never unlocked, because both are driven by the elapsed time.
        if (player.getHealth() > 0.0F && !(mc.currentScreen instanceof GuiGameOver)) {
            // Alive again — whatever was on screen belongs to a death that is over.
            DeathScene.end();
            sample(player);
        }
        now++;
    }

    /** Notes anything true this tick that could still be the cause a second later. */
    private static void sample(EntityPlayer player) {
        if (player.isBurning() || player.isInsideOfMaterial(Material.LAVA)) {
            burning = now;
        }
        if (player.getAir() <= 0) {
            drowning = now;
        }
        if (player.fallDistance > FALL_THRESHOLD) {
            falling = now;
        }
        if (player.posY < 1.0D) {
            voiding = now;
        }
        if (player.hurtTime > 0) {
            struck = now;
        }
    }

    /**
     * Notes an explosion going off near the player.
     *
     * There is no client-side event for the explosion itself — only the packet that
     * tells the client to make the noise and the particles. The noise will do.
     */
    @SubscribeEvent
    public void onSound(PlaySoundEvent event) {
        ISound sound = event.getSound();
        // "random.explode" on 1.7.10; the whole sound registry was renamed in 1.9.
        // Compared against the registry constant rather than a literal so a further
        // rename is a compile error rather than a scene that silently stops firing.
        if (sound == null || !SoundEvents.ENTITY_GENERIC_EXPLODE.getSoundName()
                .equals(sound.getSoundLocation())) {
            return;
        }
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) {
            return;
        }
        double dx = sound.getXPosF() - player.posX;
        double dy = sound.getYPosF() - player.posY;
        double dz = sound.getZPosF() - player.posZ;
        if (dx * dx + dy * dy + dz * dz <= BLAST_RANGE * BLAST_RANGE) {
            blast = now;
        }
    }

    /**
     * The best guess available, most specific cause first.
     *
     * Order matters where two are true at once, and they routinely are: burning in
     * lava at the bottom of a ravine is a fall, a fire and a strike all within the
     * same second. The one furthest from "something hit me" wins, because that is
     * the one that says something.
     */
    public static DeathTheme detect() {
        if (recent(voiding)) {
            return DeathTheme.VOID;
        }
        if (recent(blast)) {
            return DeathTheme.EXPLOSION;
        }
        if (recent(burning)) {
            return DeathTheme.FIRE;
        }
        if (recent(drowning)) {
            return DeathTheme.DROWN;
        }
        if (recent(falling)) {
            return DeathTheme.FALL;
        }
        if (recent(struck)) {
            return DeathTheme.MOB;
        }
        return DeathTheme.GENERIC;
    }

    private static boolean recent(int stamp) {
        return stamp != Integer.MIN_VALUE && now - stamp <= WINDOW;
    }

    /** Forgets everything. Called when there is no player left to have killed. */
    public static void reset() {
        burning = Integer.MIN_VALUE;
        drowning = Integer.MIN_VALUE;
        falling = Integer.MIN_VALUE;
        voiding = Integer.MIN_VALUE;
        blast = Integer.MIN_VALUE;
        struck = Integer.MIN_VALUE;
    }
}
