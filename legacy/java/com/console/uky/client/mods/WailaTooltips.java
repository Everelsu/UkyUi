package com.console.uky.client.mods;

import com.console.uky.UkyUI;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Theme;

import java.awt.Dimension;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * The pieces Waila draws inside its tooltip that are pictures rather than words.
 *
 * Waila renders most of a tooltip as text, but a provider can ask for a widget
 * instead by writing a tagged line — a furnace asks for {@code waila.progress} and
 * hands it a number and a maximum, and Waila looks that name up in a registry of
 * {@code IWailaTooltipRenderer}s and lets the winner draw into a reserved rectangle.
 * That registry is a plain map, which makes it the whole extension point: put
 * something else under the same name and every mod that ever asked for a progress bar
 * gets ours, with no cooperation from any of them.
 *
 * <p>The one thing that must not change is the size. Waila measures the tooltip by
 * asking each widget how big it is and lays the text around the answer, so ours
 * reports the same 32x16 Waila's does. What goes inside that rectangle is free.
 *
 * <p>Implemented as a {@link Proxy} rather than a class implementing the interface,
 * because the interface only exists when Waila does and this mod is compiled without
 * it. Registration goes into the map directly for a duller reason: Waila's own
 * {@code registerTooltipRenderer} refuses a name that is already taken, and the name
 * is taken by definition — by the renderer we are here to replace.
 */
public final class WailaTooltips {

    /** Waila's name for the progress widget. */
    private static final String PROGRESS = "waila.progress";
    /** Waila's own size for it, which the surrounding layout is built around. */
    private static final int WIDTH = 32;
    private static final int HEIGHT = 16;

    /** How long a highlight takes to travel the length of a bar that is filling. */
    private static final float SWEEP_SECONDS = 1.4F;

    private static boolean installed;

    private WailaTooltips() {
    }

    /**
     * Puts our widgets in Waila's registry, once.
     *
     * Called from the tooltip render rather than from init: by the time a tooltip is
     * being drawn every mod in the pack has finished registering, which removes the
     * question of load order entirely. The first tooltip of the session is drawn with
     * Waila's own bar and every one after it with ours — the renderers are looked up
     * per tooltip, so the swap costs one frame and nothing else.
     */
    static void install() {
        if (installed) {
            return;
        }
        installed = true;
        try {
            Class<?> registrar = Class.forName("mcp.mobius.waila.api.impl.ModuleRegistrar");
            Object instance = registrar.getMethod("instance").invoke(null);
            @SuppressWarnings("unchecked")
            Map<String, Object> renderers = (Map<String, Object>)
                    registrar.getField("tooltipRenderers").get(instance);

            Class<?> type = Class.forName("mcp.mobius.waila.api.IWailaTooltipRenderer");
            Object bar = Proxy.newProxyInstance(type.getClassLoader(),
                    new Class<?>[]{type}, new ProgressBar());
            renderers.put(PROGRESS, bar);
        } catch (Throwable t) {
            // The panel is the visible half of this and it is already drawn; a Waila
            // that keeps its own progress bar is a smaller loss than a crash in the
            // middle of the HUD.
            UkyUI.LOGGER.warn("Could not replace Waila's progress bar", t);
        }
    }

    /** Dispatch for the proxy; the interface has exactly two methods. */
    private static final class ProgressBar implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("getSize".equals(name)) {
                return new Dimension(WIDTH, HEIGHT);
            }
            if ("draw".equals(name)) {
                draw((String[]) args[0]);
                return null;
            }
            // Object's own three, which a proxy is asked for like any other object.
            if ("equals".equals(name)) {
                return Boolean.valueOf(proxy == args[0]);
            }
            if ("hashCode".equals(name)) {
                return Integer.valueOf(System.identityHashCode(proxy));
            }
            return UkyUI.NAME + " progress bar";
        }
    }

    /**
     * The bar itself, drawn in the rectangle's own coordinates.
     *
     * Waila translates to the widget's corner before calling this, so (0,0) here is
     * the top-left of the 32x16 it reserved.
     */
    private static void draw(String[] params) {
        float fraction = fraction(params);

        // Inset by two so the bar reads as sitting in the panel rather than butting
        // against the text on either side of it.
        float x1 = 2.0F;
        float x2 = WIDTH - 2.0F;
        float y1 = HEIGHT / 2.0F - 2.5F;
        float y2 = HEIGHT / 2.0F + 2.5F;

        Draw.rect(x1, y1, x2, y2, Draw.withAlpha(Theme.background, 0.85F));
        Draw.border(x1, y1, x2, y2, 1.0F, Draw.withAlpha(Theme.text, 0.16F));

        float inner1 = x1 + 1.0F;
        float inner2 = x2 - 1.0F;
        float end = inner1 + (inner2 - inner1) * fraction;
        if (end <= inner1) {
            return;
        }

        // Left to right, and dim to bright with it: the far end of a bar that is
        // nearly done is the brightest thing in the tooltip.
        Draw.gradientH(inner1, y1 + 1.0F, end, y2 - 1.0F,
                Draw.withAlpha(Theme.accentAlt, 0.95F), Draw.withAlpha(Theme.accent, 0.95F));

        if (fraction < 1.0F) {
            // A highlight running the length of the filled part, so a bar that is
            // working looks like it is working even while the number barely moves.
            float phase = (System.nanoTime() % (long) (SWEEP_SECONDS * 1_000_000_000L))
                    / (SWEEP_SECONDS * 1_000_000_000.0F);
            float head = inner1 + (end - inner1) * phase;
            float half = 3.0F;
            Draw.gradientH(Math.max(inner1, head - half), y1 + 1.0F, head, y2 - 1.0F,
                    Draw.withAlpha(Theme.text, 0.0F), Draw.withAlpha(Theme.text, 0.30F));
            Draw.gradientH(head, y1 + 1.0F, Math.min(end, head + half), y2 - 1.0F,
                    Draw.withAlpha(Theme.text, 0.30F), Draw.withAlpha(Theme.text, 0.0F));

            // The leading edge of the fill, kept bright so the eye has an exact
            // reading of where it has got to.
            Draw.rect(end - 1.0F, y1, end, y2, Draw.withAlpha(Theme.accent, 0.95F));
        }
    }

    /**
     * How full the bar is, from the two numbers the provider handed over.
     *
     * Defensive about every one of them. These come from whichever mod owns the block
     * being looked at, over the network in some cases, and a division by a zero
     * maximum inside the HUD render would take the frame down.
     */
    private static float fraction(String[] params) {
        if (params == null || params.length < 2) {
            return 0.0F;
        }
        try {
            float current = Integer.parseInt(params[0].trim());
            float max = Integer.parseInt(params[1].trim());
            if (max <= 0.0F) {
                return 0.0F;
            }
            float fraction = current / max;
            return fraction < 0.0F ? 0.0F : (fraction > 1.0F ? 1.0F : fraction);
        } catch (NumberFormatException malformed) {
            return 0.0F;
        }
    }
}
