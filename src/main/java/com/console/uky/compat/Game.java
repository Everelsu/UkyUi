package com.console.uky.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The few whole-game actions the screens take, in 1.21's terms. */
public final class Game {

    private Game() {
    }

    /**
     * Leaves the world or server, as the pause menu's own button does, then shows
     * {@code next}.
     *
     * Saving a local world is the part that takes time; vanilla puts a "Saving world"
     * screen up for it, and so does this.
     */
    public static void disconnect(Screen next) {
        Minecraft mc = Minecraft.getInstance();
        boolean local = mc.isLocalServer();
        if (mc.level != null) {
            mc.level.disconnect();
        }
        if (local) {
            mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
        } else {
            mc.disconnect();
        }
        mc.setScreen(next);
    }

    /** Closes whatever screen is open and hands the mouse back to the game. */
    public static void resume() {
        Minecraft.getInstance().setScreen(null);
    }

    public static void quit() {
        Minecraft.getInstance().stop();
    }
}
