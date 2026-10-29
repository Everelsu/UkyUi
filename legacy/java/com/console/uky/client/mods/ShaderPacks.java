package com.console.uky.client.mods;

import com.console.uky.UkyUI;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Iris's shader packs, as a model this interface can draw itself.
 *
 * <p>Angelica carries a port of Iris, and Iris carries a screen: a list of packs, a
 * switch for whether shaders run at all, a door to the folder they live in. That screen
 * is the one part of the renderer's settings this mod was still handing the player off
 * to, and it is the part that showed it — opened over a world it left the world showing
 * through where its list should have been, and opened from the title screen it left a
 * dark rectangle there instead. Either way the packs themselves were not on it.
 *
 * <p>What the screen is drawn from, though, is four static methods and a config object,
 * and those are as readable as Sodium's option pages are. So the packs are read here
 * and {@code GuiShaderPacksScreen} draws them in the palette, next to the rest of the
 * settings, in both places.
 *
 * <h2>Applying is Iris's own sequence, not ours</h2>
 *
 * <p>Selecting a pack is two steps and they are not interchangeable:
 * {@code IrisConfig.setShaderPackName} stages the name, and
 * {@code IrisApi.getConfig().setShadersEnabledAndApply} is what saves the config and
 * reloads the pipeline. That second call reloads whether or not the flag it is handed
 * has changed, which is exactly what a newly staged pack name needs — so it is the one
 * call this makes, and it is the same one Iris's own Apply button makes.
 *
 * <p>Everything is reflection, for the reason {@link AngelicaOptions} gives: Angelica is
 * not a compile dependency and must not become one. Nothing here throws outward — a
 * failure costs the screen, and the settings row falls back to Iris's own.
 */
public final class ShaderPacks {

    private static final String IRIS = "net.coderbot.iris.Iris";
    private static final String IRIS_API = "net.irisshaders.iris.api.v0.IrisApi";
    private static final String IRIS_API_CONFIG = "net.irisshaders.iris.api.v0.IrisApiConfig";
    private static final String BACKEND_MANAGER =
            "com.gtnewhorizons.angelica.glsm.backend.BackendManager";
    private static final String RENDER_BACKEND =
            "com.gtnewhorizons.angelica.glsm.backend.RenderBackend";

    /** What a pack is called when there is none: shaders off, vanilla rendering. */
    public static final String NONE = "";

    private static boolean resolved;
    private static boolean usable;

    private static Method getIrisConfig;
    private static Method getDirectoryManager;
    private static Method getShaderpacksDirectory;
    private static Method enumeratePacks;
    private static Method configGetPackName;
    private static Method configSetPackName;
    private static Method configShadersEnabled;

    private ShaderPacks() {
    }

    /**
     * Whether there is a shader system here to talk to at all.
     *
     * Two questions in one: Angelica's Iris has a compile-time switch of its own
     * ({@code Iris.enabled}, false in a build with shaders stripped out), and beyond
     * that everything below has to have resolved.
     */
    public static boolean available() {
        return AngelicaOptions.hasShaderPacks() && resolve();
    }

    /**
     * Every pack in the folder, in the order Iris sorts them.
     *
     * Its own directory manager rather than a listing of our own: it is what decides
     * what counts as a pack — a folder with shaders in it, or a zip — and reproducing
     * that test here would mean two answers to one question, differing by exactly the
     * cases nobody tested.
     *
     * @return the names, or an empty list if they could not be read
     */
    public static List<String> packs() {
        if (!resolve()) {
            return new ArrayList<String>();
        }
        try {
            Object manager = getDirectoryManager.invoke(null);
            Object names = enumeratePacks.invoke(manager);
            List<String> out = new ArrayList<String>();
            if (names instanceof Collection) {
                for (Object name : (Collection<?>) names) {
                    if (name != null) {
                        out.add(name.toString());
                    }
                }
            }
            return out;
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not list the shader packs", t);
            return new ArrayList<String>();
        }
    }

    /** The pack currently selected, or {@link #NONE} when there is not one. */
    public static String selected() {
        if (!resolve()) {
            return NONE;
        }
        try {
            Object config = getIrisConfig.invoke(null);
            Object name = configGetPackName.invoke(config);
            // An Optional, which is spelled the same in every version this can meet.
            if (name instanceof java.util.Optional) {
                Object value = ((java.util.Optional<?>) name).orElse(null);
                return value == null ? NONE : value.toString();
            }
            return name == null ? NONE : name.toString();
        } catch (Throwable t) {
            return NONE;
        }
    }

