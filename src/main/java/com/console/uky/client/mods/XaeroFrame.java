package com.console.uky.client.mods;

import com.console.uky.UkyUI;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Theme;
import com.console.uky.config.UiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Xaero's minimap, given this mod's frame instead of its own.
 *
 * <p>The minimap is the one piece of interface that is on screen the entire time
 * somebody is playing, and out of the box it is a beige double border with a drop
 * shadow — the look Xaero shipped in 2015 and has kept for compatibility. Beside a HUD
 * drawn in one dark palette it is the thing the eye goes to first, and for no reason:
 * nothing about it is information, it is all chrome.
 *
 * <p>So the chrome is replaced. What the map itself draws — the terrain, the entities,
 * the waypoints, the coordinates under it — is untouched, exactly as with Waila: this
 * is the box around it and nothing else.
 *
 * <h2>Their frame goes off through their own setting</h2>
 *
 * <p>Not through a mixin. Xaero draws the square frame with vanilla's own
 * {@code GuiIngame.drawTexturedModalRect}, and this project has no refmap — a mixin
 * naming a vanilla method works in a development workspace and fails in a built jar
 * (see {@code MixinGuiScreenTooltip} for the same reasoning). But the mod already has
 * a switch for exactly this: {@code ModSettings.minimapFrame}, whose fourth option is
 * "off". Setting it is a supported thing to do, survives their updates, and is visible
 * to the player in Xaero's own settings screen rather than being a mystery.
 *
 * <p>The value it had is remembered and put back the moment this feature is turned
 * off, so nothing is taken from anyone permanently.
 *
 * <h2>Where the box is</h2>
 *
 * <p>From Xaero, not guessed, and from the same two places their own
 * {@code InterfaceRenderer} reads it: the corner off the {@code Interface}, the size
 * off the {@code InterfaceInstance} the session holds for it. That renderer builds a
 * plain {@code ScaledResolution} and uses these against it, which is what makes them
 * safe to use from an ordinary HUD overlay — and it means moving the map, resizing it
 * or changing the GUI scale moves the frame with it, with no constant on this side.
 */
public final class XaeroFrame {

    /**
     * The option index that means "no frame" in their own settings.
     *
     * Their list is default, thick, thin, off. The index is written out here because
     * the array holds translation keys rather than anything nameable; a version that
     * inserts a style before "off" would need this line changed, and the cost of being
     * wrong is their frame staying on under ours.
     */
    private static final int FRAME_OFF = 3;

    /**
     * The margin their interface reserves for a frame, in minimap units.
     *
     * Straight out of {@code MinimapInterfaceInstance.getInterfaceWidth}, which is
     * {@code mapSize / 2 + 18}: eighteen units of frame around a map, nine a side.
     */
    private static final int FRAME_MARGIN = 18;

    private static boolean resolved;
    private static boolean usable;

    private static Field instanceField;
    private static Method getSettings;
    private static Method getInterfaces;
    private static Method getMinimapInterface;
    private static Field frameOption;
    private static Field shapeOption;
    private static Method interfaceX;
    private static Method interfaceY;
    private static Method currentSession;
    private static Method sessionInstances;
    private static Method instanceWidth;
    private static Method instanceHeight;
    private static Method minimapScale;
    private static Method interfaceOption;
    private static Method settingsBoolean;

    /** What their frame setting was before this took it off, or -1. */
    private static int theirFrame = -1;

    private XaeroFrame() {
    }

