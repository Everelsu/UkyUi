package com.console.uky.handler;

import com.console.uky.UkyUI;
import com.console.uky.client.mods.QuestBookTheme;
import com.console.uky.client.mods.WailaPanel;
import com.console.uky.client.render.LinkIcons;
import com.console.uky.client.render.Theme;
import com.console.uky.config.UiConfig;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Applies config edits made through the in-game screen without a restart —
 * reopening a menu is enough to see a new palette.
 */
public class ConfigChangeHandler {

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (!UkyUI.MODID.equals(event.modID)) {
            return;
        }
        UiConfig.reload();
        Theme.rebuild();
        // The link row may now name different pictures, or the same names in a
        // different folder; a cache keyed by file name would go on serving the old ones.
        LinkIcons.invalidate();
        // What we lend to other mods is built from the palette too, and neither of
        // these is redrawn every frame from it: the quest book's widget sheet is
        // painted once and kept, and Waila's text colour is a field we only write
        // when it differs from what we last wrote.
        QuestBookTheme.invalidate();
        WailaPanel.invalidate();
    }
}
