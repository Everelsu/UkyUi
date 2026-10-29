package com.console.uky.client.mods;

import com.console.uky.UkyUI;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Which installed mods have a settings screen, and how to open it.
 *
 * <p>A mod offers one by registering an {@link IConfigScreenFactory} on its container;
 * that is the same thing NeoForge's own mod list looks for, so a mod whose Config
 * button works there is listed here.
 */
public final class ModConfigCatalog {

    public static final class Entry {

        public final ModContainer mod;
        /** The mod's own settings screen, or null if it registered none. */
        private final IConfigScreenFactory factory;
        /**
         * Whether this mod's settings can be redrawn in this mod's own widgets: it keeps
         * them in a NeoForge config spec, which describes every value as data.
         */
        public final boolean redrawable;

        Entry(ModContainer mod, IConfigScreenFactory factory, boolean redrawable) {
            this.mod = mod;
            this.factory = factory;
            this.redrawable = redrawable;
        }

        public String modId() {
            return this.mod.getModId();
        }

        public String displayName() {
            return this.mod.getModInfo().getDisplayName();
        }

        public String version() {
            return this.mod.getModInfo().getVersion().toString();
        }
    }

    private static List<Entry> entries;

    private ModConfigCatalog() {
    }

    /** Every mod with a settings screen, by display name. Built once. */
    public static List<Entry> entries() {
        if (entries == null) {
            List<Entry> found = new ArrayList<Entry>();
            ModList.get().forEachModContainer((id, container) -> {
                IConfigScreenFactory factory =
                        container.getCustomExtension(IConfigScreenFactory.class).orElse(null);
                boolean redrawable = hasSpec(id);
                // A spec is enough on its own: this mod can draw those settings even for
                // a mod that never wrote a screen for them.
                if (factory != null || redrawable) {
                    found.add(new Entry(container, factory, redrawable));
                }
            });
            found.sort((a, b) -> a.displayName().toLowerCase(Locale.ROOT)
                    .compareTo(b.displayName().toLowerCase(Locale.ROOT)));
            entries = Collections.unmodifiableList(found);
        }
        return entries;
    }

    private static boolean hasSpec(String modId) {
        for (ModConfig.Type type : ModConfig.Type.values()) {
            if (type == ModConfig.Type.STARTUP) {
                continue;
            }
            for (ModConfig config : ModConfigs.getConfigSet(type)) {
                if (config.getModId().equals(modId) && config.getSpec() instanceof ModConfigSpec) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Entry entryFor(String modId) {
        for (Entry entry : entries()) {
            if (entry.modId().equals(modId)) {
                return entry;
            }
        }
        return null;
    }

    public static boolean isLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    /** The mod's own settings screen, or null if building it failed. */
    public static Screen instantiate(Entry entry, Screen parent) {
        if (entry.factory == null) {
            return null;
        }
        try {
            return entry.factory.createScreen(entry.mod, parent);
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not open the settings of {}", entry.modId(), t);
            return null;
        }
    }
}
