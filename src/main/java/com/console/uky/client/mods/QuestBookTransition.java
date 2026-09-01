package com.console.uky.client.mods;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import com.console.uky.config.UiConfig;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.lwjgl.opengl.GL11;

/**
 * The backdrop the quest book opens against, and the beat it takes to get there.
 *
 * BetterQuesting draws its panels straight onto the live world — no scrim, no
 * transition, the book is simply there on one frame and gone on another. That reads
 * as a window that appeared by accident, and against a bright landscape a dark theme
 * loses half its contrast to whatever happens to be behind it. This puts a scrim
 * under the book and gives it the same two-tenths of a second every other screen in
 * this mod takes to arrive.
 *
 * <p>Nothing here touches BetterQuesting. The scrim is drawn before its
 * {@code drawScreen} and the book is drawn inside a scale that eases out to 1, both
 * through Forge's own draw events, so the book is unmodified and unaware.
 *
 * <p>The way out is not symmetrical, and cannot be: once the screen closes there is
 * no screen left to animate, and there is nothing to draw the book <em>from</em> —
 * the only way to fade the panels themselves would be to photograph the framebuffer
 * every frame the book is open against the one frame it is closed on, which is a real
 * cost paid continuously for a fifth of a second of polish. So what continues past
 * the close is the scrim, lifting off the world from a render tick after the screen
 * is gone — the same trick, and the same reason for it, as {@code WorldEntryFade}.
 */
public final class QuestBookTransition {

    private static final float OPEN_SECONDS = 0.20F;
    private static final float CLOSE_SECONDS = 0.18F;
    /**
     * Scale the book grows from.
     *
     * Deliberately close to 1. BetterQuesting clips its scrolling areas with
     * {@code glScissor}, which works in window pixels and so does not follow a scaled
     * modelview — anything more than a few percent and the clip visibly disagrees with
     * the content for as long as the animation runs.
     */
    private static final float FROM_SCALE = 0.965F;

    private static boolean open;
    private static long openedNanos;

    /** Seconds left of the scrim lifting after the book has closed; 0 when idle. */
    private static float closing;
    /** How far the scrim had come in when the book closed, so it lifts from there. */
    private static float closingFrom;
    private static long lastFrameNanos;

    /**
     * Whether {@link #drawPre} pushed a matrix this frame.
     *
     * A higher-priority handler elsewhere is allowed to cancel the pre-draw event, and
     * a cancelled event is not delivered to us at all — while the post-draw event is
     * posted either way. Popping a matrix nobody pushed would unbalance the stack for
     * everything drawn afterwards, which is a corrupted screen rather than a missing
     * animation.
     */
    private static boolean pushed;

    private QuestBookTransition() {
    }

    private static boolean enabled() {
        return UiConfig.questBookTransition;
    }

    /** How far the world goes down behind the book, from the config. */
    private static float dim() {
        return (float) UiConfig.questBookDim;
    }

    private static boolean isBook(GuiScreen screen) {
        return screen != null
                && screen.getClass().getName().startsWith(QuestBookTheme.SCREEN_PREFIX);
    }

    /**
     * Told about every screen change, so it can tell opening from navigating.
     *
     * Moving between the book's own screens — the quest list to a quest to its rewards
     * — is not an opening, and replaying the animation on each of those would make the
     * book feel like it was being reopened every time it was used.
     */
    public static void screenChanged(GuiScreen from, GuiScreen to) {
        boolean was = isBook(from);
        boolean now = isBook(to);
        if (now == was) {
            return;
        }
        if (now) {
            open = true;
            openedNanos = System.nanoTime();
            closing = 0.0F;
        } else {
            closingFrom = progress();
            open = false;
            closing = CLOSE_SECONDS;
            lastFrameNanos = System.nanoTime();
        }
    }

    /** How far in the scrim is, 0 to 1. */
    private static float progress() {
        float elapsed = (System.nanoTime() - openedNanos) / 1_000_000_000.0F;
        return Ease.outCubic(elapsed >= OPEN_SECONDS ? 1.0F : elapsed / OPEN_SECONDS);
    }

    // ------------------------------------------------------------------ draw --

    private static void drawPre(GuiScreen gui) {
        pushed = false;
        if (!enabled() || !open || !isBook(gui)) {
            return;
        }
        float p = progress();
        Draw.rect(0, 0, gui.width, gui.height,
                Draw.withAlpha(Theme.background, dim() * p));

        float scale = FROM_SCALE + (1.0F - FROM_SCALE) * p;
        Draw.pushScale(gui.width / 2.0F, gui.height / 2.0F, scale);
        pushed = true;
    }

    private static void drawPost() {
        if (pushed) {
            Draw.popScale();
            pushed = false;
        }
    }

    /**
     * The scrim lifting after the book is gone.
     *
     * Its own projection, like every other thing this mod draws from a render tick:
     * what the world renderer left behind at this point is not a GUI space, and
     * borrowing it would make this depend on which mod rendered last.
     */
    private static void drawTail(float alpha) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution =
                new ScaledResolution(mc);
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, width, height, 0.0D, 1000.0D, 3000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -2000.0F);

        boolean depth = GL11.glGetBoolean(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        Draw.rect(0, 0, width, height, Draw.withAlpha(Theme.background, alpha));

        GL11.glDepthMask(true);
        if (depth) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
    }

    /**
     * Both halves of it. Registered on Forge's bus for the draw events and on FML's
     * for the render tick, because those two live on different buses.
     */
    public static final class Handler {

        @SubscribeEvent
        public void onDrawPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
            drawPre(event.getGui());
        }

        @SubscribeEvent
        public void onDrawPost(GuiScreenEvent.DrawScreenEvent.Post event) {
            drawPost();
        }

        @SubscribeEvent
        public void onRenderTick(TickEvent.RenderTickEvent event) {
            if (event.phase != TickEvent.Phase.END || closing <= 0.0F) {
                return;
            }
            long now = System.nanoTime();
            // Clamped for the same reason the world dissolve clamps: one long frame
            // during a chunk load should not skip the whole thing.
            closing -= Math.min((now - lastFrameNanos) / 1_000_000_000.0F, 0.1F);
            lastFrameNanos = now;
            if (closing <= 0.0F || !enabled()) {
                closing = 0.0F;
                return;
            }
            drawTail(dim() * closingFrom * Ease.outCubic(closing / CLOSE_SECONDS));
        }
    }
}
