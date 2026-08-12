package com.console.uky.client.gui;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Theme;
import com.console.uky.config.UiConfig;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.ArrayList;
import java.util.List;

/**
 * The tooltip box, redrawn — and, more to the point, made to fit.
 *
 * Vanilla's is fine for vanilla: two or three short lines, a purple border, and a
 * layout that assumes nothing will ever be wider than a quarter of the screen. A
 * modded pack breaks all three assumptions at once. A machine that lists its energy,
 * its fluids, its upgrades and three lines of lore produces a box taller than the
 * window, drawn off both ends of it; one mod's item description is a paragraph
 * written as a single line and runs off the right edge with no wrap at all.
 *
 * <p>So this does three things vanilla does not, in this order:
 *
 * <ol>
 * <li><b>Wraps.</b> Anything past {@code tooltipWidth} is re-flowed to that width,
 *     carrying its colour codes onto the continuation lines.
 * <li><b>Scales.</b> A box still taller than the screen is drawn smaller, down to a
 *     floor where the text is still legible.
 * <li><b>Cuts.</b> Past that floor the tail is dropped and counted, rather than drawn
 *     over the edge where it cannot be read anyway.
 * </ol>
 *
 * <p>Then it draws it in this interface's own palette: dark fill, hairline frame, the
 * gold rail down the left, and a rule under the item's name. What the lines <em>say</em>
 * is untouched — every mod that adds to a tooltip is still adding to this one, and
 * anything that rewrote the list before it got here (an {@code ItemTooltipEvent}
 * handler, NEI, a tooltip mod) has already had its turn.
 *
 * @see com.console.uky.mixins.MixinGuiScreenTooltip the hook that calls this
 */
public final class UkyTooltip {

    /** Vanilla's line height, kept so a tooltip is the size everyone expects. */
    private static final int LINE = 10;
    /** Space between the name and what follows it, as vanilla has it. */
    private static final int TITLE_GAP = 2;
    private static final int PAD_X = 6;
    private static final int PAD_Y = 4;
    /** The smallest this will shrink a tooltip; below it, lines are dropped instead. */
    private static final float MIN_SCALE = 0.6F;

    private UkyTooltip() {
    }

    /**
     * @return whether it drew — false hands the box back to vanilla, which is what
     *         the config switch does and what happens when there is nothing to draw
     */
    @SuppressWarnings("unchecked")
    public static boolean draw(GuiScreen screen, List lines, int x, int y, FontRenderer font) {
        if (!UiConfig.restyleTooltips || screen == null || font == null
                || lines == null || lines.isEmpty()) {
            return false;
        }
        List<String> text = new ArrayList<String>();
        for (Object line : lines) {
            // A null or a non-string in here is a mod's bug, not a reason to lose the
            // tooltip: skip it and draw the rest.
            if (line instanceof String) {
                text.add((String) line);
            }
        }
        if (text.isEmpty()) {
            return false;
        }

        int screenWidth = screen.width;
        int screenHeight = screen.height;
        // The first line is the item's name and is left alone: it is short, it is
        // coloured by rarity, and wrapping it would be the one wrap anybody noticed.
        text = wrap(text, font, Math.min(UiConfig.tooltipWidth, Math.max(80, screenWidth - 48)));

        int width = 0;
        for (int i = 0; i < text.size(); i++) {
            width = Math.max(width, font.getStringWidth(text.get(i)));
        }
        int height = height(text.size());

        // Shrink before cutting: a smaller tooltip is still the whole tooltip.
        float scale = 1.0F;
        int room = screenHeight - 8;
        if (height > room) {
            scale = Math.max(MIN_SCALE, room / (float) height);
        }
        if (height * scale > room) {
            // Still over, at the smallest this will go. Drop the tail and say how much
            // was dropped — a tooltip that silently ends mid-sentence reads as a bug in
            // whichever mod wrote it.
            int fits = Math.max(1, (int) (room / scale - PAD_Y * 2 - TITLE_GAP) / LINE);
            int dropped = text.size() - fits;
            if (dropped > 0) {
                text = new ArrayList<String>(text.subList(0, fits));
                text.set(text.size() - 1,
                        EnumChatFormatting.DARK_GRAY + "... +" + (dropped + 1));
                width = 0;
                for (int i = 0; i < text.size(); i++) {
                    width = Math.max(width, font.getStringWidth(text.get(i)));
                }
            }
            height = height(text.size());
        }

        float boxWidth = (width + PAD_X * 2) * scale;
        float boxHeight = height * scale;

        // To the right of the pointer where it fits, to the left where it does not —
        // and never off an edge, which is the other half of what goes wrong with a
        // tooltip nobody sized for the screen.
        float boxX = x + 12;
        if (boxX + boxWidth > screenWidth - 4) {
            boxX = x - 16 - boxWidth;
        }
        if (boxX < 4) {
            boxX = 4;
        }
        float boxY = y - 12;
        if (boxY + boxHeight > screenHeight - 4) {
            boxY = screenHeight - 4 - boxHeight;
        }
        if (boxY < 4) {
            boxY = 4;
        }

        // The state vanilla's own tooltip sets up, because this is drawn in its place
        // and whatever called it expects to get it back the way vanilla left it.
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        GL11.glPushMatrix();
        GL11.glTranslatef(boxX, boxY, 0.0F);
        if (scale != 1.0F) {
            GL11.glScalef(scale, scale, 1.0F);
        }
        try {
            drawBox(font, text, width, height);
        } finally {
            GL11.glPopMatrix();
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            RenderHelper.enableStandardItemLighting();
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        }
        return true;
    }

