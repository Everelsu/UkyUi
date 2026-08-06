package com.console.uky.client.mods;

import com.console.uky.UkyUI;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraft.client.gui.GuiScreen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Every loaded mod that has a settings screen, and how much can be done with it.
 *
 * Forge already knows this — it is what puts the "Config" button in the mod list —
 * but it only exposes it one mod at a time, buried two screens deep. Gathering it up
 * front is what lets the settings screen show mod options as a first-class list
 * rather than an afterthought.
 *
 * <p>Nothing here is declared or configured anywhere. The catalogue is whatever is
 * actually loaded, so a pack that drops a mod loses its entry and a pack that adds
 * one gains it, with no list to keep in step. That also answers the optional-mod
 * question directly: {@link #isLoaded} is the check, and an absent mod simply has no
 * row.
 */
public final class ModConfigCatalog {

    /** One mod's settings screen. */
    public static final class Entry {

        public final ModContainer mod;
        /**
         * The mod's own factory, which builds the screen.
         *
         * 1.7.10 named the class and let the caller construct it; 1.12.2 hands the
         * construction to the factory, so that is what has to be kept. It also means
         * "is this one of Forge's screens" can no longer be answered from a class
         * literal and has to be answered by building one — see {@link #discover}.
         */
        public final IModGuiFactory factory;

        /**
         * Whether the screen is one of Forge's, and so can be redrawn in our own
         * widgets rather than merely opened.
         *
         * A {@link GuiConfig} is not really a screen so much as a renderer for a list
         * of {@link IConfigElement}, and that list is public. Anything built on it can
         * be taken apart and presented however we like, which is most of them. The
         * rest are hand-written screens with no model behind them, and those can only
         * be opened as their author drew them.
         */
        public final boolean redrawable;

        Entry(ModContainer mod, IModGuiFactory factory, boolean redrawable) {
            this.mod = mod;
            this.factory = factory;
            this.redrawable = redrawable;
        }

        public String modId() {
            return this.mod.getModId();
        }

        public String displayName() {
            String name = this.mod.getName();
            return name == null || name.isEmpty() ? this.mod.getModId() : name;
        }

        public String version() {
            String version = this.mod.getDisplayVersion();
            return version == null ? "" : version;
        }
    }

    private static List<Entry> entries;

    private ModConfigCatalog() {
    }

    /** Every mod with a settings screen, by display name. Built once. */
    public static List<Entry> entries() {
        if (entries == null) {
            // Never throws. This is called while the settings screen is being built,
            // so anything escaping here takes the whole screen down with it — and the
            // input is ninety third-party jars, every one of which is free to do
            // something unexpected when asked about its config. An empty catalogue
            // costs one missing row; an exception costs the settings menu.
            try {
                entries = discover();
            } catch (Throwable t) {
                UkyUI.LOGGER.error("Could not build the mod settings catalogue", t);
                entries = new ArrayList<Entry>();
            }
        }
        return entries;
    }

    /** Whether a mod is present, for anything that wants to offer an optional entry. */
    public static boolean isLoaded(String modId) {
        return Loader.isModLoaded(modId);
    }

    /** Forgets the catalogue, so a resource reload picks up any change. */
    public static void invalidate() {
        entries = null;
    }

    private static List<Entry> discover() {
        List<Entry> found = new ArrayList<Entry>();
        FMLClientHandler client = FMLClientHandler.instance();

        for (ModContainer mod : Loader.instance().getActiveModList()) {
            // Asking each mod separately, and catching per mod. A pack this size will
            // eventually contain one mod whose factory throws on construction, and one
            // broken mod must not cost the list every other entry.
            try {
                IModGuiFactory factory = client.getGuiFactoryFor(mod);
                if (factory == null || !factory.hasConfigGui()) {
                    continue;
                }
                found.add(new Entry(mod, factory, isForgeScreen(factory)));
            } catch (Throwable t) {
                UkyUI.LOGGER.warn("Could not read the settings screen of mod {}",
                        mod.getModId(), t);
            }
        }

        Collections.sort(found, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return a.displayName().compareToIgnoreCase(b.displayName());
            }
        });

        int redrawable = 0;
        for (Entry entry : found) {
            if (entry.redrawable) {
                redrawable++;
            }
        }
        UkyUI.LOGGER.info("Mod settings: {} mods with a config screen, {} of them redrawable",
                Integer.valueOf(found.size()), Integer.valueOf(redrawable));
        return found;
    }

    /**
     * Builds the mod's own screen, the way Forge does.
     *
     * @return the screen, or null if it could not be constructed — a mod is free to
     *         name a class that does not have the constructor Forge expects, and that
     *         is its bug to have rather than a reason to crash out of the menu
     */
    public static GuiScreen instantiate(Entry entry, GuiScreen parent) {
        try {
            return entry.factory.createConfigGui(parent);
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not open the settings screen of mod {}", entry.modId(), t);
            return null;
        }
    }

    /**
     * Whether this factory produces one of Forge's own screens.
     *
     * On 1.7.10 this was a class comparison and cost nothing. 1.12.2 only offers a
     * factory method, so the only way to find out is to build one and look at it —
     * once, at discovery, with the result thrown away. The throwaway parent is a bare
     * {@code GuiScreen} rather than null because a mod is free to touch its parent in
     * the constructor, and a probe must not be the thing that breaks it.
     */
    /** Stand-in parent for the probe below; never shown, never drawn. */
    private static final GuiScreen PROBE_PARENT = new GuiScreen() {
    };

    private static boolean isForgeScreen(IModGuiFactory factory) {
        try {
            return factory.createConfigGui(PROBE_PARENT) instanceof GuiConfig;
        } catch (Throwable t) {
            // Not answerable, so treat it as hand-written: it will be opened as its
            // author drew it rather than taken apart.
            return false;
        }
    }

    /**
     * Takes the element list out of a Forge settings screen.
     *
     * The screen is built and then only read: its element list is public and points at
     * the mod's live {@code Property} objects, so editing through it is editing the
     * real config, exactly as the screen we threw away would have.
     *
     * @return the elements, or null if this screen is not one of Forge's
     */
    @SuppressWarnings("unchecked")
    public static List<IConfigElement> harvest(Entry entry, GuiScreen parent) {
        if (!entry.redrawable) {
            return null;
        }
        GuiScreen screen = instantiate(entry, parent);
        if (!(screen instanceof GuiConfig)) {
            return null;
        }
        List<IConfigElement> elements = ((GuiConfig) screen).configElements;
        return elements == null || elements.isEmpty() ? null : elements;
    }
}