    /**
     * Draws the frame, and takes theirs off the first time it does.
     *
     * Called from the HUD's own overlay pass rather than from inside Xaero's render:
     * this way it is drawn in the plain scaled interface space every other overlay in
     * this mod uses, with no assumption about the projection Xaero happens to have set
     * up at the moment it finishes its own drawing.
     */
    public static void draw(Minecraft mc, ScaledResolution resolution) {
        if (!UiConfig.restyleXaeroFrame) {
            // Turned off since the last frame: give them their own frame back.
            restore();
            return;
        }
        if (!resolve() || mc.theWorld == null || mc.gameSettings.hideGUI) {
            return;
        }
        try {
            Object mod = instanceField.get(null);
            if (mod == null) {
                return;
            }
            Object settings = getSettings.invoke(mod);
            Object minimap = getMinimapInterface.invoke(getInterfaces.invoke(mod));
            if (settings == null || minimap == null) {
                return;
            }
            // Their own test for whether this interface is shown at all, asked the same
            // way their renderer asks it: every interface carries the option that
            // switches it, and the minimap's is the one behind the key that turns the
            // map off. Without this the frame stayed on screen around nothing.
            Object option = interfaceOption.invoke(minimap);
            if (option == null || !((Boolean) settingsBoolean.invoke(settings, option))
                    .booleanValue()) {
                return;
            }
            hideTheirs(settings);

            // Exactly what their own InterfaceRenderer reads, and in the same order:
            // the corner off the Interface, the size off the InterfaceInstance for the
            // session. Both are already in the scaled interface space this overlay is
            // drawn in — the renderer builds a ScaledResolution and uses these against
            // it — so nothing here has to know how big a minimap pixel is.
            Object session = currentSession.invoke(null);
            if (session == null) {
                return;
            }
            Object instance = ((java.util.Map<?, ?>) sessionInstances.invoke(session))
                    .get(minimap);
            if (instance == null) {
                return;
            }
            Double scale = Double.valueOf(resolution.getScaleFactor());
            int x = ((Integer) interfaceX.invoke(minimap)).intValue();
            int y = ((Integer) interfaceY.invoke(minimap)).intValue();

            // The interface is wider than the map it holds, and by a fixed amount:
            // their own getInterfaceWidth is mapSize / 2 + 18, where the 18 is the
            // margin their frame is drawn into — nine units a side — and everything is
            // then taken from minimap units to interface ones by minimapScale / scale.
            //
            // That is the whole of what was wrong before: the box came back as the
            // interface, the corner as the map, and a frame drawn from the two was a
            // frame too big and offset down and right — which is exactly what it looked
            // like. Taking the margin back off gives the map itself.
            float unit = ((Float) minimapScale.invoke(settings, scale)).floatValue()
                    / scale.floatValue();
            int margin = Math.round(FRAME_MARGIN * unit);
            int w = ((Integer) instanceWidth.invoke(instance, scale)).intValue() - margin;
            int h = ((Integer) instanceHeight.invoke(instance, scale)).intValue() - margin;
            // And the corner is the interface's, not the map's: the eighteen units of
            // margin are split evenly, so the map starts half of one in from it. Taking
            // the width off but not moving the corner is what left the frame sitting up
            // and to the left of the map by exactly that half.
            x += margin / 2;
            y += margin / 2;
            if (w <= 0 || h <= 0) {
                return;
            }
            boolean round = ((Integer) shapeOption.get(settings)).intValue() != 0;
            drawFrame(x, y, w, h, round);
        } catch (Throwable t) {
            usable = false;
            UkyUI.LOGGER.warn("Could not draw the minimap frame; Xaero keeps its own", t);
        }
    }

    /**
     * The frame itself, in this mod's own language rather than a box.
     *
     * <p>A border alone was the wrong answer twice over: it read as a window somebody
     * had dropped on the HUD, and being a closed rectangle it had to line up with the
     * map to the pixel or look broken. What every other panel in this mod is marked
     * with is not an outline — it is a rail down one edge and the corners picked out —
     * so that is what the map gets.
     *
     * <p>Four corner brackets, the gold rail down the left, a hairline to close the
     * shape at very low opacity, and one slanted tick at the top right: the same lean
     * the achievement panel is cut with, which is what says this belongs to the same
     * interface as everything else. All of it outside the map, because every pixel
     * inside one is information.
     */
    private static void drawFrame(int x, int y, int w, int h, boolean round) {
        float x1 = x - 2.0F;
        float y1 = y - 2.0F;
        float x2 = x + w + 2.0F;
        float y2 = y + h + 2.0F;

        if (round) {
            drawRoundFrame(x + w / 2.0F, y + h / 2.0F, Math.min(w, h) / 2.0F + 2.0F);
            return;
        }

        // A seat under everything, so the marks sit on the HUD rather than float over
        // whatever the map happens to be showing at its edge.
        Draw.border(x1 - 1.0F, y1 - 1.0F, x2 + 1.0F, y2 + 1.0F, 1.0F,
                Draw.withAlpha(Theme.background, 0.8F));
        Draw.border(x1, y1, x2, y2, 1.0F, Draw.withAlpha(Theme.text, 0.10F));

        // The rail: full height, brightest at the top, the same gradient the panels use.
        Draw.gradientV(x1 - 1.0F, y1, x1 + 1.0F, y2,
                Draw.withAlpha(Theme.accent, 0.95F), Draw.withAlpha(Theme.accent, 0.30F));

        // Corners, drawn as brackets rather than joined up. The eye reads four marks as
        // a frame perfectly well, and unlike a closed box they do not have to agree with
        // the map's edge to the pixel.
        float arm = Math.max(6.0F, Math.min(w, h) * 0.14F);
        int bright = Draw.withAlpha(Theme.accent, 0.9F);
        bracket(x1, y1, arm, 1.0F, 1.0F, bright);
        bracket(x2, y1, arm, -1.0F, 1.0F, bright);
        bracket(x1, y2, arm, 1.0F, -1.0F, bright);
        bracket(x2, y2, arm, -1.0F, -1.0F, bright);

        // One tick with the lean this mod cuts its HUD panels with, so the frame is
        // recognisably ours at a glance and not merely dark.
        Draw.slant(x2 - arm - 3.0F, y1 - 4.0F, x2 - 3.0F, y1 - 2.0F, 3.0F,
                Draw.withAlpha(Theme.accentAlt, 0.85F));
    }