    private static int height(int lineCount) {
        return PAD_Y * 2 + lineCount * LINE + (lineCount > 1 ? TITLE_GAP : 0);
    }

    /** Fill, frame, rail, and the lines — laid out from the box's own top-left. */
    private static void drawBox(FontRenderer font, List<String> text, int width, int height) {
        float x2 = width + PAD_X * 2;

        Draw.rect(1.0F, 1.0F, x2 + 1.0F, height + 1.0F, Draw.withAlpha(0x000000, 0.35F));
        Draw.gradientV(0.0F, 0.0F, x2, height,
                Draw.withAlpha(Theme.background, 0.96F),
                Draw.withAlpha(Draw.mix(Theme.background, Theme.surface, 0.6F), 0.96F));
        Draw.border(0.0F, 0.0F, x2, height, 1.0F, Draw.withAlpha(Theme.text, 0.14F));
        Draw.rect(0.0F, 0.0F, 2.0F, height, Draw.withAlpha(Theme.accent, 0.9F));
        Draw.gradientH(2.0F, 0.0F, 34.0F, height,
                Draw.withAlpha(Theme.accent, 0.10F), Draw.withAlpha(Theme.accent, 0.0F));

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int lineY = PAD_Y;
        for (int i = 0; i < text.size(); i++) {
            font.drawStringWithShadow(text.get(i), PAD_X, lineY, -1);
            lineY += LINE;
            if (i == 0) {
                // The rule under the name, in the gap vanilla already leaves there.
                if (text.size() > 1) {
                    Draw.gradientH(PAD_X, lineY, x2 - PAD_X, lineY + 1.0F,
                            Draw.withAlpha(Theme.accent, 0.35F),
                            Draw.withAlpha(Theme.accent, 0.0F));
                }
                lineY += TITLE_GAP;
            }
        }
    }

    /**
     * Re-flows anything wider than {@code maxWidth}.
     *
     * The font renderer's own wrap is used, which is what carries a line's colour onto
     * its continuation — a hand-rolled split loses the formatting at every break and
     * turns a coloured paragraph into one coloured line and four white ones.
     */
    @SuppressWarnings("unchecked")
    private static List<String> wrap(List<String> lines, FontRenderer font, int maxWidth) {
        if (maxWidth <= 0) {
            return lines;
        }
        boolean needed = false;
        for (int i = 0; i < lines.size(); i++) {
            if (font.getStringWidth(lines.get(i)) > maxWidth) {
                needed = true;
                break;
            }
        }
        if (!needed) {
            return lines;
        }
        List<String> out = new ArrayList<String>(lines.size() + 4);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (font.getStringWidth(line) <= maxWidth) {
                out.add(line);
                continue;
            }
            List<String> parts = font.listFormattedStringToWidth(line, maxWidth);
            for (int j = 0; j < parts.size(); j++) {
                out.add(parts.get(j));
            }
        }
        return out;
    }
}
