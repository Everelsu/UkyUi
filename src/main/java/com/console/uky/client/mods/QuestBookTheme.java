package com.console.uky.client.mods;

import com.console.uky.UkyUI;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Theme;
import com.console.uky.config.UiConfig;
import net.minecraftforge.fml.common.Loader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/**
 * A BetterQuesting theme built out of this mod's palette.
 *
 * BetterQuesting draws its quest book entirely through a theme: every panel, button,
 * scrollbar, quest node and text colour is looked up by name from whichever theme is
 * active, and a theme is nothing but that table. Which means the quest book does not
 * have to be fought or redrawn — it has to be given a better table. This builds one
 * from {@link Theme}, paints the widgets it names with {@link QuestBookAtlas}, hands
 * it over, and then gets out of the way. Every screen the quest book has, including
 * the editors a pack author uses and the ones a future version adds, comes out in the
 * palette without a line of code here knowing they exist.
 *
 * <p>Everything is done by reflection and nothing is compiled against. BetterQuesting
 * is an optional mod in an optional pack; a hard reference would mean shipping its jar
 * to build this one and would turn its absence into a missing class. The cost is that
 * a version which moves this API is a caught exception and a log line rather than a
 * compile error, so that is exactly what it is: one failure marks the whole
 * integration unavailable for the session and the quest book keeps its own theme.
 *
 * <p>Applied lazily, from {@code GuiOpenEvent}, because the themes a resource pack
 * provides are loaded at a point in start-up we would otherwise have to guess at, and
 * because a resource reload can put the selection back. A quest book screen opening is
 * both the moment it matters and the moment it is certain to be possible.
 */
public final class QuestBookTheme {

    private static final String MOD_ID = "betterquesting";
    /** Package prefix of every screen the quest book opens. */
    public static final String SCREEN_PREFIX = "betterquesting.";

    /** Our theme's id, and the name shown in the quest book's own theme list. */
    private static final ResourceLocation ID = new ResourceLocation(UkyUI.MODID, "uky");
    /**
     * Fallen back to for anything this theme does not name — the icon sheet, the
     * stipple patterns behind quest lines, and whatever a later version adds.
     */
    private static final ResourceLocation PARENT = new ResourceLocation(MOD_ID, "dark");
    /** Where the painted widget sheet is registered with the texture manager. */
    private static final ResourceLocation ATLAS =
            new ResourceLocation(UkyUI.MODID, "textures/gui/questbook_theme");

    /** Set once something is missing or has moved; nothing is retried afterwards. */
    private static boolean unavailable;
    /** The registered theme object, or null before the first successful build. */
    private static Object theme;
    private static DynamicTexture atlas;
    /** Cleared by a palette edit, which is what makes the sheet be painted again. */
    private static boolean current;

    private QuestBookTheme() {
    }

    /** Whether BetterQuesting is in the pack at all. */
    public static boolean isLoaded() {
        return Loader.isModLoaded(MOD_ID);
    }

    /**
     * Forgets the painted sheet and the palette, so the next quest book screen is
     * built from the colours as they now stand.
     */
    public static void invalidate() {
        current = false;
    }

    /**
     * Registers the theme if it is not registered, repaints it if the palette has
     * changed, and selects it if the config asks for that.
     *
     * <p>Never throws. It is called from a GUI event handler, and a quest book that
     * refuses to open would be a far worse outcome than one that opens in its own
     * colours.
     */
    public static void ensureApplied() {
        if (unavailable || !isLoaded()) {
            return;
        }
        try {
            if (theme == null || !current) {
                boolean first = theme == null;
                build();
                current = true;
                if (first) {
                    UkyUI.LOGGER.info("BetterQuesting theme \"{}\" registered as {}",
                            UkyUI.NAME, ID);
                }
            }
            if (UiConfig.restyleQuestBook) {
                select();
            }
        } catch (Throwable t) {
            unavailable = true;
            UkyUI.LOGGER.warn("Could not theme BetterQuesting; it keeps its own look", t);
        }
    }