    /** One corner: two arms meeting at ({@code x}, {@code y}), pointing inwards. */
    private static void bracket(float x, float y, float arm, float dx, float dy,
                                int colour) {
        Draw.rect(x, y, x + arm * dx, y + 1.0F * dy, colour);
        Draw.rect(x, y, x + 1.0F * dx, y + arm * dy, colour);
    }

    /**
     * The round map's frame: a ring, and the corners' job done by four ticks.
     *
     * The ticks sit at the diagonals rather than at the compass points, which are
     * already taken — Xaero draws its own N/E/S/W letters there, and putting a mark
     * under one would read as part of it.
     */
    private static void drawRoundFrame(float cx, float cy, float radius) {
        Draw.ring(cx, cy, radius + 1.0F, 1.0F, Draw.withAlpha(Theme.background, 0.8F));
        Draw.ring(cx, cy, radius, 1.0F, Draw.withAlpha(Theme.accent, 0.55F));

        float diagonal = 0.70710677F;
        float inner = radius + 1.0F;
        float outer = radius + 4.0F;
        for (int i = 0; i < 4; i++) {
            float ux = (i == 0 || i == 3) ? diagonal : -diagonal;
            float uy = (i < 2) ? diagonal : -diagonal;
            Draw.line(cx + ux * inner, cy + uy * inner,
                    cx + ux * outer, cy + uy * outer, 1.6F,
                    Draw.withAlpha(Theme.accent, 0.9F));
        }
    }

    /** Takes their frame off, once, remembering what it was. */
    private static void hideTheirs(Object settings) throws Exception {
        int current = ((Integer) frameOption.get(settings)).intValue();
        if (current == FRAME_OFF) {
            return;
        }
        theirFrame = current;
        frameOption.setInt(settings, FRAME_OFF);
        UkyUI.LOGGER.info("Xaero's minimap frame is drawn by this mod; its own is off");
    }

    /**
     * Gives their frame back.
     *
     * Called from the draw the moment the config switch goes off, so turning this off
     * in the settings puts the map back the way Xaero draws it without a restart.
     */
    public static void restore() {
        if (theirFrame < 0 || !usable) {
            return;
        }
        try {
            Object mod = instanceField.get(null);
            if (mod != null) {
                frameOption.setInt(getSettings.invoke(mod), theirFrame);
            }
        } catch (Throwable t) {
            // Nothing to do: the value is theirs and the next launch reads it from
            // their own config file anyway.
            UkyUI.LOGGER.warn("Could not hand the minimap frame back", t);
        }
        theirFrame = -1;
    }

    /**
     * Looks up everything this needs, once.
     *
     * All of it is Xaero's own shape, so a version that moves any of it costs the frame
     * and nothing else — the map keeps drawing, with whichever frame their setting says.
     */
    private static boolean resolve() {
        if (resolved) {
            return usable;
        }
        resolved = true;
        try {
            // The class, not Loader.isModLoaded. Their mod id is "XaeroMinimap" with
            // the capitals, and FML's lookup is case sensitive — asking for the
            // lowercase spelling answered "not installed" on a game that had it, which
            // is exactly what happened here. A class that loads is the same question
            // with no spelling to get wrong.
            Class<?> mod = Class.forName("xaero.minimap.XaeroMinimap");
            instanceField = mod.getField("instance");
            getSettings = mod.getMethod("getSettings");
            getInterfaces = mod.getMethod("getInterfaces");
            getMinimapInterface = Class.forName("xaero.common.interfaces.InterfaceManager")
                    .getMethod("getMinimapInterface");

            Class<?> settings = Class.forName("xaero.common.settings.ModSettings");
            minimapScale = settings.getMethod("getMinimapScale", double.class);
            frameOption = settings.getField("minimapFrame");
            shapeOption = settings.getField("minimapShape");

            Class<?> face = Class.forName("xaero.common.interfaces.Interface");
            interfaceX = face.getMethod("getX");
            interfaceY = face.getMethod("getY");
            interfaceOption = face.getMethod("getOption");
            settingsBoolean = Class.forName("xaero.common.settings.ModSettings")
                    .getMethod("getBooleanValue",
                            Class.forName("xaero.common.settings.ModOptions"));

            Class<?> session = Class.forName("xaero.common.XaeroMinimapSession");
            currentSession = session.getMethod("getCurrentSession");
            sessionInstances = session.getMethod("getInterfaceInstances");
            Class<?> instance = Class.forName("xaero.common.interfaces.InterfaceInstance");
            instanceWidth = instance.getMethod("getW", double.class);
            instanceHeight = instance.getMethod("getH", double.class);

            usable = true;
        } catch (Throwable t) {
            usable = false;
            UkyUI.LOGGER.info("Xaero's minimap keeps its own frame: {}", t.toString());
        }
        return usable;
    }
}
