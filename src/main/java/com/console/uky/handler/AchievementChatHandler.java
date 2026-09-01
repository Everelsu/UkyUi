package com.console.uky.handler;

import com.console.uky.client.gui.AchievementLinks;
import com.console.uky.config.UiConfig;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;

/**
 * Rewrites the chat line an achievement produces, on its way in.
 *
 * Client-side and cosmetic: the server has already said what it said, the log still
 * carries the original sentence, and every other client on that server sees whatever
 * it sees. Nothing here is sent anywhere.
 *
 * @see AchievementLinks which decides what the line becomes
 */
public class AchievementChatHandler {

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        if (!UiConfig.achievementChatLink || event.message == null) {
            return;
        }
        IChatComponent compact = AchievementLinks.compact(event.message);
        if (compact != null) {
            event.message = compact;
        }
    }
}
