package com.console.uky.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 1.7.10's answer to a yes/no screen: one callback with the answer and an id, where
 * 1.21 hands each screen a lambda of its own. The helpers here build the 1.21 screens
 * around a callback of the old shape.
 */
public interface GuiYesNoCallback {

    void confirmClicked(boolean confirmed, int id);

    /** Vanilla's "open this link?" screen. Opening the link is still the caller's job. */
    static Screen confirmLink(GuiYesNoCallback callback, String url, int id) {
        return new ConfirmLinkScreen(answer -> callback.confirmClicked(answer, id), url, false);
    }

    static Screen confirm(GuiYesNoCallback callback, String title, String message,
                          String yes, String no, int id) {
        return new ConfirmScreen(answer -> callback.confirmClicked(answer, id),
                Component.literal(title), Component.literal(message),
                Component.literal(yes), Component.literal(no));
    }

    /** Opens {@code url} in the system browser, which is what the link screen's yes means. */
    static void open(String url) {
        net.minecraft.Util.getPlatform().openUri(url);
    }

    static void show(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }
}