    // ----------------------------------------------------------------- build --

    private static void build() throws Exception {
        paintAtlas();

        Class<?> themeClass = Class.forName("betterquesting.client.themes.ResourceTheme");
        if (theme == null) {
            Constructor<?> constructor = themeClass.getConstructor(
                    ResourceLocation.class, ResourceLocation.class, String.class);
            // (parent, id, name) — the parent comes first, which is the opposite of
            // how the theme files spell it and of what the field order suggests. Two
            // arguments of the same type and no compiler to catch it: getting this
            // backwards registered the theme under its own parent's id, and
            // BetterQuesting rejected it as a duplicate of the theme it ships.
            Object built = constructor.newInstance(PARENT, ID, UkyUI.NAME);
            Class<?> themeInterface =
                    Class.forName("betterquesting.api2.client.gui.themes.IGuiTheme");
            Object registry = registry();
            registry.getClass().getMethod("registerTheme", themeInterface)
                    .invoke(registry, built);
            // Only now: a theme that failed to register must not be left behind as one
            // that did, or the retry after a palette edit fills in a theme nothing can
            // reach and reports success.
            theme = built;
        }

        fillTextures(themeClass);
        fillColors(themeClass);
    }

    /**
     * Paints the widget sheet and puts it where the texture manager can find it.
     *
     * The same {@link DynamicTexture} is kept and its pixels replaced rather than a
     * new one registered per palette edit: a texture registered under a location is
     * never released by the texture manager, so the second way leaks one 256x256
     * texture for every colour anyone tries out in the settings screen.
     */
    private static void paintAtlas() {
        BufferedImage image = QuestBookAtlas.paint();
        if (atlas == null) {
            atlas = new DynamicTexture(image);
            Minecraft.getMinecraft().getTextureManager().loadTexture(ATLAS, atlas);
            return;
        }
        image.getRGB(0, 0, QuestBookAtlas.SIZE, QuestBookAtlas.SIZE,
                atlas.getTextureData(), 0, QuestBookAtlas.SIZE);
        atlas.updateDynamicTexture();
    }

    private static void fillTextures(Class<?> themeClass) throws Exception {
        Class<?> textureInterface =
                Class.forName("betterquesting.api2.client.gui.resources.textures.IGuiTexture");
        Class<?> slicedClass =
                Class.forName("betterquesting.api2.client.gui.resources.textures.SlicedTexture");
        Class<?> rectInterface = Class.forName("betterquesting.api2.client.gui.misc.IGuiRect");
        Class<?> paddingClass = Class.forName("betterquesting.api2.client.gui.misc.GuiPadding");
        Constructor<?> rect = Class.forName("betterquesting.api2.client.gui.misc.GuiRectangle")
                .getConstructor(int.class, int.class, int.class, int.class);
        Constructor<?> padding = paddingClass
                .getConstructor(int.class, int.class, int.class, int.class);
        Constructor<?> sliced = slicedClass.getConstructor(
                ResourceLocation.class, rectInterface, paddingClass);

        // Corners at their painted size, edges and middle stretched. The alternative,
        // tiling, would repeat a one-pixel border into a dotted line at some sizes.
        Class<?> sliceModeClass = Class.forName(
                "betterquesting.api2.client.gui.resources.textures.SlicedTexture$SliceMode");
        Object stretch = enumValue(sliceModeClass, "SLICED_STRETCH");
        Method setSliceMode = slicedClass.getMethod("setSliceMode", sliceModeClass);
        Method setTexture = themeClass.getMethod("setTexture",
                ResourceLocation.class, textureInterface);

        List<QuestBookAtlas.Slot> slots = QuestBookAtlas.slots();
        for (int i = 0; i < slots.size(); i++) {
            QuestBookAtlas.Slot slot = slots.get(i);
            ResourceLocation key = presetKey(
                    "betterquesting.api2.client.gui.themes.presets.PresetTexture", slot.preset);
            if (key == null) {
                continue;
            }
            Object texture = sliced.newInstance(ATLAS,
                    rect.newInstance(Integer.valueOf(slot.x), Integer.valueOf(slot.y),
                            Integer.valueOf(slot.width), Integer.valueOf(slot.height)),
                    padding.newInstance(Integer.valueOf(slot.padLeft), Integer.valueOf(slot.padTop),
                            Integer.valueOf(slot.padRight), Integer.valueOf(slot.padBottom)));
            setSliceMode.invoke(texture, stretch);
            setTexture.invoke(theme, key, texture);
        }
    }