    /** Whether shaders are switched on, whatever pack is selected. */
    public static boolean enabled() {
        if (!resolve()) {
            return false;
        }
        try {
            return ((Boolean) configShadersEnabled.invoke(getIrisConfig.invoke(null)))
                    .booleanValue();
        } catch (Throwable t) {
            return false;
        }
    }

    /** The folder packs are read from, for the button that opens it. */
    public static File folder() {
        if (!resolve()) {
            return null;
        }
        try {
            Object path = getShaderpacksDirectory.invoke(null);
            // A java.nio Path, which every version of this has returned. toFile keeps
            // the caller working in the one type the game's own folder buttons use.
            Method toFile = path.getClass().getMethod("toFile");
            toFile.setAccessible(true);
            return (File) toFile.invoke(path);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Selects a pack and turns shaders on or off, then reloads.
     *
     * <p>The name is staged first and the apply is what carries it through — see the
     * note on the class. Applying is not cheap: it destroys the pipeline and builds it
     * again, which is a visible pause on a large pack, so this is called from a click
     * and never from a hover or a keyboard move through the list.
     *
     * @param pack    the pack to select, or {@link #NONE} to leave the selection alone
     * @param shaders whether shaders should be running afterwards
     * @return whether the change was applied; false leaves everything as it was
     */
    public static boolean apply(String pack, boolean shaders) {
        if (!resolve()) {
            return false;
        }
        try {
            if (pack != null && !NONE.equals(pack)) {
                configSetPackName.invoke(getIrisConfig.invoke(null), pack);
            }
            // Through the interfaces, not through what they turn out to be: the
            // implementations live in a package of Iris's own and need not be public,
            // and a public method reached through a non-public class is refused.
            Class<?> apiType = Class.forName(IRIS_API);
            Object api = apiType.getMethod("getInstance").invoke(null);
            Object config = apiType.getMethod("getConfig").invoke(api);
            Class.forName(IRIS_API_CONFIG)
                    .getMethod("setShadersEnabledAndApply", boolean.class)
                    .invoke(config, Boolean.valueOf(shaders));
            return true;
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not apply the shader pack", t);
            return false;
        }
    }

    // ------------------------------------------------------------ dropped files --

    /**
     * Angelica's own drag-and-drop, which is the only one there is.
     *
     * <p>LWJGL 2 cannot report a file dropped on the window at all, so this is not
     * something this mod could do for itself: Angelica's render backend is what opens
     * that channel, and it does it on request — {@code startFileDrop} while a screen
     * that accepts packs is open, {@code stopFileDrop} when it closes, and a poll each
     * frame for whatever landed in between. Iris's own screen does exactly this, and
     * this is the same three calls.
     *
     * <p>Resolved apart from everything else above, because a backend that cannot do it
     * (or an Angelica that has moved it) should cost the drop and not the screen.
     */
    private static boolean dropResolved;
    private static boolean dropUsable;
    private static Object backend;
    private static Method startDrop;
    private static Method stopDrop;
    private static Method pollDrop;
    private static Method isValidPack;
    private static Method copyPack;

    /** Whether a pack can be dragged onto the window at all. */
    public static boolean fileDropSupported() {
        return resolveDrop();
    }

    /** Opens the channel. Called while the shader screen is up, and only then. */
    public static void startFileDrop() {
        if (!resolveDrop()) {
            return;
        }
        try {
            startDrop.invoke(backend);
        } catch (Throwable t) {
            dropUsable = false;
            UkyUI.LOGGER.warn("Could not listen for dropped shader packs", t);
        }
    }

    /** Closes it again. A window that is still listening after the screen is gone
     *  would collect drops nothing is going to read. */
    public static void stopFileDrop() {
        if (!dropUsable) {
            return;
        }
        try {
            stopDrop.invoke(backend);
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not stop listening for dropped shader packs", t);
        }
    }

    /**
     * Takes whatever has been dropped since the last frame into the packs folder.
     *
     * <p>Iris decides what counts: {@code isValidShaderpack} is the same test its own
     * screen applies, so a dropped screenshot is ignored rather than copied in and
     * listed as a broken pack. The copy itself is Iris's too — it knows a folder from a
     * zip and where either goes.
     *
     * <p>A pack of the same name already in the folder is left alone rather than
     * overwritten. Replacing somebody's edited copy of a pack because a stale download
     * was dragged in is not a thing to do silently.
     *
     * @return how many packs were added; 0 for the ordinary frame where nothing was
     */
    public static int acceptDropped() {
        if (!dropUsable) {
            return 0;
        }
        try {
            Object polled = pollDrop.invoke(backend);
            if (!(polled instanceof java.util.List) || ((java.util.List<?>) polled).isEmpty()) {
                return 0;
            }
            java.util.List<?> paths = (java.util.List<?>) polled;
            Object manager = getDirectoryManager.invoke(null);
            int added = 0;
            for (int i = 0; i < paths.size(); i++) {
                Object name = paths.get(i);
                if (name == null) {
                    continue;
                }
                java.nio.file.Path path = java.nio.file.Paths.get(name.toString());
                if (!((Boolean) isValidPack.invoke(null, path)).booleanValue()) {
                    continue;
                }
                try {
                    copyPack.invoke(manager, path.getFileName().toString(), path);
                    added++;
                } catch (Throwable already) {
                    // Most often FileAlreadyExistsException, wrapped: the pack is in
                    // the folder already, which is not a failure worth a log line.
                    UkyUI.LOGGER.info("Dropped shader pack {} was not copied: {}",
                            path.getFileName(), already.toString());
                }
            }
            return added;
        } catch (Throwable t) {
            dropUsable = false;
            UkyUI.LOGGER.warn("Could not read the dropped files", t);
            return 0;
        }
    }

    private static boolean resolveDrop() {
        if (dropResolved) {
            return dropUsable;
        }
        dropResolved = true;
        if (!resolve()) {
            return false;
        }
        try {
            Object holder = Class.forName(BACKEND_MANAGER).getField("RENDER_BACKEND").get(null);
            Class<?> type = Class.forName(RENDER_BACKEND);
            Method supports = type.getMethod("supportsFileDrop");
            if (holder == null
                    || !((Boolean) supports.invoke(holder)).booleanValue()) {
                return false;
            }
            backend = holder;
            startDrop = type.getMethod("startFileDrop");
            stopDrop = type.getMethod("stopFileDrop");
            pollDrop = type.getMethod("pollDroppedFiles");
            isValidPack = Class.forName(IRIS)
                    .getMethod("isValidShaderpack", java.nio.file.Path.class);
            copyPack = getDirectoryManager.getReturnType()
                    .getMethod("copyPackIntoDirectory", String.class, java.nio.file.Path.class);
            dropUsable = true;
        } catch (Throwable t) {
            dropUsable = false;
            UkyUI.LOGGER.info("Shader packs cannot be dragged in here: {}", t.toString());
        }
        return dropUsable;
    }

    /**
     * Looks up everything this needs, once.
     *
     * All or nothing on purpose. A half-resolved handle set would mean a screen that
     * lists packs and cannot select one, which is worse than the screen that was
     * already there — so a single miss marks the whole thing unusable and the settings
     * row goes back to opening Iris's own.
     */
    private static boolean resolve() {
        if (resolved) {
            return usable;
        }
        resolved = true;
        try {
            Class<?> iris = Class.forName(IRIS);
            getIrisConfig = iris.getMethod("getIrisConfig");
            getDirectoryManager = iris.getMethod("getShaderpacksDirectoryManager");
            getShaderpacksDirectory = iris.getMethod("getShaderpacksDirectory");

            Class<?> manager = getDirectoryManager.getReturnType();
            enumeratePacks = manager.getMethod("enumerate");

            Class<?> config = getIrisConfig.getReturnType();
            configGetPackName = config.getMethod("getShaderPackName");
            configSetPackName = config.getMethod("setShaderPackName", String.class);
            configShadersEnabled = config.getMethod("areShadersEnabled");

            // Not called, only proved: the apply path goes through these two and a
            // screen that cannot apply should never open in the first place.
            Class.forName(IRIS_API);
            Class.forName(IRIS_API_CONFIG);

            setAccessible();
            usable = true;
        } catch (Throwable t) {
            usable = false;
            UkyUI.LOGGER.info("Shader packs will use the renderer's own screen: {}",
                    t.toString());
        }
        return usable;
    }

    /**
     * The methods are public, and their declaring classes are not always.
     *
     * {@code getShaderpacksDirectoryManager} hands back a type that a build can leave
     * package-private, and a public method on a non-public class is exactly the case
     * reflection refuses without this.
     */
    private static void setAccessible() {
        Method[] handles = {getIrisConfig, getDirectoryManager, getShaderpacksDirectory,
            enumeratePacks, configGetPackName, configSetPackName, configShadersEnabled};
        for (int i = 0; i < handles.length; i++) {
            handles[i].setAccessible(true);
        }
    }
}
