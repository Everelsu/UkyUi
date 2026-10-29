package com.console.uky.client.sound;

import com.console.uky.config.UiConfig;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

/**
 * Keeps Minecraft's own music out of the menus.
 *
 * Vanilla plays {@code music.menu} whenever no world is loaded, which is precisely
 * when this mod is playing its own track — so the two ran on top of each other.
 * 1.7.10 had no hook for it and the ticker itself had to be swapped out; NeoForge asks
 * which music to play every tick, and answering "none" also stops whatever vanilla
 * still had going from the world that was just left.
 *
 * <p>Turning off {@code mainmenu.music} hands the menus back to vanilla, which is
 * the behaviour a pack author would expect from that switch.
 */
public final class UkyMusicTicker {

    @SubscribeEvent
    public void onSelectMusic(SelectMusicEvent event) {
        if (UiConfig.menuMusic && Minecraft.getInstance().level == null) {
            event.setMusic(null);
        }
    }
}
