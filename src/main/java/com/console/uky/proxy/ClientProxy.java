package com.console.uky.proxy;

import com.console.uky.client.WindowBranding;
import com.console.uky.client.death.DeathTracker;
import com.console.uky.client.gui.AchievementCommand;
import com.console.uky.client.mods.QuestBookTransition;
import com.console.uky.client.render.BlackHole;
import com.console.uky.client.render.Theme;
import com.console.uky.client.sound.UkyMusicTicker;
import com.console.uky.client.world.WorldEntryFade;
import com.console.uky.config.Quality;
import com.console.uky.config.UiConfig;
import com.console.uky.handler.AchievementChatHandler;
import com.console.uky.handler.ConfigChangeHandler;
import com.console.uky.handler.GuiEventHandler;
import com.console.uky.handler.WorldCaptureHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        // The palette is derived from config values, so it has to be built after load.
        Theme.rebuild();

        // Tracing the lensing takes a second or two on a background thread. Kicking
        // it off here means it is done well before the title screen needs it.
        // Nothing to warm up when the preset has taken the hole away entirely;
        // that trace is a second or two of a background thread and a texture to hold.
        if ("blackhole".equals(UiConfig.background) && Quality.blackHole()) {
            BlackHole.warmUp();
        }

        // One bus, not two. 1.7.10 kept FML's own events (ticks, ConfigChangedEvent)
        // on FMLCommonHandler.instance().bus() and Forge's on MinecraftForge.EVENT_BUS;
        // 1.12.2 folded the first into the second. Each handler is therefore registered
        // exactly once — the pairs that used to need both buses would now run twice.
        MinecraftForge.EVENT_BUS.register(new GuiEventHandler());

        // The achievement chat line, shortened and turned into a link on the way in.
        MinecraftForge.EVENT_BUS.register(new AchievementChatHandler());

        // Watches for what killed the player, and for the respawn that ends the scene.
        // One bus, unlike 1.7: FML folded its own into Forge's, so the tick, the sound
        // event and the config change all arrive here.
        MinecraftForge.EVENT_BUS.register(new DeathTracker());
        MinecraftForge.EVENT_BUS.register(new ConfigChangeHandler());
        // Render-tick driven: quitting a world waits for one clean frame of it.
        MinecraftForge.EVENT_BUS.register(new WorldCaptureHandler());

        // Draws the tail of the arrival dissolve, after the loading screen is gone
        // and there is no screen left to draw it from.
        MinecraftForge.EVENT_BUS.register(new WorldEntryFade.Handler());

        // The scrim under the quest book: the draw events it hangs the scrim on and
        // the render tick that lifts it afterwards are all on the one bus here.
        MinecraftForge.EVENT_BUS.register(new QuestBookTransition.Handler());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        // Not pre-init. Minecraft.init fires mod construction and pre-init from its
        // first loading stage, and only *then* assigns itself a fresh MusicTicker —
        // so a ticker installed in pre-init was overwritten a few lines later and
        // vanilla's menu music played anyway. init runs past that point.
        UkyMusicTicker.install(Minecraft.getMinecraft());

        // Also not pre-init: startup sets the title and the icon itself, and only
        // reaches mod loading afterwards. Doing this any earlier would be overwritten.
        WindowBranding.apply();

        // The other end of an achievement link in chat. Registered here rather than in
        // pre-init only for tidiness — the handler is a client command and nothing
        // dispatches one until a world is joined.
        AchievementCommand.register();
    }

}
