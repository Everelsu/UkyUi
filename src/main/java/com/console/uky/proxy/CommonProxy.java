package com.console.uky.proxy;

import com.console.uky.config.UiConfig;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Server-safe proxy. Keep anything here strictly logic-only —
 * no GuiScreen / rendering references, since this class is also
 * loaded on dedicated servers.
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        // Not getSuggestedConfigurationFile(): that is config/uky.cfg, and this mod
        // keeps its settings in config/uky/ alongside the link icons it reads from
        // the same folder. On the client the splash has usually loaded it already
        // and this call no-ops; on a dedicated server this is the one that runs.
        UiConfig.loadEarly(event.getModConfigurationDirectory().getParentFile());
    }

    public void init(FMLInitializationEvent event) {
    }
}
