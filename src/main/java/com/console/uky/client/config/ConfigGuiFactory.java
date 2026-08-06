package com.console.uky.client.config;

import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import java.util.Set;

/**
 * Wires the "Config" button in FML's mod list to {@link ConfigScreen}, so the
 * whole theme can be retuned from inside the game.
 *
 * 1.7.10 asked for the screen's class and constructed it itself, which meant the
 * screen had to have a one-GuiScreen-argument constructor and nothing else. 1.12.2
 * asks the factory to build it, so the parent is handed over here instead.
 */
public class ConfigGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraftInstance) {
    }

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parentScreen) {
        return new ConfigScreen(parentScreen);
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }
}
