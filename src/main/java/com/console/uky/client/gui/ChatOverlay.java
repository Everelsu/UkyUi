package com.console.uky.client.gui;

import com.console.uky.UkyUI;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import com.console.uky.config.UiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.WeakHashMap;

/**
 * The chat, drawn in this mod's own style.
 *
 * Vanilla draws each line on a flat grey box the width of the chat area, at half the
 * line's own opacity, and that is the whole design. This keeps the geometry exactly —
 * same line positions, same widths, same fade, same everything the game's own chat
 * settings control — and changes only what is drawn: a panel that fades out to the
 * right instead of a slab, a rail down the left edge, and new lines arriving from the
 * left rather than simply existing.
 *
 * <p><b>The geometry is not negotiable, and that is why.</b> Chat hover and click
 * detection is vanilla's ({@code GuiNewChat.func_146236_a}), and it works out which
 * line and which part of it the pointer is over by recomputing the layout from the
 * same numbers. Move the text by two pixels here and every link in the chat is two
 * pixels out of place — including ours. So the panel is drawn into the margin that is
 * already there, to the left of where the text starts, and the text itself does not
 * move at all.
 *
 * <p>Off by default. See {@code UiConfig.redesignChat}.
 */
public final class ChatOverlay {

    /** Line height, in the space the chat is scaled into. Vanilla's, and fixed. */
    private static final int LINE = 9;
    /** Ticks after which vanilla stops drawing a line that is not being read. */
    private static final int FADE_AFTER = 200;
    /** How long a new line takes to arrive, in milliseconds. */
    private static final float ARRIVE_MS = 220.0F;
    /** Ticks a line may be old and still be treated as new; see {@link #arrival}. */
    private static final int ARRIVE_GRACE = 4;

    /**
     * When each line was first drawn, for an arrival smoother than ticks allow.
     *
     * Weak on purpose: the chat drops a line when it scrolls off the end of its
     * hundred, and the entry for it should go at the same time without anything
     * having to notice. Nothing else refers to these keys.
     */
    private static final Map<ChatLine, Long> firstSeen = new WeakHashMap<ChatLine, Long>();

    /** {@code GuiNewChat.field_146253_i} — the wrapped lines, as drawn. */
    private static Field linesField;
    /** {@code GuiNewChat.field_146250_j} — how far the chat has been scrolled back. */
    private static Field scrollField;
    private static boolean fieldsSearched;
    /** Set once the fields cannot be read, so the failure costs one log line total. */
    private static boolean unavailable;

    private ChatOverlay() {
    }

    /**
     * Draws the chat.
     *
     * @return whether it drew — false leaves vanilla's chat alone, which is what the
     *         config switch turns back on and also what happens if the two fields
     *         below ever stop being where they are
     */
    public static boolean draw(Minecraft mc, int originY) {
        if (!UiConfig.redesignChat || unavailable || mc.ingameGUI == null) {
            return false;
        }
        GuiNewChat chat = mc.ingameGUI.getChatGUI();
        if (chat == null) {
            return false;
        }
        List<ChatLine> lines = readLines(chat);
        if (lines == null) {
            return false;
        }
        // Hidden is a setting, not a failure: nothing is drawn, and vanilla must not
        // draw either, so this still counts as handled.
        if (mc.gameSettings.chatVisibility == EntityPlayer.EnumChatVisibility.HIDDEN) {
            return true;
        }
        if (lines.isEmpty()) {
            return true;
        }

        boolean open = chat.getChatOpen();
        float scale = chat.func_146244_h();
        int visibleLines = chat.func_146232_i();
        int width = MathHelper.ceiling_float_int(chat.func_146228_f() / scale);
        float opacity = mc.gameSettings.chatOpacity * 0.9F + 0.1F;
        int scroll = readScroll(chat);
        int counter = mc.ingameGUI.getUpdateCounter();

        GL11.glPushMatrix();
        GL11.glTranslatef(2.0F, 20.0F, 0.0F);
        GL11.glScalef(scale, scale, 1.0F);

        int rows = Math.min(visibleLines, lines.size() - scroll);
        // Under everything, and only while the chat is being read: open, this is a
        // panel the player is looking at and it should be one; closed, it is a HUD
        // element sitting on the world and a slab behind it would be in the way.
        if (open) {
            drawBackdrop(width, rows);
        }
        int hovered = open ? hoveredRow(mc, originY, scale, width, rows) : -1;

        for (int row = 0; row + scroll < lines.size() && row < visibleLines; row++) {
            ChatLine line = lines.get(row + scroll);
            if (line == null) {
                continue;
            }
            int age = counter - line.getUpdatedCounter();
            if (age >= FADE_AFTER && !open) {
                continue;
            }
            float alpha = fade(age, open) * opacity;
            // Vanilla's own floor. Below it a line is a smear rather than text, and
            // the panel under it would be the only thing still visible.
            if (alpha <= 3.0F / 255.0F) {
                continue;
            }
            drawLine(mc.fontRenderer, line, -row * LINE, width, alpha, age, open,
                    row == hovered);
        }

        if (open) {
            drawScrollRail(width, visibleLines, lines.size(), scroll);
            // Something above the top of the box, said by fading its edge rather than
            // by an arrow nobody would look for.
            if (lines.size() - scroll > rows) {
                Draw.gradientV(-2.0F, -rows * LINE, width + 4.0F, -rows * LINE + LINE,
                        Draw.withAlpha(Theme.background, 0.85F),
                        Draw.withAlpha(Theme.background, 0.0F));
            }
        }
        GL11.glPopMatrix();
        return true;
    }