    /**
     * The colours the quest book draws text and quest lines in.
     *
     * The quest states are the part worth reading twice. A quest map says five things
     * with colour — locked, available, in progress, done, repeatable — and the usual
     * red/yellow/green does say them, loudly, in a palette that has none of those.
     * These say the same five things along the one axis this interface has, and gold
     * goes to <em>available</em> rather than to complete: on a map anyone has played
     * for a week most of the nodes are finished, and a theme that lights those up
     * brightest spends its loudest colour on the one thing nobody is looking for.
     * Finished quests are filled in and dim; the thing you can start right now is the
     * only gold on the screen.
     */
    private static void fillColors(Class<?> themeClass) throws Exception {
        Class<?> colorInterface =
                Class.forName("betterquesting.api2.client.gui.resources.colors.IGuiColor");
        Method setColor = themeClass.getMethod("setColor",
                ResourceLocation.class, colorInterface);
        Constructor<?> staticColor = Class.forName(
                "betterquesting.api2.client.gui.resources.colors.GuiColorStatic")
                .getConstructor(int.class);

        color(setColor, staticColor, "TEXT_HEADER", Theme.accent);
        color(setColor, staticColor, "TEXT_MAIN", Theme.text);
        color(setColor, staticColor, "TEXT_AUX_0", Theme.textDim);
        color(setColor, staticColor, "TEXT_AUX_1", Draw.withAlpha(Theme.textDim, 0.75F));
        color(setColor, staticColor, "TEXT_HIGHLIGHT", Theme.accent);
        color(setColor, staticColor, "TEXT_WATERMARK", Draw.withAlpha(Theme.textDim, 0.35F));
        color(setColor, staticColor, "ITEM_HIGHLIGHT", Draw.withAlpha(Theme.accent, 0.28F));
        color(setColor, staticColor, "UPDATE_NOTICE", Theme.accent);

        color(setColor, staticColor, "GUI_DIVIDER", Draw.withAlpha(Theme.text, 0.18F));
        color(setColor, staticColor, "GRID_MAJOR", Draw.withAlpha(Theme.text, 0.09F));
        color(setColor, staticColor, "GRID_MINOR", Draw.withAlpha(Theme.text, 0.05F));

        color(setColor, staticColor, "BTN_DISABLED", Theme.textDisabled);
        color(setColor, staticColor, "BTN_IDLE", Theme.text);
        color(setColor, staticColor, "BTN_HOVER", Theme.accent);

        color(setColor, staticColor, "QUEST_LINE_LOCKED", Draw.withAlpha(Theme.textDim, 0.35F));
        color(setColor, staticColor, "QUEST_LINE_UNLOCKED", Draw.withAlpha(Theme.accent, 0.85F));
        color(setColor, staticColor, "QUEST_LINE_PENDING", Draw.withAlpha(Theme.accentAlt, 0.90F));
        color(setColor, staticColor, "QUEST_LINE_COMPLETE", Draw.withAlpha(Theme.text, 0.55F));
        color(setColor, staticColor, "QUEST_LINE_REPEATABLE", Draw.withAlpha(Theme.accentAlt, 0.85F));
        color(setColor, staticColor, "QUEST_LINE_IMPLICIT_MIXIN", Draw.withAlpha(Theme.textDim, 0.55F));

        color(setColor, staticColor, "QUEST_ICON_LOCKED", Draw.withAlpha(Theme.textDim, 0.55F));
        color(setColor, staticColor, "QUEST_ICON_UNLOCKED", Theme.accent);
        color(setColor, staticColor, "QUEST_ICON_PENDING", Theme.accentAlt);
        color(setColor, staticColor, "QUEST_ICON_COMPLETE", Draw.withAlpha(Theme.text, 0.60F));
        color(setColor, staticColor, "QUEST_ICON_REPEATABLE",
                Draw.mix(Theme.accentAlt, Theme.accent, 0.5F));
    }

