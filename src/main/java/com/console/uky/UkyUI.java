package com.console.uky;

import com.console.uky.client.death.DeathTracker;
import com.console.uky.client.render.BlackHole;
import com.console.uky.client.render.Theme;
import com.console.uky.client.sound.UkyMusicTicker;
import com.console.uky.client.world.WorldEntryFade;
import com.console.uky.config.Quality;
import com.console.uky.config.UiConfig;
import com.console.uky.handler.ConfigChangeHandler;
import com.console.uky.handler.GuiEventHandler;
import com.console.uky.handler.WorldCaptureHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point. Client only: everything this mod does is drawn, and a dedicated
 * server loading it does nothing at all.
 */
@Mod(value = UkyUI.MODID, dist = Dist.CLIENT)
public class UkyUI {

    public static final String MODID = "uky";
    public static final String NAME = "UltraKill YI";
    /** Must match {@code version} in build.gradle.kts; see the note there. */
    public static final String VERSION = "0.6.1";

    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    public UkyUI(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, UiConfig.SPEC, UiConfig.FILE_NAME);
        // NeoForge's own editor for the file, from the mod list's Config button.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.register(new ConfigChangeHandler());
        modBus.register(new WorldCaptureHandler.SoundReload());
        modBus.addListener(this::clientSetup);

        NeoForge.EVENT_BUS.register(new GuiEventHandler());
        NeoForge.EVENT_BUS.register(new UkyMusicTicker());
        // Every tooltip, as one of this mod's panels.
        NeoForge.EVENT_BUS.register(new com.console.uky.client.gui.UkyTooltip());
        NeoForge.EVENT_BUS.register(new com.console.uky.client.gui.PlayerListOverlay.Handler());
        NeoForge.EVENT_BUS.register(new com.console.uky.client.gui.AchievementToast.Handler());
        // The advancement chat line, shortened and turned into a link to the list.
        NeoForge.EVENT_BUS.register(new com.console.uky.client.gui.AchievementLinks());
        // Watches for what killed the player, and for the respawn that ends the scene.
        NeoForge.EVENT_BUS.register(new DeathTracker());
        // Quitting a world waits for one clean frame of it.
        NeoForge.EVENT_BUS.register(new WorldCaptureHandler());
        // Draws the tail of the arrival dissolve, after the loading screen is gone and
        // there is no screen left to draw it from.
        NeoForge.EVENT_BUS.register(new WorldEntryFade.Handler());
        if (Boolean.getBoolean("uky.debugShots")) {
            NeoForge.EVENT_BUS.register(new DebugShots());
        }
    }

    private void clientSetup(FMLClientSetupEvent event) {
        // The palette is derived from config values, so it has to be built after load.
        Theme.rebuild();
        // Tracing the lensing takes a second or two on a background thread. Kicking it
        // off here means it is done well before the title screen needs it. Nothing to
        // warm up when the preset has taken the hole away entirely.
        if ("blackhole".equals(UiConfig.background) && Quality.blackHole()) {
            BlackHole.warmUp();
        }
    }
}