    /**
     * One panel behind the whole open chat, rather than a stack of separate boxes.
     *
     * Open, the chat is a window: it holds still, it can be scrolled, and it is being
     * read line by line. A single field with a rail down the side and its own top edge
     * reads as one, where per-line boxes read as a list of unrelated notifications.
     */
    private static void drawBackdrop(int width, int rows) {
        float top = -rows * LINE - 2.0F;
        Draw.gradientV(-3.0F, top, width + 6.0F, 1.0F,
                Draw.withAlpha(Theme.background, 0.55F),
                Draw.withAlpha(Theme.background, 0.75F));
        Draw.gradientH(-3.0F, top, -2.0F, 1.0F,
                Draw.withAlpha(Theme.accent, 0.55F), Draw.withAlpha(Theme.accent, 0.0F));
        Draw.gradientH(-3.0F, top, width * 0.55F, top + 1.0F,
                Draw.withAlpha(Theme.accent, 0.5F), Draw.withAlpha(Theme.accent, 0.0F));
    }

    /**
     * Which line the pointer is over, in the chat's own rows.
     *
     * Worked out here rather than asked of vanilla because vanilla answers a different
     * question — {@code func_146236_a} returns the component under the pointer, which
     * is null over the empty half of a short line, and a row that stops being
     * highlighted halfway along itself looks broken.
     */
    private static int hoveredRow(Minecraft mc, int originY, float scale, int width, int rows) {
        if (mc.currentScreen == null) {
            return -1;
        }
        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        float mouseX = Mouse.getX() * res.getScaledWidth() / (float) mc.displayWidth;
        float mouseY = res.getScaledHeight()
                - Mouse.getY() * res.getScaledHeight() / (float) mc.displayHeight - 1.0F;

        // Into the space the rows are drawn in: the chat's origin, then its scale.
        float localX = (mouseX - 2.0F) / scale;
        float localY = (mouseY - originY - 20.0F) / scale;
        if (localX < -3.0F || localX > width + 6.0F || localY >= 0.0F) {
            return -1;
        }
        int row = (int) Math.floor(-localY / LINE);
        return row >= 0 && row < rows ? row : -1;
    }

