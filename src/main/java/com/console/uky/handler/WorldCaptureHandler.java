package com.console.uky.handler;

import com.console.uky.UkyUI;
import com.console.uky.client.death.DeathScene;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import com.console.uky.client.sound.UkySounds;
import com.console.uky.client.world.UkyLoadingScreen;
import com.console.uky.client.world.WorldPreviews;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.sound.SoundLoadEvent;
import org.lwjgl.opengl.GL11;

/**
 * Takes the world's parting screenshot.
 *
 * The tricky part is timing. At the moment the player clicks "Save and quit" the
 * screen shows the pause menu, and a frame later the world is gone — neither is
 * what should end up in the tile. So the quit is deferred: the menu closes, the
 * HUD is hidden, one clean frame of the world is drawn, that frame is read back,
 * and only then does the world actually unload.
 *
 * A frame is skipped between closing the menu and reading, because the buffer
 * still holds the menu until the next one is drawn.
 */
public class WorldCaptureHandler {

    private static String pendingFolder;
    private static Runnable pendingAction;
    private static boolean hidGui;
    private static int framesWaited;
    private static int ticksWaited;

    /**
     * Game ticks to wait for the render tick that takes the picture, before leaving
     * without one.
     *
     * The deferral is the risky part of this whole mechanism: between closing the menu
     * and running the action, the client is sitting in a world with no screen and
     * nothing scheduled to get it out. If the render tick that was supposed to finish
     * the job never arrives — the window minimised, the renderer stalled, another mod
     * throwing out of the same event — it sits there indefinitely while the integrated
     * server shuts down underneath it, and the next frame drawn is a frame of a world
     * whose registries FML has already begun taking apart.
     *
     * <p>Six, not two: the two frames this normally needs are two ticks only at twenty
     * frames a second, and a loaded pack quitting a world is routinely slower than that.
     * Set too tight, the picture would simply stop being taken on the machines that are
     * hardest to get one from. Three hundred milliseconds is still a bound.
     */
    private static final int MAX_TICKS = 6;

    /**
     * Captures the world, then runs {@code afterwards}.
     *
     * If there is no world, or a capture is already in flight, the action runs
     * immediately — quitting must never be blocked by a screenshot.
     */
    public static void captureThen(Runnable afterwards) {
        Minecraft mc = Minecraft.getMinecraft();
        if (pendingFolder != null || mc.world == null || !mc.isSingleplayer()
                || mc.getIntegratedServer() == null) {
            afterwards.run();
            return;
        }

        pendingFolder = mc.getIntegratedServer().getFolderName();
        pendingAction = afterwards;
        framesWaited = 0;
        ticksWaited = 0;

        hidGui = mc.gameSettings.hideGUI;
        mc.gameSettings.hideGUI = true;
        // Close the menu so the next frame is the world alone.
        mc.displayGuiScreen(null);
    }

    /**
     * The sound engine is rebuilt around a world load, which invalidates any
     * handle held across it. Without this the menu track is considered still
     * playing after coming back from a world, and stays silent.
     */
    @SubscribeEvent
    public void onSoundLoad(SoundLoadEvent event) {
        UkySounds.onSoundSystemReloaded();
        DeathScene.onSoundSystemReloaded();
    }

    /**
     * Seconds spent taking the world down to the colour the save screen comes up on.
     *
     * <p>Short. This is in the way of leaving, so it has to be felt rather than waited
     * for — the same reasoning as the menus' own close animation.
     */
    private static final float FADE_SECONDS = 0.32F;

