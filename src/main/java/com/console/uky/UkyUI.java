package com.console.uky;

import com.console.uky.proxy.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = UkyUI.MODID, name = UkyUI.NAME, version = UkyUI.VERSION,
     guiFactory = "com.console.uky.client.config.ConfigGuiFactory")
public class UkyUI {

    public static final String MODID = "uky";
    public static final String NAME = "UltraKill YI";
    /** Must match {@code version} in build.gradle.kts; see the note there. */
    public static final String VERSION = "0.5.4";

    public static final Logger LOGGER = LogManager.getLogger(NAME);

    @SidedProxy(clientSide = "com.console.uky.proxy.ClientProxy",
                serverSide = "com.console.uky.proxy.CommonProxy")
    public static CommonProxy proxy;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