    /**
     * One line: its panel, its rail, and the text at exactly the place vanilla would
     * have put it.
     *
     * @param baseline the y of the line's bottom edge, as vanilla computes it
     */
    private static void drawLine(FontRenderer font, ChatLine line, int baseline, int width,
                                 float alpha, int age, boolean open, boolean hovered) {
        float arrive = arrival(line, age);
        // The whole row moves, text included, so the arrival is a sub-pixel slide
        // rather than the four-frame jump the tick counter alone could manage.
        float slide = (1.0F - arrive) * -12.0F;
        float shown = alpha * arrive;

        float top = baseline - LINE;
        float bottom = baseline;

        GL11.glPushMatrix();
        GL11.glTranslatef(slide, 0.0F, 0.0F);

        boolean ours = isLink(line);

        // Closed, each line carries its own panel — it is alone on the world and needs
        // something to sit on. Open, the backdrop is already there and a second layer
        // on top of it only muddies the text; the rows get a hairline instead.
        if (!open) {
            // Fading out to the right rather than a slab: chat lines are ragged, and a
            // box drawn to the full width of the chat area is mostly a box drawn
            // behind nothing. This one is opaque where the text is and gone by the far
            // edge.
            Draw.gradientH(-2.0F, top, width + 4.0F, bottom,
                    Draw.withAlpha(Theme.background, 0.72F * shown),
                    Draw.withAlpha(Theme.background, 0.30F * shown));
        } else if (hovered) {
            Draw.gradientH(-2.0F, top, width + 4.0F, bottom,
                    Draw.withAlpha(ours ? Theme.accent : Theme.text, 0.14F),
                    Draw.withAlpha(ours ? Theme.accent : Theme.text, 0.0F));
        }

        // The rail. Gold for a line of ours — an achievement link — and the quieter
        // separator colour for everything else, so our own lines are findable in a
        // busy chat without shouting.
        Draw.rect(-2.0F, top, -1.0F, bottom,
                ours ? Draw.withAlpha(Theme.accent, 0.95F * shown)
                     : Draw.withAlpha(Theme.text, 0.16F * shown));
        if (ours) {
            // A link is the one line here that can be clicked, so it is the one line
            // that gets to glow a little.
            Draw.gradientH(-1.0F, top, 10.0F, bottom,
                    Draw.withAlpha(Theme.accent, 0.18F * shown),
                    Draw.withAlpha(Theme.accent, 0.0F));
        }

        // One highlight along the top edge while the line is arriving, and nothing
        // afterwards — the panel it leaves behind is completely still.
        if (arrive < 1.0F) {
            Draw.gradientH(-2.0F, top, width * 0.6F, top + 1.0F,
                    Draw.withAlpha(Theme.accent, 0.75F * (1.0F - arrive) * alpha),
                    Draw.withAlpha(Theme.accent, 0.0F));
        }

        // When it was said, out at the right edge where nothing else is — and only
        // while the chat is open, because that is the only time the answer is being
        // looked for. It cannot move the text: everything to the left of here is
        // exactly where vanilla would have put it, which is what keeps clicking a
        // link landing on the link.
        String text = line.func_151461_a().getFormattedText();
        if (open) {
            String stamp = timestamp(age);
            int stampWidth = font.getStringWidth(stamp);
            // Only where the line itself leaves room. A stamp printed over the end of
            // a long message would be worse than no stamp at all, and the lines it
            // would land on are exactly the ones being read.
            if (font.getStringWidth(text) < width - stampWidth - 6) {
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                font.drawString(stamp, (int) (width + 2 - stampWidth), (int) (baseline - 8),
                        Draw.withAlpha(Theme.textDim, (hovered ? 0.55F : 0.30F) * alpha));
            }
        }

        // Blending, explicitly, immediately before the text.
        //
        // The font renderer turns alpha testing on and nothing else, so a colour with
        // an alpha in it only means anything while blending is enabled — and the last
        // thing to draw before this was a panel, which turns blending off again on its
        // way out. Vanilla's own chat has the same line in it, added by Forge as the
        // fix for MC-36812, for exactly this reason.
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        int colour = 0xFFFFFF | ((int) (shown * 255.0F) << 24);
        font.drawString(text, 0, baseline - 8, colour);
        GL11.glPopMatrix();
    }

    /**
     * The scroll indicator, on the right where it is out of the way of the text.
     *
     * Vanilla puts its own two-pixel bar to the left of the messages, inside the
     * margin this design uses for the rails. Moving it costs nothing: it is drawn,
     * never clicked — the chat is scrolled with the wheel.
     */
    private static void drawScrollRail(int width, int visibleLines, int total, int scroll) {
        if (total <= visibleLines) {
            return;
        }
        float viewport = visibleLines * LINE;
        float x = width + 6.0F;
        Draw.rect(x, -viewport, x + 1.0F, 0.0F, Draw.withAlpha(Theme.text, 0.12F));

        float thumb = Math.max(8.0F, viewport * visibleLines / (float) total);
        float travel = viewport - thumb;
        float t = Ease.clamp01(scroll / (float) Math.max(1, total - visibleLines));
        float bottom = -travel * t;
        Draw.rect(x - 1.0F, bottom - thumb, x + 2.0F, bottom,
                Draw.withAlpha(Theme.accent, 0.65F));
    }

