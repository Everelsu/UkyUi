package com.console.uky.handler;

import com.console.uky.client.gui.ChatOverlay;
import com.console.uky.client.gui.PlayerListOverlay;
import com.console.uky.client.gui.screen.GuiConnectingScreen;
import com.console.uky.client.gui.screen.GuiUkyChat;
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
import com.console.uky.client.gui.screen.GuiWorkingScreen;
import com.console.uky.client.gui.screen.GuiWorldsScreen;
import com.console.uky.client.mods.QuestBookTheme;
import com.console.uky.client.mods.QuestBookTransition;
import com.console.uky.client.sound.UkyMusicTicker;
import com.console.uky.client.sound.UkySounds;
import com.console.uky.config.UiConfig;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiWorldEdit;
import net.minecraft.client.gui.GuiWorldSelection;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenWorking;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.opengl.GL11;

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

    /**
     * Replaces the Tab player list with ours.
     *
     * The list is not a screen, so it cannot be swapped like the others: vanilla draws
     * it inline in {@code GuiIngame.renderGameOverlay}. Forge announces it as its own
     * overlay element first, though, and cancelling that is enough to stop vanilla
     * drawing it — leaving the space to draw in without touching {@code GuiIngame}.
     */
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Pre event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.PLAYER_LIST) {
            return;
        }
        if (!UiConfig.replacePlayerList) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (!PlayerListOverlay.shouldDraw(mc)) {
            return;
        }
        event.setCanceled(true);
        PlayerListOverlay.draw(mc, event.getResolution());
    }

    /**
     * Replaces the chat's own drawing with ours.
     *
     * Its own event, not the {@code Pre} above, because this one carries where the
     * chat is about to be drawn — Forge posts it <em>before</em> applying that
     * translation, so a listener that cancels the draw has to apply it itself.
     */
    @SubscribeEvent
    public void onRenderChat(RenderGameOverlayEvent.Chat event) {
        if (!UiConfig.redesignChat) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(event.getPosX(), event.getPosY(), 0.0F);
        boolean drawn;
        try {
            drawn = ChatOverlay.draw(Minecraft.getMinecraft(), event.getPosY());
        } finally {
            GlStateManager.popMatrix();
        }
        if (drawn) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        // The menu track follows the menus: it survives moving between title,
        // world select and options, and stops the moment a world is in play.
        if (Minecraft.getMinecraft().world != null) {
            UkySounds.stopMusic();
        }

        // Backstop for the ticker swap. It is a no-op once ours is in place, and it
        // costs one field read on a screen change rather than every tick.
        UkyMusicTicker.ensureInstalled(Minecraft.getMinecraft());

        keepLatinCrisp();
        UkyFontRenderer.install(Minecraft.getMinecraft());

        // Before the null check below, because closing the quest book to no screen at
        // all is exactly the change this needs to hear about. The screen being replaced
        // is still the current one at this point; the event carries the new one.
        QuestBookTransition.screenChanged(Minecraft.getMinecraft().currentScreen, event.getGui());

        if (event.getGui() == null) {
            return;
        }
        Class<?> type = event.getGui().getClass();

        if (UiConfig.replaceMainMenu && type == GuiMainMenu.class) {
            event.setGui(new GuiTitleScreen());
            return;
        }

        // Only when the redesign is on: with it off the chat screen is left entirely
        // alone, which is one fewer vanilla screen this mod is standing in front of.
        if (UiConfig.redesignChat && type == GuiChat.class) {
            event.setGui(new GuiUkyChat(defaultChatText(event.getGui())));
            return;
        }

        if (UiConfig.replacePauseMenu && type == GuiIngameMenu.class) {
            event.setGui(new GuiPauseScreen());
            return;
        }

        if (UiConfig.replaceDeathScreen && type == GuiGameOver.class) {
            event.setGui(new GuiDeathScreen());
            return;
        }

        if (UiConfig.replaceWorldList && type == GuiWorldSelection.class) {
            // Another mod (or a vanilla path we did not route) opened the stock
            // world list; swap it for the tiles.
            event.setGui(new GuiWorldsScreen(parentOf(event.getGui())));
            return;
        }

        // 1.7.10 had a rename-only screen; 1.12.2 folded renaming into "Edit World"
        // alongside "Reset icon" and "Open folder". Those two are not reachable
        // through the prompt this puts up — the mod's own world list is the intended
        // way in and offers neither. Take this branch out to leave the stock screen.
        if (UiConfig.replaceWorldList && type == GuiWorldEdit.class) {
            String folder = folderOf(event.getGui());
            if (folder != null) {
                event.setGui(new GuiWorldPromptScreen(parentOf(event.getGui()),
                        GuiWorldPromptScreen.Mode.RENAME, folder));
            }
            return;
        }

        if (UiConfig.replaceWorldList && type == GuiCreateWorld.class) {
            // A world type's customiser hands control back by displaying the
            // GuiCreateWorld it was given; that instance carries the edited
            // generator options, so it is restored rather than replaced.
            GuiCreateWorldScreen resumed = GuiCreateWorldScreen.resume(event.getGui());
            event.setGui(resumed != null
                    ? resumed
                    : new GuiCreateWorldScreen(parentOf(event.getGui())));
            return;
        }

        if (type == GuiConnecting.class) {
            // Wrapped, not replaced: the vanilla screen owns the handshake.
            event.setGui(new GuiConnectingScreen(event.getGui()));
            return;
        }

        if (type == GuiDisconnected.class) {
            // Everything this screen needs is locatable by field type — one String,
            // one ITextComponent, one GuiScreen — so no MCP names are involved and it
            // works the same in a production pack.
            String heading = firstOfType(event.getGui(), String.class);
            ITextComponent reason = firstOfType(event.getGui(), ITextComponent.class);
            if (reason != null) {
                event.setGui(new GuiDisconnectedScreen(parentOf(event.getGui()),
                        heading == null ? "" : heading, reason));
            }
            return;
        }

        if (UiConfig.replaceLoadingScreen && type == GuiDownloadTerrain.class) {
            // Wraps rather than replaces: the vanilla screen is what notices the
            // world is ready and hands control back.
            event.setGui(new GuiWorldLoadingScreen(event.getGui()));
            return;
        }

        // The phase before that one: the integrated server starting up. Left alone it
        // draws Mojang's tiled dirt, which flashed through every world load in the
        // gaps between UkyLoadingScreen's repaints. Ours is a subclass, so the exact
        // class test above cannot match it and this cannot recurse.
        if (UiConfig.replaceLoadingScreen && type == GuiScreenWorking.class) {
            event.setGui(new GuiWorkingScreen());
            return;
        }

        // BetterQuesting's own screens, left exactly as they are and drawn in our
        // palette. See QuestBookTheme for why this is the moment it is done.
        if (type.getName().startsWith(QuestBookTheme.SCREEN_PREFIX)) {
            QuestBookTheme.ensureApplied();
            return;
        }

        // FML's mid-load question. Restyled rather than replaced: it stays a
        // GuiNotification because that type is what FML's own draw-and-input path
        // looks for while the game loop is parked. See GuiStartupQueryScreen.
        if (type.getName().startsWith("net.minecraftforge.fml.client.Gui")) {
            GuiScreen restyled = GuiStartupQueryScreen.wrap(event.getGui());
            if (restyled != null) {
                event.setGui(restyled);
            }
            return;
        }

        if (UiConfig.replaceOptions && type == GuiOptions.class) {
            event.setGui(new GuiSettingsScreen(parentOf(event.getGui()),
                    Minecraft.getMinecraft().gameSettings));
        }
    }

    /**
     * The text a chat screen was opened pre-filled with, or "".
     *
     * Pressing the command key opens the chat with a "/" already typed, and that is
     * carried in a private field — so replacing the screen without reading it turned
     * the command key into a second chat key. Located by value rather than by name:
     * the screen has two String fields, both empty at the moment it is handed to us,
     * except the one that was constructed with something in it.
     */
    private static String defaultChatText(GuiScreen screen) {
        for (Field candidate : screen.getClass().getDeclaredFields()) {
            if (candidate.getType() != String.class
                    || java.lang.reflect.Modifier.isStatic(candidate.getModifiers())) {
                continue;
            }
            candidate.setAccessible(true);
            try {
                Object value = candidate.get(screen);
                if (value instanceof String && !((String) value).isEmpty()) {
                    return (String) value;
                }
            } catch (IllegalAccessException e) {
                return "";
            }
        }
        return "";
    }

    /**
     * Reads the save folder out of a {@link GuiWorldEdit}.
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
