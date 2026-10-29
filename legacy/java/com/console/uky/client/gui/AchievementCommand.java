package com.console.uky.client.gui;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraftforge.client.ClientCommandHandler;

/**
 * The other end of an achievement link in chat.
 *
 * Registered with Forge's client command handler, which means it is dispatched from
 * {@code EntityClientPlayerMP.sendChatMessage} before anything is sent anywhere — so
 * a click on one of our chat lines opens the achievements list and the server never
 * hears about it. That is also what makes the link independent of which chat screen
 * is installed: every one of them ends up sending the command the same way.
 *
 * <p>It is a real command, so it can also be typed. That is a side effect rather than
 * the feature, but it costs nothing and it is how anyone would test the thing.
 *
 * @see AchievementLinks which writes the link this receives
 */
public final class AchievementCommand extends CommandBase {

    /** Kept short and unmistakable: it shows up in tab completion under "/". */
    public static final String NAME = "ukyach";

    private static boolean registered;

    /** Registers once; safe to call again. */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientCommandHandler.instance.registerCommand(new AchievementCommand());
    }

    @Override
    public String getCommandName() {
        return NAME;
    }

    /** The word that shows the popup instead of opening the list. See below. */
    private static final String PREVIEW = "demo";

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/" + NAME + " <achievement id|" + PREVIEW + ">";
    }

    /**
     * Anyone, always.
     *
     * {@code CommandBase} defaults to permission level 4 — the level a server
     * operator has — which is the right default for a command that changes the world
     * and the wrong one for a command that opens a screen on the machine it was typed
     * on. Left alone, clicking the link on a server you do not own answered with
     * "You do not have permission to use this command".
     */
    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            return;
        }
        // A way to see the popup without earning something.
        //
        // It is worth a line of code for one reason: the popup lasts four seconds and
        // happens when the player is doing something else, so the only way to judge
        // the animation — or to see a change to it — is to be able to ask for one.
        // Taking a second argument would be a way to preview any given achievement,
        // and is deliberately not offered: /ukyach <id> already exists and would then
        // mean two different things depending on a word.
        if (PREVIEW.equalsIgnoreCase(args[0])) {
            AchievementToast.show(net.minecraft.stats.AchievementList.openInventory);
            return;
        }
        // Silent when the id is unknown. It can only be unknown if a pack removed the
        // achievement between the line being written and it being clicked, and a chat
        // error about an internal id would explain nothing to whoever clicked it.
        AchievementLinks.open(args[0]);
    }
}
