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
        if (this.ticks % 60 != 0) {
            return;
        }
        int shot = this.ticks / 60;
        String name = "uky_" + shot + "_"
                + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()) + ".png";
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> { });

        Screen title = mc.screen instanceof GuiTitleScreen ? mc.screen : new GuiTitleScreen();
        switch (shot) {
            case 2: com.console.uky.compat.Saves.create(title); break;
            case 4:
                if (mc.screen != null) {
                    mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
                }
                break;
            case 12:
            case 13:
                if (mc.level != null && mc.player != null) {
                    net.minecraft.advancements.AdvancementHolder root = mc.player.connection.getAdvancements()
                            .get(net.minecraft.resources.ResourceLocation.withDefaultNamespace("story/root"));
                    if (root != null) {
                        com.console.uky.client.gui.AchievementToast.show(root);
                    }
                }
                break;
            case 15:
                if (mc.level != null) {
                    mc.setScreen(new com.console.uky.client.gui.screen.GuiShareScreen(null));
                }
                break;
            default: break;
        }
    }
}
