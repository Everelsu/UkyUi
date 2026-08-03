package com.console.uky.client.render;

import com.console.uky.UkyUI;
import com.console.uky.config.UiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Icons for the link row: either one of ours, drawn from primitives, or a picture
 * the pack dropped in a folder.
 *
 * A pack wants its own marks — the real YouTube glyph, the real Discord one, a
 * server logo — and none of those can be shipped here or drawn convincingly from
 * rectangles. So anything is allowed: name a PNG in the icon folder and it is used
 * as-is. What a pack should not have to do is supply artwork just to get a
 * recognisable row, which is what {@link #guess} is for — an unmarked link is
 * given a shape based on where it points.
 */
public final class LinkIcons {

    /** Built-in shapes, by the name written in the config. */
    public static final String YOUTUBE = "youtube";
    public static final String DISCORD = "discord";
    public static final String TELEGRAM = "telegram";
    public static final String BOOSTY = "boosty";
    public static final String TWITCH = "twitch";
    public static final String PATREON = "patreon";
    public static final String PLAY = "play";
    public static final String CHAT = "chat";
    public static final String HEART = "heart";
    public static final String STAR = "star";
    public static final String GLOBE = "globe";
    public static final String SPARK = "spark";
    public static final String SKULL = "skull";

    /** Loaded pictures, by file name. A null value means "tried, and it failed". */
    private static final Map<String, ResourceLocation> loaded =
            new HashMap<String, ResourceLocation>();

    private LinkIcons() {
    }

    /** The folder pack icons are read from, created if it is not there yet. */
    public static File folder() {
        File dir = new File(Minecraft.getMinecraft().mcDataDir, UiConfig.linkIconFolder);
        if (!dir.exists()) {
            // Made eagerly and on purpose: a folder that exists is documentation, and
            // "put your PNGs here" is much easier to act on when the here is visible.
            dir.mkdirs();
        }
        return dir;
    }

    /** Whether {@code spec} names a picture rather than one of the built-in shapes. */
    public static boolean isImage(String spec) {
        return spec != null && spec.toLowerCase(Locale.ROOT).endsWith(".png");
    }

    /**
     * Loads a pack icon, or returns null if there is nothing usable at that name.
     *
     * Cached both ways: a picture is uploaded to GL once, and a name that could not
     * be read is remembered as unreadable so a missing file does not mean an I/O
     * attempt every frame.
     */
    public static ResourceLocation image(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        if (loaded.containsKey(fileName)) {
            return loaded.get(fileName);
        }

        ResourceLocation location = null;
        try {
            File file = new File(folder(), fileName);
            // Contained to the icon folder: the config is a text file a pack ships,
            // and it has no business naming paths elsewhere on the disk.
            if (file.isFile() && file.getParentFile().equals(folder())) {
                BufferedImage image = ImageIO.read(file);
                if (image != null) {
                    DynamicTexture texture = new DynamicTexture(image);
                    location = Minecraft.getMinecraft().getTextureManager()
                            .getDynamicTextureLocation("uky_link_" + fileName, texture);
                }
            }
            if (location == null) {
                UkyUI.LOGGER.warn("Link icon {} not found in {}", fileName, folder());
            }
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not read link icon " + fileName, t);
        }

        loaded.put(fileName, location);
        return location;
    }

    /** Drops the cache, so an edited icon is picked up without a restart. */
    public static void invalidate() {
        loaded.clear();
    }

    /**
     * A shape for a link that did not ask for one, from where it points.
     *
     * Deliberately coarse. The point is that a pack that writes nothing but a label
     * and a URL still gets a row that reads at a glance — video is a play triangle,
     * somewhere to talk is a bubble, somewhere to support the pack is a heart —
     * and anything unrecognised is simply a link.
     */
    public static String guess(String url) {
        String host = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (contains(host, "youtube", "youtu.be")) {
            return YOUTUBE;
        }
        if (contains(host, "discord")) {
            return DISCORD;
        }
        if (contains(host, "t.me", "telegram")) {
            return TELEGRAM;
        }
        if (contains(host, "boosty")) {
            return BOOSTY;
        }
        if (contains(host, "twitch")) {
            return TWITCH;
        }
        if (contains(host, "patreon")) {
            return PATREON;
        }
        if (contains(host, "rutube", "vimeo", "kick.com", "nimo", "trovo")) {
            return PLAY;
        }
        if (contains(host, "vk.com", "reddit", "matrix", "guilded", "teamspeak", "steamcommunity")) {
            return CHAT;
        }
        if (contains(host, "donationalerts", "ko-fi", "buymeacoffee", "paypal", "yoomoney",
                "donate")) {
            return HEART;
        }
        if (contains(host, "github", "gitlab", "curseforge", "modrinth")) {
            return STAR;
        }
        return GLOBE;
    }

    private static boolean contains(String haystack, String... needles) {
        for (int i = 0; i < needles.length; i++) {
            if (haystack.contains(needles[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Draws a built-in shape inside a square box centred on (cx, cy).
     *
     * @param behind what the mark is being drawn on, for the parts cut out of it
     */
    public static void draw(String name, float cx, float cy, float size, int colour, int behind) {
        if (YOUTUBE.equals(name)) {
            Icons.youtube(cx, cy, size, colour, behind);
        } else if (DISCORD.equals(name)) {
            Icons.discord(cx, cy, size, colour, behind);
        } else if (TELEGRAM.equals(name)) {
            Icons.telegram(cx, cy, size, colour);
        } else if (BOOSTY.equals(name)) {
            Icons.boosty(cx, cy, size, colour);
        } else if (TWITCH.equals(name)) {
            Icons.twitch(cx, cy, size, colour, behind);
        } else if (PATREON.equals(name)) {
            Icons.patreon(cx, cy, size, colour);
        } else if (PLAY.equals(name)) {
            Icons.play(cx, cy, size, colour);
        } else if (CHAT.equals(name)) {
            Icons.chat(cx, cy, size, colour);
        } else if (HEART.equals(name)) {
            Icons.heart(cx, cy, size, colour);
        } else if (STAR.equals(name)) {
            Icons.star(cx, cy, size, colour);
        } else if (SPARK.equals(name)) {
            Icons.spark(cx, cy, size, colour);
        } else if (SKULL.equals(name)) {
            Icons.skull(cx, cy, size, colour, behind);
        } else {
            Icons.globe(cx, cy, size, colour);
        }
    }
}
