package com.console.uky.handler;

import com.console.uky.client.sound.UkySounds;
import com.console.uky.client.world.UkyLoadingScreen;
import com.console.uky.client.world.WorldPreviews;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.sound.SoundLoadEvent;

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
        if (pendingFolder != null || mc.theWorld == null || !mc.isSingleplayer()
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
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (pendingFolder == null || event.phase != TickEvent.Phase.END) {
            return;
        }
        // The first frame after closing the menu is still the menu.
        if (framesWaited++ < 1) {
            return;
        }
        finish(true);
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
        if (++ticksWaited > MAX_TICKS || Minecraft.getMinecraft().theWorld == null) {
            finish(false);
        }
    }

    /**
     * Ends the deferral: picture if there is one to take, then the action, once.
     *
     * The action runs from a finally, and the fields are cleared before any of it, so
     * that a capture throwing — or an event firing again while this is running — cannot
     * either skip the leaving or do it twice.
     */
    private static void finish(boolean takePicture) {
        String folder = pendingFolder;
        Runnable action = pendingAction;
        pendingFolder = null;
        pendingAction = null;
        if (action == null) {
            return;
        }

        try {
            if (takePicture) {
                WorldPreviews.capture(folder);
            }
        } finally {
            Minecraft.getMinecraft().gameSettings.hideGUI = hidGui;
            // Leaving is drawn by the same screen as arriving, so its picture and
            // clock have to be cleared or the save screen reuses the ones from the
            // load that is now ending.
            UkyLoadingScreen.rearmIfInstalled(Minecraft.getMinecraft());
            WorldPreviews.setEnteringWorld(null);
            action.run();
        }
    }
}
