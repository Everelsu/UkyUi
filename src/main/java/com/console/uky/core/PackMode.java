package com.console.uky.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Easy or Standard: the same pack with a few mods switched off, chosen from the menu.
 *
 * <p>A mod is switched off the way launchers do it, by renaming its jar so Forge does
 * not load it ({@code .jar} to {@code .jar.disabled}; the older {@code .jar---} is
 * recognised too). That cannot happen while the game runs — Windows locks a loaded jar
 * — so the menu only records the choice, and {@link UkyCore} applies it on the next
 * launch, before Forge goes looking for mods and while every jar is still closed.
 *
 * <p>Which mods Easy switches off is a text file, {@code config/uky/easy-mode-mods.txt},
 * one file-name prefix per line, so the pack can change the list without a new build.
 * When it does not exist yet it is written from the pack itself: a pack shipped in its
 * Easy form has exactly those mods switched off already, so they are the list. Only a
 * pack with nothing switched off falls back to the built-in one.
 *
 * <p>Plain Java only: this runs in the coremod phase, before Minecraft or Forge
 * classes may be touched.
 */
public final class PackMode {

    public static final String EASY = "easy";
    public static final String STANDARD = "standard";

    private static final String LIST_FILE = "config/uky/easy-mode-mods.txt";
    private static final String PENDING_FILE = "config/uky/packmode.pending";
    private static final String OFF = ".disabled";
    private static final String OFF_LEGACY = "---";
    private static final Charset UTF8 = Charset.forName("UTF-8");

    /** Explains the file to whoever opens it; written above whatever list it starts with. */
    private static final List<String> HEADER = Arrays.asList(
            "# Моды, которые выключаются в режиме Easy. Один мод на строку.",
            "# Mods switched off in Easy mode. One mod per line.",
            "#",
            "# Пишите имя jar-файла из папки mods — целиком, как в примере ниже.",
            "# Write the jar's file name from the mods folder - in full, like the example.",
            "#",
            "#   SpecialMobs-3.7.0.jar",
            "#",
            "# Совпадение ищется по началу имени, без учёта регистра, поэтому можно",
            "# писать и без версии (SpecialMobs) - тогда строка переживёт обновление мода.",
            "# Matched by the start of the name, any case, so the name without its version",
            "# (SpecialMobs) works too, and keeps working when the mod is updated.",
            "#",
            "# Строки с # - комментарии. Изменения работают со следующего переключения режима.",
            "# Lines starting with # are comments. Changes apply from the next mode switch.",
            "");

    private static final List<String> DEFAULT_MODS = Arrays.asList(
            "nosleepmod",
            "EpicSiegeMod",
            "SpecialMobs");

    private static final List<String> DEFAULT_LIST = withHeader(DEFAULT_MODS);

    private static List<String> withHeader(List<String> mods) {
        List<String> lines = new ArrayList<String>(HEADER);
        lines.addAll(mods);
        return lines;
    }

    private PackMode() {
    }

    /** The mode the installed files are in, or null when this pack has none of the listed mods. */
    public static String current(File gameDir) {
        List<File> mods = listed(gameDir);
        if (mods.isEmpty()) {
            return null;
        }
        for (File mod : mods) {
            if (isEnabled(mod.getName())) {
                return STANDARD;
            }
        }
        return EASY;
    }

    /** The mode waiting for the next launch, or null if nothing is waiting. */
    public static String pending(File gameDir) {
        File file = new File(gameDir, PENDING_FILE);
        if (!file.isFile()) {
            return null;
        }
        try {
            String mode = new String(Files.readAllBytes(file.toPath()), UTF8).trim();
            return EASY.equals(mode) || STANDARD.equals(mode) ? mode : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Asks for {@code mode} on the next launch; asking for the current mode cancels the request. */
    public static void request(File gameDir, String mode) throws IOException {
        File file = new File(gameDir, PENDING_FILE);
        if (mode.equals(current(gameDir))) {
            if (file.isFile() && !file.delete()) {
                throw new IOException("Could not delete " + file);
            }
            return;
        }
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), mode.getBytes(UTF8));
    }

    /**
     * Renames the listed jars into the requested mode, if one is waiting.
     *
     * Called from the coremod phase. Never throws: a failed switch leaves the pack as
     * it was and the request in place to try again next launch.
     */
    public static void applyPending(File gameDir) {
        String mode = pending(gameDir);
        if (mode == null) {
            return;
        }
        boolean enable = STANDARD.equals(mode);
        boolean ok = true;
        for (File mod : listed(gameDir)) {
            String name = mod.getName();
            if (isEnabled(name) == enable) {
                continue;
            }
            File target = new File(mod.getParentFile(), enable ? enabledName(name) : name + OFF);
            if (target.exists()) {
                log("pack mode: " + target.getName() + " already exists, leaving " + name + " as it is");
                ok = false;
                continue;
            }
            if (mod.renameTo(target)) {
                log("pack mode: " + name + " -> " + target.getName());
            } else {
                log("pack mode: could not rename " + name + "; will try again next launch");
                ok = false;
            }
        }
        if (ok) {
            new File(gameDir, PENDING_FILE).delete();
            log("pack mode: now " + mode);
        }
    }

