package com.console.uky;

import com.console.uky.client.gui.screen.GuiAudioScreen;
import com.console.uky.client.gui.screen.GuiSettingsScreen;
import com.console.uky.client.gui.screen.GuiTitleScreen;
import com.console.uky.client.gui.screen.GuiWorldsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * TEMPORARY: -Duky.debugShots=true walks through the menus, saving a screenshot of
 * each to run/neoforge/screenshots, for checking the port without anyone at the keys.
 */
public final class DebugShots {

    private int ticks;

    @SubscribeEvent
    public void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ++this.ticks;
        // A resource reload early on, shot every second, to catch the loading screen.
        if (this.ticks == 50) {
            mc.reloadResourcePacks();
        }
        if (this.ticks > 50 && this.ticks <= 170 && this.ticks % 20 == 0) {
            Screenshot.grab(mc.gameDirectory, "uky_reload_" + this.ticks + ".png",
                    mc.getMainRenderTarget(), msg -> { });
        }
        if (this.ticks % 60 != 0) {
            return;
        }
        int shot = this.ticks / 60;
        String name = "uky_" + shot + "_"
                + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()) + ".png";
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> { });

        Screen title = mc.screen instanceof GuiTitleScreen ? mc.screen : new GuiTitleScreen();
        switch (shot) {
            case 3: mc.setScreen(new GuiSettingsScreen(title, mc.options)); break;
            case 4: mc.setScreen(new GuiAudioScreen(title, mc.options)); break;
            case 5: mc.setScreen(new GuiWorldsScreen(new GuiTitleScreen())); break;
            case 6: com.console.uky.compat.Saves.create(mc.screen); break;
            case 8:
                if (mc.screen != null) {
                    mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
                }
                break;
            case 16:
                if (mc.level != null) {
                    mc.setScreen(new com.console.uky.client.gui.screen.GuiPauseScreen());
                }
                break;
            case 18:
                if (mc.level != null) {
                    mc.setScreen(new com.console.uky.client.gui.screen.GuiProgressScreen(null,
                            com.console.uky.client.gui.screen.GuiProgressScreen.achievementsTab()));
                }
                break;
            case 19:
                if (mc.level != null) {
                    mc.setScreen(new com.console.uky.client.gui.screen.GuiProgressScreen(null,
                            com.console.uky.client.gui.screen.GuiProgressScreen.statsTab()));
                }
                break;
            case 21:
                if (mc.level != null) {
                    com.console.uky.compat.Game.disconnect(new GuiTitleScreen());
                }
                break;
            default: break;
        }
    }
}
