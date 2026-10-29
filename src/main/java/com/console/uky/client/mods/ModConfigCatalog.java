package com.console.uky.client.mods;

import com.console.uky.UkyUI;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
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
        private final IConfigScreenFactory factory;
        /**
         * Whether this mod's settings can be redrawn in this mod's own widgets. None yet:
         * on 1.7.10 that was every mod built on FML's GuiConfig, whose element list is
         * public. Kept so the screens that badge such rows need not change.
         */
        public final boolean redrawable = false;

        Entry(ModContainer mod, IConfigScreenFactory factory) {
            this.mod = mod;
            this.factory = factory;
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
                container.getCustomExtension(IConfigScreenFactory.class)
                        .ifPresent(factory -> found.add(new Entry(container, factory)));
            });
            found.sort((a, b) -> a.displayName().toLowerCase(Locale.ROOT)
                    .compareTo(b.displayName().toLowerCase(Locale.ROOT)));
            entries = Collections.unmodifiableList(found);
        }
        return entries;
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
        try {
            return entry.factory.createScreen(entry.mod, parent);
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not open the settings of {}", entry.modId(), t);
            return null;
        }
    }
}
