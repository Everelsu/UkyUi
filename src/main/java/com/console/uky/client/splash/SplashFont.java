package com.console.uky.client.splash;

import com.console.uky.UkyUI;
import com.console.uky.compat.GL11;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.InputStream;

/**
 * Text before the game has fonts.
 *
 * <p>The loading screen runs while the resource reload that builds the game's fonts is
 * still under way, so {@code Minecraft.font} has nothing to draw with yet. This reads
 * vanilla's ASCII sheet straight out of the game's own jar — not the resource manager,
 * which is what is being reloaded — and measures each glyph off its pixels, the way
 * 1.7.10's font renderer did. Printable ASCII only; see {@code UiConfig.splashSafe}.
 */
final class SplashFont {

    private static final ResourceLocation SHEET =
            ResourceLocation.withDefaultNamespace("textures/font/ascii.png");

    private int texture = -1;
    private final int[] widths = new int[256];
    private boolean broken;

    private boolean ready() {
        if (this.texture >= 0) {
            return true;
        }
        if (this.broken) {
            return false;
        }
        try {
            IoSupplier<InputStream> source = Minecraft.getInstance().getVanillaPackResources()
                    .getResource(PackType.CLIENT_RESOURCES, SHEET);
            if (source == null) {
                throw new IllegalStateException("no " + SHEET);
            }
            NativeImage image;
            try (InputStream in = source.get()) {
                image = NativeImage.read(in);
            }
            measure(image);
            DynamicTexture uploaded = new DynamicTexture(image);
            this.texture = uploaded.getId();
            return true;
        } catch (Throwable t) {
            this.broken = true;
            UkyUI.LOGGER.warn("Loading screen font unavailable; it will show no text", t);
            return false;
        }
    }

    private void measure(NativeImage image) {
        int cell = image.getWidth() / 16;
        for (int c = 0; c < 256; c++) {
            int cx = (c % 16) * cell;
            int cy = (c / 16) * cell;
            int last = -1;
            for (int x = cell - 1; x >= 0 && last < 0; x--) {
                for (int y = 0; y < cell; y++) {
                    if ((image.getPixelRGBA(cx + x, cy + y) >>> 24) > 0) {
                        last = x;
                        break;
                    }
                }
            }
            // In sheet pixels, scaled to the 8-unit cell the rest of this class works in.
            this.widths[c] = c == ' ' ? 4 : (last < 0 ? 0 : (last + 2) * 8 / cell);
        }
    }

    int width(String text) {
        if (!ready()) {
            return 0;
        }
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += this.widths[text.charAt(i) & 0xFF];
        }
        return w;
    }

    /** Draws {@code text} with its top left at (x, y), in ARGB. */
    void draw(String text, float x, float y, int argb) {
        if (text == null || text.isEmpty() || (argb >>> 24) == 0 || !ready()) {
            return;
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.texture);
        GL11.vertexColour(argb);
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < text.length(); i++) {
            int c = text.charAt(i) & 0xFF;
            float u = (c % 16) / 16.0F;
            float v = (c / 16) / 16.0F;
            float s = 1.0F / 16.0F;
            GL11.glTexCoord2f(u, v);
            GL11.glVertex2f(x, y);
            GL11.glTexCoord2f(u, v + s);
            GL11.glVertex2f(x, y + 8);
            GL11.glTexCoord2f(u + s, v + s);
            GL11.glVertex2f(x + 8, y + 8);
            GL11.glTexCoord2f(u + s, v);
            GL11.glVertex2f(x + 8, y);
            x += this.widths[c];
        }
        GL11.glEnd();
        GL11.vertexColour(0xFFFFFFFF);
    }
}
