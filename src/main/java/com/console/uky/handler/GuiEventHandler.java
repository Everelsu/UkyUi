package com.console.uky.handler;

import com.console.uky.client.gui.screen.GuiConnectingScreen;
import com.console.uky.client.render.UkyFontRenderer;
import com.console.uky.client.gui.screen.GuiCreateWorldScreen;
import com.console.uky.client.gui.screen.GuiDeathScreen;
import com.console.uky.client.gui.screen.GuiDisconnectedScreen;
import com.console.uky.client.gui.screen.GuiTitleScreen;
import com.console.uky.client.gui.screen.GuiWorldPromptScreen;
import com.console.uky.client.gui.screen.GuiSettingsScreen;
import com.console.uky.client.gui.screen.GuiStartupQueryScreen;
import com.console.uky.client.gui.screen.GuiPauseScreen;
import com.console.uky.client.gui.screen.GuiWorldLoadingScreen;
import com.console.uky.client.gui.screen.GuiWorldsScreen;
import com.console.uky.client.sound.UkyMusicTicker;
import com.console.uky.client.sound.UkySounds;
import com.console.uky.config.UiConfig;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiRenameWorld;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.GuiOpenEvent;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Swaps vanilla screens for their UKY equivalents as they open.
 *
 * Exact class comparison (not {@code instanceof}) on purpose: another mod may
 * subclass these screens to add its own controls, and silently throwing that
 * away would be worse than showing the vanilla look.
 */
public class GuiEventHandler {

    /** Cached parent-screen field per screen class; see {@link #parentOf}. */
    private static final Map<Class<?>, Field> PARENT_FIELDS = new HashMap<Class<?>, Field>();

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        // The menu track follows the menus: it survives moving between title,
        // world select and options, and stops the moment a world is in play.
        if (Minecraft.getMinecraft().theWorld != null) {
            UkySounds.stopMusic();
        }

        // Backstop for the ticker swap. It is a no-op once ours is in place, and it
        // costs one field read on a screen change rather than every tick.
        UkyMusicTicker.ensureInstalled(Minecraft.getMinecraft());

        if (event.gui == null) {
            return;
        }
        Class<?> type = event.gui.getClass();

        // Nothing touches the font while the game is still loading.
        //
        // These two rebuild and reconfigure the game's font renderer, and they used to
        // run at the top of this method — that is, on every screen FML puts up during
        // mod loading, when the resource system is half-built and the loading screen
        // still owns the GL context. Building a font renderer there is how the splash
        // font took Angelica's unicode pages down with it, and there is no reason to
        // do it that early: nothing before the title screen is drawn by this mod.
        //
        // FML's own screens are the ones that appear during loading, and they are
        // already recognised further down by their package.
        if (!isLoadingScreen(type)) {
            keepLatinCrisp();
            UkyFontRenderer.install(Minecraft.getMinecraft());
        }

        if (UiConfig.replaceMainMenu && type == GuiMainMenu.class) {
            event.gui = new GuiTitleScreen();
            return;
        }

        if (UiConfig.replacePauseMenu && type == GuiIngameMenu.class) {
            event.gui = new GuiPauseScreen();
            return;
        }

        if (UiConfig.replaceDeathScreen && type == GuiGameOver.class) {
            event.gui = new GuiDeathScreen();
            return;
        }

        if (UiConfig.replaceWorldList && type == GuiSelectWorld.class) {
            // Another mod (or a vanilla path we did not route) opened the stock
            // world list; swap it for the tiles.
            event.gui = new GuiWorldsScreen(parentOf(event.gui));
            return;
        }

        if (UiConfig.replaceWorldList && type == GuiRenameWorld.class) {
            String folder = folderOf(event.gui);
            if (folder != null) {
                event.gui = new GuiWorldPromptScreen(parentOf(event.gui),
                        GuiWorldPromptScreen.Mode.RENAME, folder);
            }
            return;
        }

        if (UiConfig.replaceWorldList && type == GuiCreateWorld.class) {
            // A world type's customiser hands control back by displaying the
            // GuiCreateWorld it was given; that instance carries the edited
            // generator options, so it is restored rather than replaced.
            GuiCreateWorldScreen resumed = GuiCreateWorldScreen.resume(event.gui);
            event.gui = resumed != null
                    ? resumed
                    : new GuiCreateWorldScreen(parentOf(event.gui));
            return;
        }

        if (type == GuiConnecting.class) {
            // Wrapped, not replaced: the vanilla screen owns the handshake.
            event.gui = new GuiConnectingScreen(event.gui);
            return;
        }

        if (type == GuiDisconnected.class) {
            // Everything this screen needs is locatable by field type — one String,
            // one IChatComponent, one GuiScreen — so no MCP names are involved and it
            // works the same in a production pack.
            String heading = firstOfType(event.gui, String.class);
            IChatComponent reason = firstOfType(event.gui, IChatComponent.class);
            if (reason != null) {
                event.gui = new GuiDisconnectedScreen(parentOf(event.gui),
                        heading == null ? "" : heading, reason);
            }
            return;
        }