    /**
     * The wall-clock time a line arrived, as {@code HH:MM}.
     *
     * Derived from the tick counter rather than remembered, because a line that was
     * already in the chat when this started drawing has no remembered time and would
     * be stamped with now. Twenty ticks to the second is exact enough for a minute,
     * and it survives a resize — the counter belongs to the HUD, not to the line
     * objects, which are rebuilt whenever the chat is re-wrapped.
     */
    private static String timestamp(int ageTicks) {
        long when = System.currentTimeMillis() - ageTicks * 50L;
        long local = (when + TimeZone.getDefault().getOffset(when)) / 1000L;
        long minutes = local / 60L % 60L;
        long hours = local / 3600L % 24L;
        return (hours < 10 ? "0" : "") + hours + ":" + (minutes < 10 ? "0" : "") + minutes;
    }

    /** Vanilla's own fade curve, so a line lives exactly as long as it used to. */
    private static float fade(int age, boolean open) {
        if (open) {
            return 1.0F;
        }
        float t = 1.0F - age / (float) FADE_AFTER;
        t = Ease.clamp01(t * 10.0F);
        return t * t;
    }

    /**
     * How far into its arrival a line is.
     *
     * Only ever animates a line that is genuinely new. The chat rebuilds every line
     * object from scratch when it is re-wrapped — a window resize, a change to the
     * chat width — and without the tick check that rebuild would replay the arrival
     * of the entire backlog at once, which looks like the chat being rewritten.
     */
    private static float arrival(ChatLine line, int age) {
        if (age > ARRIVE_GRACE) {
            return 1.0F;
        }
        long now = System.currentTimeMillis();
        Long seen = firstSeen.get(line);
        if (seen == null) {
            firstSeen.put(line, Long.valueOf(now));
            return 0.0F;
        }
        return Ease.outCubic((now - seen.longValue()) / ARRIVE_MS);
    }

    /** Whether this line is one of ours, i.e. carries an achievement link. */
    private static boolean isLink(ChatLine line) {
        try {
            IChatComponent component = line.func_151461_a();
            ClickEvent click = component.getChatStyle().getChatClickEvent();
            return click != null
                    && click.getAction() == ClickEvent.Action.RUN_COMMAND
                    && click.getValue() != null
                    && click.getValue().startsWith("/" + AchievementCommand.NAME + " ");
        } catch (Throwable t) {
            return false;
        }
    }

    // -------------------------------------------------------------- reflection --

    /**
     * The two things {@code GuiNewChat} does not expose.
     *
     * Both are {@code field_} names, which means they are spelled the same in a
     * development workspace and in a production jar — the obfuscation pass leaves
     * anything MCP never named alone. A readable name would need the mapping and
     * would not survive the trip.
     */
    @SuppressWarnings("unchecked")
    private static List<ChatLine> readLines(GuiNewChat chat) {
        findFields();
        if (linesField == null) {
            return null;
        }
        try {
            return (List<ChatLine>) linesField.get(chat);
        } catch (Throwable t) {
            giveUp(t);
            return null;
        }
    }

    private static int readScroll(GuiNewChat chat) {
        if (scrollField == null) {
            return 0;
        }
        try {
            return scrollField.getInt(chat);
        } catch (Throwable t) {
            // Not fatal on its own: an unscrolled chat is the normal case, and the
            // lines are what actually matter.
            return 0;
        }
    }

    private static void findFields() {
        if (fieldsSearched) {
            return;
        }
        fieldsSearched = true;
        try {
            linesField = GuiNewChat.class.getDeclaredField("field_146253_i");
            linesField.setAccessible(true);
            scrollField = GuiNewChat.class.getDeclaredField("field_146250_j");
            scrollField.setAccessible(true);
        } catch (Throwable t) {
            giveUp(t);
        }
    }

    private static void giveUp(Throwable cause) {
        unavailable = true;
        linesField = null;
        scrollField = null;
        UkyUI.LOGGER.warn("Chat redesign is off: GuiNewChat no longer keeps its lines"
                + " where this expects them. Vanilla's chat is drawn instead.", cause);
    }
}
