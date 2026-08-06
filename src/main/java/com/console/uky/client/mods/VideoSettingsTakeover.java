package com.console.uky.client.mods;

import net.minecraftforge.fml.common.Loader;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.settings.GameSettings;

/**
 * A way through to whichever mod has taken over the video settings.
 *
 * Angelica, and renderer replacements generally, do not add a screen of their own
 * for anyone to find. They listen for Forge's {@code InitGuiEvent.Pre}, watch for
 * vanilla's {@link GuiVideoSettings} being opened, and swap their own screen in as it
 * appears — Angelica's client proxy does exactly that, putting Sodium's option pages
 * in its place. It is a neat trick and it has one consequence: a mod like this one,
 * which replaces the options screen outright and so never opens the vanilla video
 * screen at all, silently cuts the renderer's settings off from the player. The
 * shader options exist and there is no longer any door to them.
 *
 * <p>So this opens that door. The screen handed back is vanilla's, and whoever is
 * listening replaces it on the way up, exactly as it would have been from the vanilla
 * menu. Nothing here knows or needs to know what those options look like.
 *
 * <p>It is no longer the way in. {@link AngelicaOptions} reads the same options as a
 * model and the Graphics tab draws them itself, which is a better answer than a second
 * settings screen in a second style. This remains as the fallback for when that read
 * comes back empty — the mod has changed shape, or a claimant turns up that was never
 * built on Sodium at all — because the one outcome worth ruling out is the player
 * having renderer settings and no way whatsoever to reach them.
 */
public final class VideoSettingsTakeover {

    /**
     * Mods known to claim the video settings screen.
     *
     * Checked by id rather than by asking whether anything is listening, because
     * Forge offers no way to ask that: a handler is only revealed by firing the event,
     * and firing it means opening the screen. Being wrong here is cheap in one
     * direction and not the other — an unlisted mod means one missing entry, whereas
     * offering the entry with nothing behind it would drop the player into a bare
     * vanilla screen from a menu that had promised renderer settings.
     */
    private static final String[] CLAIMANTS = {"angelica"};

    private VideoSettingsTakeover() {
    }

    /** Whether some mod is expected to replace the video settings screen. */
    public static boolean isClaimed() {
        for (String modId : CLAIMANTS) {
            if (Loader.isModLoaded(modId)) {
                return true;
            }
        }
        return false;
    }

    /** The screen to open, before anyone has had the chance to replace it. */
    public static GuiScreen open(GuiScreen parent, GameSettings settings) {
        return new GuiVideoSettings(parent, settings);
    }
}