    // -------------------------------------------------------------------------

    private static boolean isEnabled(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    private static String enabledName(String name) {
        if (name.endsWith(OFF)) {
            return name.substring(0, name.length() - OFF.length());
        }
        if (name.endsWith(OFF_LEGACY)) {
            return name.substring(0, name.length() - OFF_LEGACY.length());
        }
        return name;
    }

    /** Jars in mods/, on or off, whose names start with a prefix from the list. */
    static List<File> listed(File gameDir) {
        List<String> prefixes = prefixes(gameDir);
        List<File> out = new ArrayList<File>();
        File[] files = new File(gameDir, "mods").listFiles();
        if (files == null) {
            return out;
        }
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            String lower = file.getName().toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".jar") && !lower.endsWith(".jar" + OFF) && !lower.endsWith(".jar" + OFF_LEGACY)) {
                continue;
            }
            for (String prefix : prefixes) {
                if (lower.startsWith(prefix)) {
                    out.add(file);
                    break;
                }
            }
        }
        return out;
    }

    private static List<String> prefixes(File gameDir) {
        File file = new File(gameDir, LIST_FILE);
        List<String> lines = DEFAULT_LIST;
        try {
            if (file.isFile()) {
                lines = Files.readAllLines(file.toPath(), UTF8);
            } else {
                lines = seed(gameDir);
                file.getParentFile().mkdirs();
                Files.write(file.toPath(), lines, UTF8);
            }
        } catch (IOException e) {
            log("pack mode: could not read " + LIST_FILE + ", using the built-in list");
        }
        List<String> out = new ArrayList<String>();
        for (String line : lines) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("#")) {
                out.add(t.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    /** The first list: what this pack already has switched off, else the built-in one. */
    private static List<String> seed(File gameDir) {
        List<String> found = new ArrayList<String>();
        File[] files = new File(gameDir, "mods").listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (!file.isFile() || isEnabled(name)) {
                    continue;
                }
                String on = enabledName(name);
                if (!on.equals(name) && on.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    // The full name, ".jar" and all: a prefix of itself switched off too.
                    found.add(on);
                }
            }
        }
        if (found.isEmpty()) {
            return DEFAULT_LIST;
        }
        List<String> lines = new ArrayList<String>(HEADER);
        lines.add("# Записано при первом запуске из модов, которые в сборке уже были выключены.");
        lines.add("# Written on first launch from the mods this pack already had switched off.");
        lines.addAll(found);
        return lines;
    }

    private static void log(String message) {
        // The game's logger is not up yet in the coremod phase.
        System.out.println("[UKY] " + message);
    }

    /** Self-check: run with a scratch directory. */
    public static void main(String[] args) throws IOException {
        File dir = Files.createTempDirectory("packmode").toFile();
        File mods = new File(dir, "mods");
        mods.mkdirs();
        new File(mods, "SpecialMobs-3.7.0.jar").createNewFile();
        new File(mods, "nosleepmod.jar---").createNewFile();
        new File(mods, "OtherMod-1.0.jar").createNewFile();
        new File(dir, LIST_FILE).getParentFile().mkdirs();
        Files.write(new File(dir, LIST_FILE).toPath(), DEFAULT_LIST, UTF8);

        check(STANDARD.equals(current(dir)), "one listed mod on means Standard");
        request(dir, EASY);
        check(EASY.equals(pending(dir)), "Easy is waiting");
        applyPending(dir);
        check(new File(mods, "SpecialMobs-3.7.0.jar" + OFF).isFile(), "SpecialMobs switched off");
        check(new File(mods, "OtherMod-1.0.jar").isFile(), "unlisted mod untouched");
        check(EASY.equals(current(dir)) && pending(dir) == null, "now Easy, nothing waiting");
        request(dir, STANDARD);
        applyPending(dir);
        check(new File(mods, "SpecialMobs-3.7.0.jar").isFile(), "SpecialMobs back on");
        check(new File(mods, "nosleepmod.jar").isFile(), "legacy --- name back on");
        request(dir, STANDARD);
        check(pending(dir) == null, "asking for the current mode waits for nothing");

        // A pack shipped in its Easy form writes its own list from what is switched off.
        File easyPack = Files.createTempDirectory("packmode-easy").toFile();
        File easyMods = new File(easyPack, "mods");
        easyMods.mkdirs();
        new File(easyMods, "SomeSiegeMod-2.0.jar---").createNewFile();
        new File(easyMods, "Other-1.0.jar").createNewFile();
        check(EASY.equals(current(easyPack)), "seeded from the switched-off jar");
        List<String> entries = prefixes(easyPack);
        check(entries.equals(Arrays.asList("somesiegemod-2.0.jar")),
                "the list is what was off, not the built-in one: " + entries);
        System.out.println("ok");
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}
