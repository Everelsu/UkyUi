package com.console.uky.compat;

import com.console.uky.UkyUI;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The singleplayer saves, as the world list needs them: listed, opened, deleted,
 * renamed and re-created. What 1.7.10 kept on {@code ISaveFormat}, in 1.21's terms.
 */
public final class Saves {

    private Saves() {
    }

    private static LevelStorageSource source() {
        return Minecraft.getInstance().getLevelSource();
    }

    /**
     * Every save, most recently played first.
     *
     * Read synchronously. Vanilla reads them on a worker and draws a spinner meanwhile;
     * this screen lays its tiles out from the list, and a pack's few dozen level.dat
     * files are read in a few milliseconds.
     */
    public static List<LevelSummary> list() throws Exception {
        LevelStorageSource source = source();
        List<LevelSummary> found = new ArrayList<LevelSummary>(
                source.loadLevelSummaries(source.findLevelCandidates()).join());
        Collections.sort(found);
        return found;
    }

    public static void delete(String folder) throws Exception {
        try (LevelStorageSource.LevelStorageAccess access = source().createAccess(folder)) {
            access.deleteLevel();
        }
    }

    public static void rename(String folder, String name) throws Exception {
        try (LevelStorageSource.LevelStorageAccess access = source().createAccess(folder)) {
            access.renameLevel(name);
        }
    }

    /** Opens a save, with vanilla's checks for old versions and experimental features. */
    public static void play(String folder, Screen back) {
        Minecraft mc = Minecraft.getInstance();
        mc.createWorldOpenFlows().openWorld(folder, () -> mc.setScreen(back));
    }

    public static void create(Screen back) {
        CreateWorldScreen.openFresh(Minecraft.getInstance(), back);
    }

    /** World creation pre-filled from an existing save — vanilla's "Re-Create". */
    public static void recreate(String folder, Screen back) {
        Minecraft mc = Minecraft.getInstance();
        try (LevelStorageSource.LevelStorageAccess access = source().validateAndCreateAccess(folder)) {
            Pair<LevelSettings, WorldCreationContext> data = mc.createWorldOpenFlows().recreateWorldData(access);
            Path packs = CreateWorldScreen.createTempDataPackDirFromExistingWorld(
                    access.getLevelPath(LevelResource.DATAPACK_DIR), mc);
            data.getSecond().validate();
            mc.setScreen(CreateWorldScreen.createFromExisting(mc, back, data.getFirst(), data.getSecond(), packs));
        } catch (Exception e) {
            UkyUI.LOGGER.warn("Could not read {} to re-create it", folder, e);
        }
    }
}