    private static void color(Method setColor, Constructor<?> staticColor,
                              String preset, int argb) throws Exception {
        ResourceLocation key = presetKey(
                "betterquesting.api2.client.gui.themes.presets.PresetColor", preset);
        if (key != null) {
            setColor.invoke(theme, key, staticColor.newInstance(Integer.valueOf(argb)));
        }
    }

    // --------------------------------------------------------------- selection --

    /**
     * Makes ours the active theme — once, and then never again.
     *
     * <p><b>It used to do this on every quest book screen opening</b>, and that made the
     * theme impossible to leave: the quest book has a Themes screen, picking anything
     * else in it worked exactly until the book was next opened, and then ours was back.
     * A setting that cannot be changed from inside the interface it belongs to is not a
     * default, it is a lock.
     *
     * <p>So selecting it now also clears the switch that asked for it, which is the same
     * shape {@code showSettingsOnFirstRun} has and for the same reason: one key that
     * turns itself off, rather than a preference plus a hidden "already done" flag that
     * nobody reading the config file could guess the meaning of. After this has run once,
     * BetterQuesting remembers the choice in its own config and whatever the player picks
     * afterwards is left alone — set it back to true to hand the book our theme again.
     *
     * <p>The switch is cleared on both paths, including the one where ours is already
     * active. Clearing it only when something was actually changed would leave it set on
     * a fresh install whose first book opened on our theme anyway, and the first time the
     * player then chose another one it would be overridden — which is the whole bug,
     * arrived at one step later.
     */
    private static void select() throws Exception {
        Object registry = registry();
        Object active = registry.getClass().getMethod("getCurrentTheme").invoke(registry);
        if (active != null) {
            // Asked of the interface rather than of the object's own class: a theme
            // from another mod is free to be a package-private class, and a Method
            // taken off one of those cannot be invoked.
            Object id = Class.forName("betterquesting.api2.client.gui.themes.IGuiTheme")
                    .getMethod("getID").invoke(active);
            if (ID.equals(id)) {
                UiConfig.setRestyleQuestBook(false);
                return;
            }
        }
        registry.getClass().getMethod("setTheme", ResourceLocation.class)
                .invoke(registry, ID);
        UiConfig.setRestyleQuestBook(false);
        UkyUI.LOGGER.info("BetterQuesting theme set to {}; the quest book's own Themes"
                + " screen now decides, and mods.restyleQuestBook has turned itself off", ID);
    }

    // ------------------------------------------------------------- reflection --

    private static Object registry() throws Exception {
        return Class.forName("betterquesting.client.themes.ThemeRegistry")
                .getField("INSTANCE").get(null);
    }

    /**
     * The resource name BetterQuesting files a preset widget or colour under.
     *
     * Asked of the enum constant rather than spelled out here, so a theme key that is
     * renamed upstream follows along, and one that is removed simply drops out of our
     * table instead of being written under a name nothing reads.
     *
     * @return the key, or null if this version of BetterQuesting has no such preset
     */
    private static ResourceLocation presetKey(String enumClass, String constant) throws Exception {
        Class<?> type = Class.forName(enumClass);
        Object value;
        try {
            value = enumValue(type, constant);
        } catch (IllegalArgumentException absent) {
            return null;
        }
        return (ResourceLocation) type.getMethod("getKey").invoke(value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> type, String constant) {
        return Enum.valueOf((Class) type, constant);
    }
}
