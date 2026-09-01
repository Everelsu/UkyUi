package com.console.uky.client.render;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * An item stack drawn as an icon, with the GL state a block actually needs.
 *
 * <p>Half the things in the game draw as a flat sprite and the other half — every
 * ordinary block — as real 3D geometry: {@code RenderItem} sends the whole cube
 * through {@code RenderBlocks}, six faces of it, rotated into the isometric view.
 * Three of those faces point away from the camera, and the only thing stopping them
 * being painted over the three that face it is <b>back-face culling</b>.
 *
 * <p>Which this interface turns off. {@link Draw} disables {@code GL_CULL_FACE} for
 * its own shapes — its circles and glows are wound the wrong way round for a
 * y-down coordinate system and would be culled entirely — and deliberately does not
 * put it back, because 2D drawing never wants it. So any block drawn after a panel
 * came out inside out: the back of the cube over the front, dark faces where the lit
 * ones should be, sometimes a hole through the middle. Sprites were unaffected, which
 * is exactly why it looked like only <em>some</em> items were broken.
 *
 * <p>Scaling had the same shape of bug. A block is three-dimensional, so growing it
 * on x and y while leaving z alone does not enlarge it, it flattens it — the entrance
 * animation squashed the cube into the screen and let it back out again.
 *
 * <p>So this is the one place that draws an item, and it sets up all of it: culling,
 * depth, lighting, and a scale that applies to every axis a cube has.
 */
public final class ItemIcon {

    /** Vanilla's GUI icon size; every coordinate here is relative to it. */
    public static final int SIZE = 16;

    /** One instance is plenty — it holds no per-call state worth keeping apart. */
    private static RenderItem renderer;

    private ItemIcon() {
    }

    /** As {@link #draw(ItemStack, float, float, float, float, float)}, unrotated. */
    public static void draw(ItemStack stack, float centerX, float centerY, float brightness) {
        draw(stack, centerX, centerY, 1.0F, 0.0F, brightness);
    }

    /**
     * Draws {@code stack} centred on the given point.
     *
     * @param scale      1 draws it at the usual 16x16; applied on every axis
     * @param spin       degrees about the screen normal, for entrances
     * @param brightness multiplied into the item's colour; below 1 dims it, which is
     *                   how a locked achievement is shown
     */
    public static void draw(ItemStack stack, float centerX, float centerY, float scale,
                            float spin, float brightness) {
        if (stack == null) {
            return;
        }
        if (renderer == null) {
            // 1.12 hands one out rather than having one made: it needs the model
            // manager, which only the game has.
            renderer = Minecraft.getMinecraft().getRenderItem();
        }
        Minecraft mc = Minecraft.getMinecraft();

        GlStateManager.pushMatrix();

        // The state a cube needs, in the order RenderItem expects to find it — and the
        // lighting before the transform below rather than after it.
        //
        // `glLight(GL_POSITION)` puts the light where the modelview matrix says, so
        // setting it up inside the entrance would turn the two lights with the item
        // and light it from somewhere new on every frame of the spin. Vanilla calls
        // this at the top of a screen's draw for the same reason.
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableColorMaterial();
        GlStateManager.enableLighting();
        GlStateManager.color(brightness, brightness, brightness, 1.0F);

        GlStateManager.translate(centerX, centerY, 0.0F);
        if (spin != 0.0F) {
            GlStateManager.rotate(spin, 0.0F, 0.0F, 1.0F);
        }
        // Every axis, including the one pointing into the screen: see the note above.
        GlStateManager.scale(scale, scale, scale);

        try {
            renderer.renderItemAndEffectIntoGUI(stack, -SIZE / 2, -SIZE / 2);
        } catch (Throwable t) {
            // A modded item can carry a renderer that throws. One bad icon must not
            // take the screen — or the frame — with it.
        } finally {
            GlStateManager.disableLighting();
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableDepth();
            // Culling goes back off, because everything else drawn here is 2D and
            // half of it is wound the way culling would throw away.
            GlStateManager.disableCull();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }
}
