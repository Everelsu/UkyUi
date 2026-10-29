package com.console.uky.handler;

import com.console.uky.client.render.LinkIcons;
import com.console.uky.client.render.Theme;
import com.console.uky.config.UiConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * Re-reads the config into the fields whenever NeoForge loads or reloads it — on start,
 * from the in-game config screen, and when the file is edited while the game runs —
 * and rebuilds everything derived from it. Registered on the mod bus.
 */
public class ConfigChangeHandler {

    @SubscribeEvent
    public void onLoad(ModConfigEvent.Loading event) {
        apply(event);
    }

    @SubscribeEvent
    public void onReload(ModConfigEvent.Reloading event) {
        apply(event);
    }

    private static void apply(ModConfigEvent event) {
        if (event.getConfig().getSpec() != UiConfig.SPEC) {
            return;
        }
        UiConfig.reload();
        Theme.rebuild();
        LinkIcons.invalidate();
    }
}
