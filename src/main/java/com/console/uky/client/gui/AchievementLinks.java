package com.console.uky.client.gui;

import com.console.uky.client.gui.screen.GuiProgressScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.stats.Achievement;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatList;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

/**
 * The chat line an achievement produces, cut down and made to lead somewhere.
 *
 * The server writes a whole sentence — "Relsev has just earned the achievement
 * [Taking Inventory]" — which says one useful word and eleven others, in a place
 * where a dozen other things are competing for the same three lines. This rewrites it
 * client-side to the achievement itself, keeping the player's name only when it was
 * somebody else's, and turns the whole line into a link: clicking it opens the
 * achievements list scrolled to that entry with it picked out.
 *
 * <p>The link is a command rather than a click handler, and that is on purpose. Chat
 * clicks are dispatched inside {@code GuiChat}, so a handler would only work while
 * <em>our</em> chat screen is the one open — and a pack is free to install a chat mod
 * that replaces it. A client-side command is dispatched from
 * {@code EntityClientPlayerMP.sendChatMessage}, underneath every chat screen there
 * is, so the link keeps working whatever is drawing the box it was clicked in. It
 * never reaches the server either: Forge's client command handler consumes it.
 *
 * @see AchievementCommand the command that receives the click
 * @see AchievementToast the popup the same event produces
 */
public final class AchievementLinks {

    /** The key the server's own announcement carries; anything else is left alone. */
    private static final String ANNOUNCEMENT = "chat.type.achievement";

    /**
     * The marker in front of the line.
     *
     * Outside ASCII, which the game draws from its unicode pages rather than from
     * {@code ascii.png} — fine in chat, which is only ever drawn once the resources
     * are up. (The loading screen is the one place where that is not true; see
     * {@code UiConfig.splashSafe}.)
     */
    private static final String MARK = "★";

    private AchievementLinks() {
    }

    /**
     * Rewrites the server's announcement, or returns null if this was not one.
     *
     * Null rather than the original on purpose: the caller is an event handler, and
     * "not ours" and "ours, unchanged" want to be told apart at the call site.
     */
    public static IChatComponent compact(IChatComponent message) {
        if (!(message instanceof ChatComponentTranslation)) {
            return null;
        }
        ChatComponentTranslation translation = (ChatComponentTranslation) message;
        if (!ANNOUNCEMENT.equals(translation.getKey())) {
            return null;
        }
        Object[] args = translation.getFormatArgs();
        if (args == null || args.length < 2) {
            return null;
        }

        String statId = statIdOf(args[1]);
        Achievement achievement = achievementOf(statId);
        if (achievement == null) {
            // A pack can announce something that is not in the achievement list —
            // some quest mods borrow this message. Leave those exactly as they are
            // rather than shortening a line we cannot link anywhere.
            return null;
        }

        String earner = plain(args[0]);
        String name = plain(achievement.func_150951_e());
        boolean special = safeSpecial(achievement);

        StringBuilder text = new StringBuilder();
        text.append("§6").append(MARK).append(' ');
        if (!isSelf(earner)) {
            text.append("§f").append(earner).append(" §8· ");
        }
        text.append(special ? "§d" : "§f").append(name);

        ChatComponentText line = new ChatComponentText(text.toString());
        // Both events on the one component: chat hit-testing walks a line's parts and
        // returns the one under the pointer, so a line built out of several pieces
        // would only be clickable over some of itself.
        line.getChatStyle().setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ACHIEVEMENT,
                new ChatComponentText(statId)));
        line.getChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/" + AchievementCommand.NAME + " " + statId));
        return line;
    }

    /**
     * Handles a click on one of our lines, if that is what it was.
     *
     * Only used by our own chat screen, which gets to act before the click becomes a
     * command at all. Everything else goes the long way round, through the command.
     */
    public static boolean handleClick(IChatComponent component) {
        if (component == null) {
            return false;
        }
        ClickEvent click = component.getChatStyle().getChatClickEvent();
        if (click == null || click.getAction() != ClickEvent.Action.RUN_COMMAND) {
            return false;
        }
        String prefix = "/" + AchievementCommand.NAME + " ";
        String value = click.getValue();
        if (value == null || !value.startsWith(prefix)) {
            return false;
        }
        return open(value.substring(prefix.length()).trim());
    }

    /**
     * Opens the achievements list on the named achievement.
     *
     * @return whether it opened; false when the id is unknown or there is no player
     *         to read a stat file off, in which case the click does nothing at all
     *         rather than opening an empty list
     */
    public static boolean open(String statId) {
        Achievement achievement = achievementOf(statId);
        if (achievement == null) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return false;
        }
        GuiProgressScreen screen = new GuiProgressScreen(null,
                mc.thePlayer.getStatFileWriter(), GuiProgressScreen.achievementsTab());
        screen.focusOn(achievement);
        mc.displayGuiScreen(screen);
        return true;
    }

    // ------------------------------------------------------------------ digging --

    /**
     * The achievement's id, read off the hover event the server already put there.
     *
     * That hover is how vanilla's own tooltip finds the achievement behind the
     * bracketed name, so it is present on every announcement worth rewriting — and
     * reading it beats matching the translated name against the achievement list,
     * which would be wrong in any language where two of them translate alike.
     */
    private static String statIdOf(Object arg) {
        if (!(arg instanceof IChatComponent)) {
            return null;
        }
        HoverEvent hover = ((IChatComponent) arg).getChatStyle().getChatHoverEvent();
        if (hover == null || hover.getAction() != HoverEvent.Action.SHOW_ACHIEVEMENT) {
            return null;
        }
        IChatComponent value = hover.getValue();
        return value == null ? null : value.getUnformattedText();
    }

    private static Achievement achievementOf(String statId) {
        if (statId == null || statId.isEmpty()) {
            return null;
        }
        StatBase stat = StatList.func_151177_a(statId);
        return stat instanceof Achievement ? (Achievement) stat : null;
    }

    private static boolean isSelf(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || name == null) {
            return false;
        }
        return name.equals(mc.thePlayer.getCommandSenderName());
    }

    private static boolean safeSpecial(Achievement achievement) {
        try {
            return achievement.getSpecial();
        } catch (Throwable t) {
            return false;
        }
    }

    private static String plain(Object component) {
        if (component instanceof IChatComponent) {
            try {
                return ((IChatComponent) component).getUnformattedText();
            } catch (Throwable t) {
                return "";
            }
        }
        return component == null ? "" : component.toString();
    }
}
