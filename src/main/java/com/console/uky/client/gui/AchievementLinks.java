package com.console.uky.client.gui;

import com.console.uky.client.gui.screen.GuiProgressScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.FrameType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.ITextComponent;

/**
 * The chat line an advancement produces, cut down and made to lead somewhere.
 *
 * The server writes a whole sentence — "Relsev has just made the advancement
 * [Taking Inventory]" — which says one useful word and eleven others, in a place
 * where a dozen other things are competing for the same three lines. This rewrites it
 * client-side to the achievement itself, keeping the player's name only when it was
 * somebody else's, and turns the whole line into a link: clicking it opens the
 * advancements list with that entry picked out.
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

    /**
     * The keys the server's own announcement carries; anything else is left alone.
     *
     * Three of them, because 1.12 announces a task, a goal and a challenge with three
     * different messages — the same sentence with a different word for what was made.
     */
    private static final String[] ANNOUNCEMENTS = {
        "chat.type.advancement.task",
        "chat.type.advancement.goal",
        "chat.type.advancement.challenge",
    };

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
    public static ITextComponent compact(ITextComponent message) {
        if (!(message instanceof TextComponentTranslation)) {
            return null;
        }
        TextComponentTranslation translation = (TextComponentTranslation) message;
        if (!isAnnouncement(translation.getKey())) {
            return null;
        }
        Object[] args = translation.getFormatArgs();
        if (args == null || args.length < 2) {
            return null;
        }

        Advancement advancement = announced(args[1]);
        if (advancement == null) {
            // A pack can announce something that is not in the advancement list —
            // some quest mods borrow this message. Leave those exactly as they are
            // rather than shortening a line we cannot link anywhere.
            return null;
        }

        String earner = plain(args[0]);
        String name = titleOf(advancement);
        String statId = String.valueOf(advancement.getId());
        boolean special = isChallenge(advancement);

        StringBuilder text = new StringBuilder();
        text.append("§6").append(MARK).append(' ');
        if (!isSelf(earner)) {
            text.append("§f").append(earner).append(" §8· ");
        }
        text.append(special ? "§d" : "§f").append(name);

        TextComponentString line = new TextComponentString(text.toString());
        // Both events on the one component: chat hit-testing walks a line's parts and
        // returns the one under the pointer, so a line built out of several pieces
        // would only be clickable over some of itself.
        // The hover the announcement already carried — the title and the description,
        // built by the game itself. 1.12 has no hover kind for "this is an advancement"
        // the way 1.7.10 had one for an achievement, so there is nothing better to put
        // here and nothing worth building by hand.
        HoverEvent hover = hoverOf(args[1]);
        if (hover != null) {
            line.getStyle().setHoverEvent(hover);
        }
        line.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/" + AchievementCommand.NAME + " " + statId));
        return line;
    }

    /**
     * Handles a click on one of our lines, if that is what it was.
     *
     * Only used by our own chat screen, which gets to act before the click becomes a
     * command at all. Everything else goes the long way round, through the command.
     */
    public static boolean handleClick(ITextComponent component) {
        if (component == null) {
            return false;
        }
        ClickEvent click = component.getStyle().getClickEvent();
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
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            return false;
        }
        Advancement advancement = byId(statId);
        if (advancement == null) {
            return false;
        }
        GuiProgressScreen screen = new GuiProgressScreen(null,
                mc.player.getStatFileWriter(), GuiProgressScreen.advancementsTab());
        screen.focusOn(advancement);
        mc.displayGuiScreen(screen);
        return true;
    }

    // ------------------------------------------------------------------ digging --

    private static boolean isAnnouncement(String key) {
        for (int i = 0; i < ANNOUNCEMENTS.length; i++) {
            if (ANNOUNCEMENTS[i].equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The advancement an announcement is about.
     *
     * <p>Matched by its title, which is the only thing the message carries. 1.7.10 named
     * the achievement in a hover event and reading that was exact; 1.12 builds its line
     * out of {@code Advancement.getDisplayText}, which is the title in brackets with a
     * hover holding the title and the description again. There is no id in it anywhere.
     *
     * <p>So the title is matched against the advancement list the client already has —
     * the same list the advancements screen is drawn from. Two advancements with the
     * same title are indistinguishable here and the first is taken; that is a link
     * landing on the wrong one of two identically named entries, which is the whole of
     * what this can get wrong.
     */
    private static Advancement announced(Object arg) {
        if (!(arg instanceof ITextComponent)) {
            return null;
        }
        String title = strip(((ITextComponent) arg).getUnformattedText());
        if (title.isEmpty()) {
            return null;
        }
        Iterable<Advancement> all = known();
        if (all == null) {
            return null;
        }
        for (Advancement candidate : all) {
            if (title.equals(titleOf(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    /** The brackets the game puts round the title, taken off again. */
    private static String strip(String text) {
        String trimmed = text == null ? "" : text.trim();
        int last = trimmed.length() - 1;
        if (last >= 1 && trimmed.charAt(0) == BRACKET_OPEN
                && trimmed.charAt(last) == BRACKET_CLOSE) {
            return trimmed.substring(1, last);
        }
        return trimmed;
    }

    private static final char BRACKET_OPEN = '[';
    private static final char BRACKET_CLOSE = ']';

    /**
     * The hover the game already built for this line.
     *
     * On the title rather than on the brackets around it, so the siblings are where it
     * is found; taken as it is because it says exactly what a player wants from a
     * hover — the name of the thing and what it was for.
     */
    private static HoverEvent hoverOf(Object arg) {
        if (!(arg instanceof ITextComponent)) {
            return null;
        }
        ITextComponent component = (ITextComponent) arg;
        HoverEvent own = component.getStyle().getHoverEvent();
        if (own != null) {
            return own;
        }
        for (ITextComponent part : component.getSiblings()) {
            HoverEvent hover = part.getStyle().getHoverEvent();
            if (hover != null) {
                return hover;
            }
        }
        return null;
    }

    /** Every advancement this client knows about, or null before one is connected. */
    private static Iterable<Advancement> known() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.player.connection == null) {
            return null;
        }
        try {
            return mc.player.connection.getAdvancementManager()
                    .getAdvancementList().getAdvancements();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Advancement byId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        Iterable<Advancement> all = known();
        if (all == null) {
            return null;
        }
        ResourceLocation wanted;
        try {
            wanted = new ResourceLocation(id);
        } catch (Throwable malformed) {
            return null;
        }
        for (Advancement candidate : all) {
            if (wanted.equals(candidate.getId())) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * An advancement's own title, or its id when it has no display of its own.
     *
     * Public because the screen a link opens uses it too: the title is what goes into
     * the search box there, and the two have to agree on what an advancement is called
     * or the row the link asked for would not be among the ones it narrows to.
     */
    public static String titleOf(Advancement advancement) {
        DisplayInfo display = advancement == null ? null : advancement.getDisplay();
        if (display == null) {
            return advancement == null ? "" : String.valueOf(advancement.getId());
        }
        try {
            return display.getTitle().getUnformattedText();
        } catch (Throwable t) {
            return String.valueOf(advancement.getId());
        }
    }

    /** Challenges are the hard ones, and are marked the way a special one used to be. */
    private static boolean isChallenge(Advancement advancement) {
        try {
            DisplayInfo display = advancement.getDisplay();
            return display != null && display.getFrame() == FrameType.CHALLENGE;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isSelf(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || name == null) {
            return false;
        }
        return name.equals(mc.player.getName());
    }

    private static String plain(Object component) {
        if (component instanceof ITextComponent) {
            try {
                return ((ITextComponent) component).getUnformattedText();
            } catch (Throwable t) {
                return "";
            }
        }
        return component == null ? "" : component.toString();
    }
}
