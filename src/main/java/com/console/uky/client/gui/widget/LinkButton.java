package com.console.uky.client.gui.widget;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.LinkIcons;
import com.console.uky.client.render.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

/**
 * One entry in the title screen's link row: a mark, a label, and a URL.
 *
 * Drawn as its own widget rather than as a {@link MenuButton} with a label,
 * because a row of link words is indistinguishable from the menu above it — the
 * mark is what separates "somewhere else" from "somewhere in this game". The mark
 * is whatever the pack asked for: one of ours drawn from primitives, or a picture
 * out of the icon folder.
 */
public class LinkButton extends MenuButton {

    /** The picture, if this link has one; null means the built-in shape below. */
    private final ResourceLocation image;
    private final String shape;

    private LinkButton(int id, int x, int y, int width, int height, String label,
                       ResourceLocation image, String shape) {
        // GHOST only so nothing inherited draws a panel; every mark on this widget
        // is drawn below.
        super(id, x, y, width, height, label, Style.GHOST);
        this.image = image;
        this.shape = shape;
    }

    /**
     * Builds a link, resolving its icon.
     *
     * A named picture that cannot be read falls back to a guessed shape rather than
     * to nothing: a typo in the config should cost the pack its logo, not the row.
     */
    public static LinkButton of(int id, int x, int y, int width, int height,
                                String label, String url, String iconSpec) {
        ResourceLocation image = null;
        String shape = iconSpec;
        if (LinkIcons.isImage(iconSpec)) {
            image = LinkIcons.image(iconSpec);
            shape = null;
        }
        if (image == null && (shape == null || shape.isEmpty())) {
            shape = LinkIcons.guess(url);
        }
        return new LinkButton(id, x, y, width, height, label, image, shape);
    }

    /** Width this link needs for its mark, its label and the spacing between. */
    public static int widthFor(net.minecraft.client.gui.FontRenderer font, String label,
                               int height) {
        return height + 5 + font.getStringWidth(label);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }
        this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width
                && mouseY < this.y + this.height;

        float alpha = this.entrance * this.screenFade;
        if (alpha <= 0.01F) {
            return;
        }

        // Deliberately no panel and no rail.
        //
        // The first version drew both, and a row of them came out as a fence of gold
        // bars that lit up into solid blocks under the pointer — louder than the menu
        // it sits under, which is exactly backwards for a secondary row. These are
        // marks and words: the mark carries the colour, and hovering warms it and
        // draws a line under the pair. Nothing moves and nothing fills.
        float x1 = this.x;
        float y1 = this.y;
        float x2 = x1 + this.width;
        float y2 = y1 + this.height;
        float h = this.hover;
        int accent = accentColor();

        float box = this.height;
        float cx = x1 + box * 0.5F;
        float cy = (y1 + y2) * 0.5F;
        if (this.image != null) {
            // Pack artwork is drawn as it is, only dimmed — tinting someone's logo
            // toward the accent is how a red mark ends up gold and unrecognisable.
            float size = box - 3.0F;
            Draw.texture(this.image, cx - size * 0.5F, cy - size * 0.5F, size, size,
                    Draw.withAlpha(0xFFFFFF, (0.72F + 0.28F * h) * alpha));
        } else {
            int colour = Draw.fade(Draw.mix(Theme.textDim, accent, 0.30F + 0.70F * h), alpha);
            // The cut-outs inside a mark are the backdrop showing through, so they
            // are drawn in the backdrop's colour at the mark's own opacity.
            LinkIcons.draw(this.shape, cx, cy, box * 0.62F, colour,
                    Draw.fade(Theme.background, alpha));
        }

        int textColour = Draw.fade(Draw.mix(Theme.textDim, Theme.textHover, h), alpha);
        float textX = x1 + box + 5.0F;
        drawFitting(mc.fontRenderer, this.displayString, textX,
                y1 + (this.height - 8) / 2.0F, x2 - textX, textColour);

        if (h > 0.01F) {
            float from = textX;
            float to = from + (x2 - from) * h;
            Draw.gradientH(from, y2 - 1.0F, to, y2,
                    Draw.fade(accent, 0.7F * h * alpha), Draw.withAlpha(accent, 0.0F));
        }
    }
}
