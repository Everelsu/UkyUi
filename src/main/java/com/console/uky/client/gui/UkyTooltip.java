package com.console.uky.client.gui;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Theme;
import com.console.uky.compat.GL11;
import com.console.uky.config.UiConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import org.joml.Vector2ic;

import java.util.ArrayList;
import java.util.List;

/**
 * Every tooltip in the game, as one of this mod's panels.
 *
 * <p>The look is the smaller half. Vanilla's box assumes nothing is ever very wide or
 * very tall, which is true of vanilla and not of a modded pack: a machine listing its
 * energy, its fluids and three lines of lore produces a box taller than the window. This
 * one wraps long lines to {@code tooltipWidth}, scales a box that is still too tall until
 * it fits, and only then cuts what is left — saying how many lines it cut.
 *
 * <p>What the lines say is untouched, and so is anything a mod draws into a tooltip as an
 * image (a bundle's contents, a map preview): each component still draws itself, only
 * the box behind them is ours.
 */
public final class UkyTooltip {

    private static final int TITLE_GAP = 2;
    private static final int PAD_X = 6;
    private static final int PAD_Y = 4;
    private static final float MIN_SCALE = 0.6F;

    /** Wraps long lines before vanilla turns them into components. */
    @SubscribeEvent
    public void onGather(RenderTooltipEvent.GatherComponents event) {
        if (!UiConfig.restyleTooltips || UiConfig.tooltipWidth <= 0) {
            return;
        }
        int width = Math.min(UiConfig.tooltipWidth, Math.max(80, event.getScreenWidth() - 48));
        if (event.getMaxWidth() <= 0 || event.getMaxWidth() > width) {
            event.setMaxWidth(width);
        }
    }

    @SubscribeEvent
    public void onRender(RenderTooltipEvent.Pre event) {
        if (!UiConfig.restyleTooltips || event.getComponents().isEmpty()) {
            return;
        }
        event.setCanceled(true);
        draw(event.getGraphics(), event.getFont(), new ArrayList<ClientTooltipComponent>(event.getComponents()),
                event.getX(), event.getY(), event.getScreenWidth(), event.getScreenHeight(), event);
    }

    private static int width(Font font, List<ClientTooltipComponent> lines) {
        int width = 0;
        for (ClientTooltipComponent line : lines) {
            width = Math.max(width, line.getWidth(font));
        }
        return width;
    }

    private static int height(List<ClientTooltipComponent> lines) {
        int height = PAD_Y * 2 + (lines.size() > 1 ? TITLE_GAP : 0);
        for (ClientTooltipComponent line : lines) {
            height += line.getHeight();
        }
        return height;
    }

    private static void draw(GuiGraphics g, Font font, List<ClientTooltipComponent> lines,
                             int mouseX, int mouseY, int screenWidth, int screenHeight,
                             RenderTooltipEvent.Pre event) {
        int height = height(lines);
        int room = screenHeight - 8;
        float scale = height > room ? Math.max(MIN_SCALE, room / (float) height) : 1.0F;

        // Still too tall at the smallest scale worth reading: cut, and say so.
        if (height * scale > room) {
            int dropped = 0;
            while (lines.size() > 1 && height(lines) * scale > room) {
                lines.remove(lines.size() - 1);
                dropped++;
            }
            if (dropped > 0) {
                lines.set(lines.size() - 1, ClientTooltipComponent.create(
                        Component.literal("... +" + (dropped + 1)).withStyle(ChatFormatting.DARK_GRAY)
                                .getVisualOrderText()));
            }
            height = height(lines);
        }
        int width = width(font, lines);

        int boxW = (int) Math.ceil((width + PAD_X * 2) * scale);
        int boxH = (int) Math.ceil(height * scale);
        // Placed the way this tooltip asked to be — next to the pointer, or above a
        // widget — and only the size is ours.
        Vector2ic at = event.getTooltipPositioner().positionTooltip(screenWidth, screenHeight,
                mouseX, mouseY, boxW, boxH);
        float boxX = Math.max(4, Math.min(at.x() - PAD_X, screenWidth - 4 - boxW));
        float boxY = Math.max(4, Math.min(at.y() - PAD_Y, screenHeight - 4 - boxH));

        g.flush();
        GL11.glPushMatrix();
        GL11.glTranslatef(boxX, boxY, 0.0F);
        GL11.glScalef(scale, scale, 1.0F);
        g.pose().pushPose();
        // In front of the items it is drawn over, as vanilla's is.
        g.pose().translate(0.0F, 0.0F, 400.0F);
        try {
            float x2 = width + PAD_X * 2;
            drawPanel(0.0F, 0.0F, x2, height);
            int y = PAD_Y;
            for (int i = 0; i < lines.size(); i++) {
                ClientTooltipComponent line = lines.get(i);
                line.renderText(font, PAD_X, y, g.pose().last().pose(), g.bufferSource());
                g.flush();
                line.renderImage(font, PAD_X, y, g);
                y += line.getHeight();
                if (i == 0 && lines.size() > 1) {
                    g.flush();
                    Draw.gradientH(PAD_X, y, x2 - PAD_X, y + 1.0F,
                            Draw.withAlpha(Theme.accent, 0.35F), Draw.withAlpha(Theme.accent, 0.0F));
                    y += TITLE_GAP;
                }
            }
            g.flush();
        } finally {
            g.pose().popPose();
            GL11.glPopMatrix();
        }
    }

    private static void drawPanel(float x1, float y1, float x2, float y2) {
        Draw.rect(x1 + 1.0F, y1 + 1.0F, x2 + 1.0F, y2 + 1.0F, Draw.withAlpha(0x000000, 0.35F));
        Draw.gradientV(x1, y1, x2, y2,
                Draw.withAlpha(Theme.background, 0.96F),
                Draw.withAlpha(Draw.mix(Theme.background, Theme.surface, 0.6F), 0.96F));
        Draw.border(x1, y1, x2, y2, 1.0F, Draw.withAlpha(Theme.text, 0.14F));
        Draw.rect(x1, y1, x1 + 2.0F, y2, Draw.withAlpha(Theme.accent, 0.9F));
        Draw.gradientH(x1 + 2.0F, y1, x1 + 34.0F, y2,
                Draw.withAlpha(Theme.accent, 0.10F), Draw.withAlpha(Theme.accent, 0.0F));
    }

    /** The same panel, for anything else that wants to look like a tooltip. */
    public static boolean panel(int x, int y, int w, int h) {
        if (!UiConfig.restyleTooltips || w <= 0 || h <= 0) {
            return false;
        }
        drawPanel(x, y, x + (float) w, y + (float) h);
        return true;
    }
}
