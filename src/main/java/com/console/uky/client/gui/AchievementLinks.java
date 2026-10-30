package com.console.uky.client.gui;

import com.console.uky.client.gui.screen.GuiProgressScreen;
import com.console.uky.config.UiConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * The chat line an advancement produces, shortened and made a link.
 *
 * <p>Vanilla writes a whole sentence — "Player has made the advancement [Stone Age]".
 * This cuts it to the advancement itself, keeping the player's name only when somebody
 * else earned it. Hovering still shows what it was for; clicking opens the advancements
 * list scrolled to that entry with it picked out.
 *
 * <p>The click runs a client command, which never reaches the server: NeoForge answers
 * client commands before anything is sent.
 */
public final class AchievementLinks {

    private static final String ANNOUNCEMENT = "chat.type.advancement.";
    private static final String COMMAND = "ukyach";
    private static final String MARK = "★";

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent.System event) {
        if (!UiConfig.achievementChatLink) {
            return;
        }
        Component compact = compact(event.getMessage());
        if (compact != null) {
            event.setMessage(compact);
        }
    }

    @SubscribeEvent
    public void onCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(COMMAND)
                .then(Commands.argument("id", StringArgumentType.greedyString())
                        .executes(context -> {
                            String id = StringArgumentType.getString(context, "id");
                            // After the click is done with: the chat screen it came
                            // from is still being handled this frame.
                            Minecraft.getInstance().execute(() -> open(id));
                            return 1;
                        })));
    }

    /** The shortened line, or null when the message is not an advancement announcement. */
    static Component compact(Component message) {
        if (!(message.getContents() instanceof TranslatableContents)) {
            return null;
        }
        TranslatableContents translation = (TranslatableContents) message.getContents();
        Object[] args = translation.getArgs();
        if (!translation.getKey().startsWith(ANNOUNCEMENT) || args.length < 2) {
            return null;
        }
        String title = stripBrackets(text(args[1]));
        // Usually not yet known here: the server sends the announcement before the
        // advancement update that carries it. The link is resolved on click instead,
        // by id if it was known and by title otherwise.
        AdvancementNode node = find(title);
        DisplayInfo display = node == null ? null : node.advancement().display().orElse(null);
        String earner = text(args[0]);
        boolean challenge = display != null ? display.getType() == AdvancementType.CHALLENGE
                : translation.getKey().endsWith(".challenge");
        Component hover = display != null
                ? display.getTitle().copy().withStyle(display.getType().getChatColor())
                        .append("\n").append(display.getDescription())
                : hoverOf(args[1]);
        Component name = display != null ? display.getTitle() : Component.literal(title);
        String target = node != null ? node.holder().id().toString() : title;

        MutableComponent line = Component.literal(MARK + " ").withStyle(ChatFormatting.GOLD);
        if (!isSelf(earner)) {
            line.append(Component.literal(earner).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
        }
        line.append(name.copy().withStyle(style -> style
                .withColor(challenge ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.WHITE)
                .withHoverEvent(hover == null ? null : new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/" + COMMAND + " " + target))));
        return line;
    }

    /** Opens the advancements list on the one with this id or, failing that, title. */
    static boolean open(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        AdvancementNode byTitle = find(id);
        for (AdvancementNode node : mc.player.connection.getAdvancements().getTree().nodes()) {
            if (node.holder().id().toString().equals(id) || node == byTitle) {
                GuiProgressScreen screen = new GuiProgressScreen(null, GuiProgressScreen.achievementsTab());
                screen.focusOn(node);
                mc.setScreen(screen);
                return true;
            }
        }
        return false;
    }

    /**
     * The advancement a chat line names. The line carries its title, not its id, so it
     * is looked up by title among the ones this client knows — which is every one the
     * server has shown it, and an announced one always has been.
     */
    private static AdvancementNode find(String title) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        for (AdvancementNode node : mc.player.connection.getAdvancements().getTree().nodes()) {
            DisplayInfo display = node.advancement().display().orElse(null);
            if (display != null && display.getTitle().getString().equals(title)) {
                return node;
            }
        }
        return null;
    }

    private static String stripBrackets(String text) {
        return text.startsWith("[") && text.endsWith("]") ? text.substring(1, text.length() - 1) : text;
    }

    /** The hover text vanilla put on the advancement's name: its title and description. */
    private static Component hoverOf(Object argument) {
        if (!(argument instanceof Component)) {
            return null;
        }
        Component[] found = new Component[1];
        ((Component) argument).visit((style, text) -> {
            HoverEvent event = style.getHoverEvent();
            if (found[0] == null && event != null && event.getAction() == HoverEvent.Action.SHOW_TEXT) {
                found[0] = event.getValue(HoverEvent.Action.SHOW_TEXT);
            }
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return found[0];
    }

    private static boolean isSelf(String name) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && name.equals(mc.player.getName().getString());
    }

    private static String text(Object argument) {
        return argument instanceof Component ? ((Component) argument).getString() : String.valueOf(argument);
    }
}
