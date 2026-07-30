package com.console.uky.proxy;

import com.console.uky.config.UiConfig;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * Server-safe proxy. Keep anything here strictly logic-only —
 * no GuiScreen / rendering references, since this class is also
 * loaded on dedicated servers.
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        UiConfig.load(event.getSuggestedConfigurationFile());
    }

    public void init(FMLInitializationEvent event) {
    }
}
