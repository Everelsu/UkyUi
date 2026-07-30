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

        String folder = pendingFolder;
        Runnable action = pendingAction;
        pendingFolder = null;
        pendingAction = null;

        try {
            WorldPreviews.capture(folder);
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
