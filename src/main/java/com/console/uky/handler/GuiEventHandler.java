package com.console.uky.handler;

import com.console.uky.client.death.DeathTracker;
import com.console.uky.client.gui.screen.GuiConnectingScreen;
import com.console.uky.client.gui.screen.GuiCreateWorldScreen;
import com.console.uky.client.gui.screen.GuiDeathScreen;
import com.console.uky.client.gui.screen.GuiDisconnectedScreen;
import com.console.uky.client.gui.screen.GuiPauseScreen;
import com.console.uky.client.gui.screen.GuiServersScreen;
import com.console.uky.client.gui.screen.GuiShareScreen;
import com.console.uky.client.gui.screen.GuiSettingsScreen;
import com.console.uky.client.gui.screen.GuiTitleScreen;
import com.console.uky.client.gui.screen.GuiWorldLoadingScreen;
import com.console.uky.client.gui.screen.GuiWorldsScreen;
import com.console.uky.client.sound.UkySounds;
import com.console.uky.config.UiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Swaps vanilla screens for this mod's own as they open.
 *
 * <p>By exact class, never by {@code instanceof}: another mod's subclass of a vanilla
 * screen is that mod's screen, with whatever it added, and replacing it would take
 * those additions away.
 */
public class GuiEventHandler {

    /** The parent-screen field of each vanilla screen type, found once by type. */
    private static final Map<Class<?>, Field> PARENT_FIELDS = new HashMap<Class<?>, Field>();

    @SubscribeEvent
    public void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        // The menu track belongs to the menus. A screen opening over a world — the
        // pause menu, an inventory — must not carry it into the game.
        if (mc.level != null) {
            UkySounds.stopMusic();
        }

        Screen gui = event.getNewScreen();
        if (gui == null) {
            return;
        }
        Class<?> type = gui.getClass();

        if (UiConfig.replaceMainMenu && type == TitleScreen.class) {
            event.setNewScreen(new GuiTitleScreen());
            return;
        }

        // F3+Esc opens a pause screen with no menu on it, on purpose; that one stays.
        if (UiConfig.replacePauseMenu && type == PauseScreen.class && ((PauseScreen) gui).showsPauseMenu()) {
            event.setNewScreen(new GuiPauseScreen());
            return;
        }

        if (UiConfig.replaceDeathScreen && type == DeathScreen.class) {
            Component cause = firstOfType(gui, Component.class);
            if (cause != null && cause.getContents() instanceof TranslatableContents) {
                DeathTracker.told(((TranslatableContents) cause.getContents()).getKey());
            }
            event.setNewScreen(new GuiDeathScreen());
            return;
        }

        if (UiConfig.replaceWorldList && type == SelectWorldScreen.class) {
            event.setNewScreen(new GuiWorldsScreen(parentOf(gui)));
            return;
        }

        if (UiConfig.replaceWorldList && type == CreateWorldScreen.class) {
            Screen ours = GuiCreateWorldScreen.wrap((CreateWorldScreen) gui, parentOf(gui));
            if (ours != null) {
                event.setNewScreen(ours);
            }
            return;
        }

        // Leaving a server comes back to the list it was joined from.
        if (type == JoinMultiplayerScreen.class) {
            event.setNewScreen(new GuiServersScreen(parentOf(gui)));
            return;
        }

        if (type == ShareToLanScreen.class) {
            event.setNewScreen(new GuiShareScreen(parentOf(gui)));
            return;
        }

        if (type == ConnectScreen.class) {
            event.setNewScreen(new GuiConnectingScreen(gui));
            return;
        }

        if (type == DisconnectedScreen.class) {
            Component reason = firstOfType(gui, Component.class);
            if (reason != null) {
                event.setNewScreen(new GuiDisconnectedScreen(parentOf(gui),
                        gui.getTitle().getString(), reason));
            }
            return;
        }

        if (UiConfig.replaceLoadingScreen
                && (type == ReceivingLevelScreen.class || type == LevelLoadingScreen.class
                        || type == GenericMessageScreen.class)) {
            event.setNewScreen(new GuiWorldLoadingScreen(gui));
            return;
        }

        if (UiConfig.replaceOptions && type == OptionsScreen.class) {
            event.setNewScreen(new GuiSettingsScreen(parentOf(gui), mc.options));
        }
    }

    /**
     * The first field of a type on a screen; how the reason is pulled off vanilla's
     * disconnect screen, which keeps it private.
     */
    @SuppressWarnings("unchecked")
    private static <T> T firstOfType(Screen screen, Class<T> wanted) {
        for (Field candidate : screen.getClass().getDeclaredFields()) {
            if (wanted.isAssignableFrom(candidate.getType())
                    && !java.lang.reflect.Modifier.isStatic(candidate.getModifiers())) {
                candidate.setAccessible(true);
                try {
                    return (T) candidate.get(screen);
                } catch (IllegalAccessException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * The screen {@code screen} returns to, read off its first field of screen type.
     *
     * Every vanilla screen this replaces keeps exactly one such field, and it is the
     * parent; by type rather than by name keeps it independent of what it is called.
     */
    private static Screen parentOf(Screen screen) {
        Class<?> type = screen.getClass();
        Field field = PARENT_FIELDS.get(type);
        if (field == null) {
            for (Field candidate : type.getDeclaredFields()) {
                if (Screen.class.isAssignableFrom(candidate.getType())
                        && !java.lang.reflect.Modifier.isStatic(candidate.getModifiers())) {
                    candidate.setAccessible(true);
                    field = candidate;
                    break;
                }
            }
            if (field == null) {
                return null;
            }
            PARENT_FIELDS.put(type, field);
        }
        try {
            return (Screen) field.get(screen);
        } catch (IllegalAccessException e) {
            return null;
        }
    }
}
