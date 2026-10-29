package com.console.uky.client.config;

import com.console.uky.UkyUI;
import com.console.uky.config.UiConfig;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game config screen: every category of {@code uky.cfg} as its own page.
 *
 * Uses FML's stock {@link GuiConfig} rather than a hand-drawn one — this screen
 * is reached from the mod list, which is already vanilla-styled, and getting
 * property editing right is not worth re-implementing.
 */
public class ConfigScreen extends GuiConfig {

    public ConfigScreen(GuiScreen parent) {
        super(parent,
                categories(),
                UkyUI.MODID,
                false,
                false,
                UkyUI.NAME);
    }

    @SuppressWarnings("rawtypes")
    private static List<IConfigElement> categories() {
        List<IConfigElement> elements = new ArrayList<IConfigElement>();
        for (String name : UiConfig.raw().getCategoryNames()) {
            elements.add(new ConfigElement(UiConfig.raw().getCategory(name)));
        }
        return elements;
    }
}