        if (UiConfig.replaceLoadingScreen && type == GuiDownloadTerrain.class) {
            // Wraps rather than replaces: the vanilla screen is what notices the
            // world is ready and hands control back.
            event.gui = new GuiWorldLoadingScreen(event.gui);
            return;
        }

        // FML's mid-load question. Restyled rather than replaced: it stays a
        // GuiNotification because that type is what FML's own draw-and-input path
        // looks for while the game loop is parked. See GuiStartupQueryScreen.
        if (isLoadingScreen(type)) {
            GuiScreen restyled = GuiStartupQueryScreen.wrap(event.gui);
            if (restyled != null) {
                event.gui = restyled;
            }
            return;
        }

        if (UiConfig.replaceOptions && type == GuiOptions.class) {
            event.gui = new GuiSettingsScreen(parentOf(event.gui),
                    Minecraft.getMinecraft().gameSettings);
        }
    }

    /**
     * Whether this is one of FML's own screens, which only appear during loading.
     *
     * By package rather than by class: FML puts up several of these — the notification,
     * the confirmation, the access-denied — and what they have in common is when they
     * appear, which is the part that matters here.
     */
    private static boolean isLoadingScreen(Class<?> type) {
        return type.getName().startsWith("cpw.mods.fml.client.Gui");
    }

    /**
     * Reads the save folder out of a {@link GuiRenameWorld}.
     *
     * Located by type like {@link #parentOf}: the screen holds exactly one String
     * field, the folder it was constructed with. Null if that ever stops being
     * true, in which case the vanilla screen is left in place rather than opening
     * ours pointed at nothing.
     */
    private static String folderOf(GuiScreen screen) {
        for (Field candidate : screen.getClass().getDeclaredFields()) {
            if (candidate.getType() == String.class
                    && !java.lang.reflect.Modifier.isStatic(candidate.getModifiers())) {
                candidate.setAccessible(true);
                try {
                    return (String) candidate.get(screen);
                } catch (IllegalAccessException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Keeps Latin text rendering from ascii.png, whatever the language is set to.
     *
     * 1.7.10 decides this per font renderer, not per character: a locale flagged as
     * unicode sets {@code unicodeFlag}, and from then on <em>every</em> character —
     * Latin included — is drawn from the unicode pages instead of ascii.png. Those
     * pages are a coarser, monospaced-ish fallback, which is why English text looked
     * noticeably worse with the game in Russian than the identical text with the game
     * in English.
     *
     * <p>Cyrillic is unaffected either way: it is not in the character set ascii.png
     * covers, so it goes through the unicode pages regardless of this flag. Turning
     * the flag off therefore costs nothing and fixes the Latin.
     *
     * <p>An explicit "Force Unicode" in the options still wins — that switch exists
     * for people who need it, and silently ignoring it would be its own bug.
     */
    private static void keepLatinCrisp() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.fontRenderer == null || mc.gameSettings == null) {
            return;
        }
        // Hands off when something else lays text out.
        //
        // This overrides a *global* setting, every time any screen opens, and it does
        // it on an assumption about what the flag means: vanilla reads it per
        // character, so Latin comes from ascii.png and Cyrillic still falls through to
        // the unicode pages. Angelica reads it as a choice of font provider, and with
        // the flag forced off its Cyrillic has nowhere to come from — ascii.png does
        // not contain a single Cyrillic glyph. The result is Russian rendered from the
        // wrong set, everywhere, including screens this mod does not own.
        //
        // A cosmetic improvement to vanilla's text is not worth breaking somebody
        // else's, so it applies only where the assumption it rests on is true.
        if (UkyFontRenderer.rendererOwnsText()) {
            return;
        }
        boolean wanted = mc.gameSettings.forceUnicodeFont;
        if (mc.fontRenderer.getUnicodeFlag() != wanted) {
            mc.fontRenderer.setUnicodeFlag(wanted);
        }
    }

    /**
     * First non-static field of the given type, or null.
     *
     * Same reasoning as {@link #parentOf}: looked up by type because MCP field names
     * only exist in a development environment. Only safe where the screen holds
     * exactly one field of that type, which is checked per use site.
     */
    @SuppressWarnings("unchecked")
    private static <T> T firstOfType(GuiScreen screen, Class<T> wanted) {
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
     * Reads a vanilla screen's parent so "Done" returns where the player came from.
     *
     * Looked up by field <em>type</em> rather than name: the MCP name only exists in
     * a dev environment, while the obfuscated production name differs. The screens
     * this is used on each hold exactly one GuiScreen field, so it is unambiguous.
     */
    private static GuiScreen parentOf(GuiScreen screen) {
        Class<?> type = screen.getClass();
        Field field = PARENT_FIELDS.get(type);
        if (field == null) {
            for (Field candidate : type.getDeclaredFields()) {
                if (GuiScreen.class.isAssignableFrom(candidate.getType())) {
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
            return (GuiScreen) field.get(screen);
        } catch (IllegalAccessException e) {
            // Falling back to null just means Done closes to the main menu / game.
            return null;
        }
    }
}
