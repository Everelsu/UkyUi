package com.console.uky.client.death;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

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
 *
 * <p>With ukycorpses installed there is no guessing: it hands its own client the
 * server's death message, and that names the damage source.
 */
public class DeathTracker {

    /** How far back a sample still counts, in client ticks. */
    private static final int WINDOW = 60;
    /** Fall far enough to be worth attributing a death to. */
    private static final float FALL_THRESHOLD = 3.5F;
    /** Blocks from the player an explosion has to be to have plausibly hit them. */
    private static final double BLAST_RANGE = 12.0D;
    /** How old ukycorpses' word on the death may be: its death camera runs first. */

    private static int now;
    private static int burning = Integer.MIN_VALUE;
    private static int drowning = Integer.MIN_VALUE;
    private static int falling = Integer.MIN_VALUE;
    private static int voiding = Integer.MIN_VALUE;
    private static int blast = Integer.MIN_VALUE;
    private static int struck = Integer.MIN_VALUE;

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            DeathScene.end();
            reset();
            return;
        }

        if (player.getHealth() > 0.0F) {
            // Alive again — whatever was on screen belongs to a death that is over.
            DeathScene.end();
            sample(player);
        }
        now++;
    }

    /** Notes anything true this tick that could still be the cause a second later. */
    private static void sample(Player player) {
        if (player.isOnFire() || player.isInLava()) {
            burning = now;
        }
        if (player.getAirSupply() <= 0) {
            drowning = now;
        }
        if (player.fallDistance > FALL_THRESHOLD) {
            falling = now;
        }
        if (player.getY() < player.level().getMinBuildHeight() + 1.0D) {
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
        if (event.getSound() == null
                || !"minecraft:entity.generic.explode".equals(event.getSound().getLocation().toString())) {
            return;
        }
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        double dx = event.getSound().getX() - player.getX();
        double dy = event.getSound().getY() - player.getY();
        double dz = event.getSound().getZ() - player.getZ();
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
        DeathTheme told = fromMessage(toldKey);
        if (told != null) {
            return told;
        }
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

    /**
     * The theme for a vanilla death message key, null for none. Downed and bled out,
     * the key is still the blow that knocked them down.
     */
    static DeathTheme fromMessage(String key) {
        if (key == null) {
            return null;
        }
        if (key.contains("outOfWorld")) {
            return DeathTheme.VOID;
        }
        if (key.contains("explosion")) {
            return DeathTheme.EXPLOSION;
        }
        if (key.contains("inFire") || key.contains("onFire") || key.contains("lava")
                || key.contains("fireball")) {
            return DeathTheme.FIRE;
        }
        if (key.contains("drown") || key.contains("inWall")) {
            return DeathTheme.DROWN;
        }
        if (key.startsWith("death.fell") || key.contains("attack.fall")) {
            return DeathTheme.FALL;
        }
        if (key.contains("mob") || key.contains("player") || key.contains("arrow") || key.contains("thrown")
                || key.contains("indirectMagic") || key.contains("thorns")) {
            return DeathTheme.MOB;
        }
        return DeathTheme.GENERIC;
    }

    private static boolean recent(int stamp) {
        return stamp != Integer.MIN_VALUE && now - stamp <= WINDOW;
    }

    /** Forgets everything. Called when there is no player left to have killed. */
    /**
     * The translation key of the death message the server sent, if it sent one.
     *
     * 1.7.10's client was never told what killed it, which is why everything above
     * guesses. 1.21 sends the message with the death itself, and vanilla's death
     * screen carries it; whatever it says beats any guess.
     */
    private static String toldKey;

    public static void told(String key) {
        toldKey = key;
    }

    public static void reset() {
        toldKey = null;
        burning = Integer.MIN_VALUE;
        drowning = Integer.MIN_VALUE;
        falling = Integer.MIN_VALUE;
        voiding = Integer.MIN_VALUE;
        blast = Integer.MIN_VALUE;
        struck = Integer.MIN_VALUE;
    }
}
