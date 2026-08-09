package com.console.uky.proxy;

import com.console.uky.client.WindowBranding;
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
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
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

        MinecraftForge.EVENT_BUS.register(new GuiEventHandler());

        // Watches for what killed the player, and for the respawn that ends the
        // scene. Both buses: the tick is FML's, the explosion it listens for is a
        // sound event on Forge's.
        DeathTracker deaths = new DeathTracker();
        FMLCommonHandler.instance().bus().register(deaths);
        MinecraftForge.EVENT_BUS.register(deaths);
        // ConfigChangedEvent lives on FML's bus, not Forge's.
        FMLCommonHandler.instance().bus().register(new ConfigChangeHandler());
        // Render-tick driven: quitting a world waits for one clean frame of it.
        // It listens on both buses — TickEvent is FML's, SoundLoadEvent is Forge's.
        WorldCaptureHandler capture = new WorldCaptureHandler();
        FMLCommonHandler.instance().bus().register(capture);
        MinecraftForge.EVENT_BUS.register(capture);

        // Draws the tail of the arrival dissolve, after the loading screen is gone
        // and there is no screen left to draw it from. RenderTickEvent is FML's.
        FMLCommonHandler.instance().bus().register(new WorldEntryFade.Handler());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        // Not pre-init. Minecraft.startGame fires mod construction and pre-init from
        // beginMinecraftLoading, and only *then* assigns itself a fresh MusicTicker —
        // so a ticker installed in pre-init was overwritten a few lines later and
        // vanilla's menu music played anyway. init runs from finishMinecraftLoading,
        // which is past that point.
        UkyMusicTicker.install(Minecraft.getMinecraft());

        // Also not pre-init: startGame sets the title and the icon itself, and only
        // reaches mod loading afterwards. Doing this any earlier would be overwritten.
        WindowBranding.apply();
    }

}