    /** Set once the picture has been taken and the world is being darkened. */
    private static boolean fading;
    private static float faded;
    private static long lastFrameNanos;

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (pendingFolder == null || event.phase != TickEvent.Phase.END) {
            return;
        }
        if (fading) {
            advanceFade();
            return;
        }
        // The first frame after closing the menu is still the menu.
        if (framesWaited++ < 1) {
            return;
        }
        capture();
    }

    /**
     * Takes the world down to black before it is unloaded.
     *
     * <p>Leaving used to cut three times in a row: the menu vanished, two frames of the
     * bare world went past while the picture was taken, and then the save screen replaced
     * them. Nothing was wrong with any one of those steps — they simply had nothing
     * between them.
     *
     * <p>This is the piece that was missing, and it is drawn from here rather than from a
     * screen for the reason {@code WorldEntryFade} is: by this point there is no screen
     * left. It ends on {@link Theme#background} under a vignette at 0.8, which is not an
     * arbitrary dark — it is exactly what {@code UkyLoadingScreen} shows while the world
     * saves. So the frame this finishes on and the frame that screen starts on are the
     * same picture, and the join between them cannot be seen.
     */
    private static void advanceFade() {
        long now = System.nanoTime();
        // Clamped: saving a large world stutters, and a stutter must slow the fade rather
        // than skip it — skipping it is the cut this exists to remove.
        faded += Math.min((now - lastFrameNanos) / 1_000_000_000.0F, 0.1F);
        lastFrameNanos = now;

        float progress = Math.min(1.0F, faded / FADE_SECONDS);
        drawFade(Ease.inCubic(progress));
        if (progress >= 1.0F) {
            finish();
        }
    }

    /** The scrim, in its own projection because nothing else is set one up here. */
    private static void drawFade(float opacity) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) {
            finish();
            return;
        }
        ScaledResolution resolution =
                new ScaledResolution(mc);
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, width, height, 0.0D, 1000.0D, 3000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -2000.0F);

        boolean depth = GL11.glGetBoolean(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        Draw.rect(0, 0, width, height, Draw.withAlpha(Theme.background, opacity));
        Draw.vignette(width, height, 0.8F * opacity, 0xFF000000);

        GL11.glDepthMask(true);
        if (depth) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
    }

    /**
     * Reads the frame, then starts the fade rather than leaving straight away.
     *
     * The picture has to come off a frame with nothing over it, so it is taken before
     * the first frame of the scrim and not after.
     */
    private static void capture() {
        String folder = pendingFolder;
        if (folder == null) {
            return;
        }
        try {
            WorldPreviews.capture(folder);
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not capture world preview for {}", folder, t);
        }
        fading = true;
        faded = 0.0F;
        lastFrameNanos = System.nanoTime();
    }

    /**
     * The way out if the render tick never comes.
     *
     * Nothing here takes a picture — a game tick is not a frame and there is no buffer
     * to read. Its whole job is that the deferral cannot strand the client in a world
     * it has already decided to leave. Also fires the moment the world goes: if it has
     * gone there is nothing left to photograph, and the action is now overdue.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (pendingFolder == null || event.phase != TickEvent.Phase.END) {
            return;
        }
        // Counted only while waiting for the picture. Once the fade is running the render
        // tick is demonstrably arriving, and a deadline measured in game ticks would cut
        // the fade short on any machine drawing fewer than a handful of frames per tick.
        if (fading) {
            return;
        }
        if (++ticksWaited > MAX_TICKS || Minecraft.getMinecraft().world == null) {
            // No frame ever came, so there is no picture and nothing to fade from.
            finish();
        }
    }

    /**
     * Ends the deferral and leaves, once.
     *
     * The action runs from a finally, and the fields are cleared before any of it, so
     * that anything here throwing — or an event firing again while this is running —
     * cannot either skip the leaving or do it twice.
     */
    private static void finish() {
        Runnable action = pendingAction;
        pendingFolder = null;
        pendingAction = null;
        fading = false;
        faded = 0.0F;
        if (action == null) {
            return;
        }

        try {
            Minecraft.getMinecraft().gameSettings.hideGUI = hidGui;
            // Leaving is drawn by the same screen as arriving, so its picture and
            // clock have to be cleared or the save screen reuses the ones from the
            // load that is now ending.
            UkyLoadingScreen.rearmIfInstalled(Minecraft.getMinecraft());
            WorldPreviews.setEnteringWorld(null);
        } finally {
            action.run();
        }
    }
}
