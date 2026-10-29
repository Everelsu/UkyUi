package com.console.uky.compat;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.awt.image.BufferedImage;
import java.util.Locale;

/**
 * BufferedImage to game texture, which 1.7.10 did in one constructor.
 *
 * 1.21 only uploads {@link NativeImage}s, so pictures this mod builds or reads through
 * ImageIO are copied across pixel by pixel. NativeImage keeps ABGR where BufferedImage
 * hands out ARGB; the swap is the whole of the conversion.
 */
public final class Images {

    private Images() {
    }

    public static NativeImage toNative(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        NativeImage out = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = image.getRGB(x, y);
                int abgr = (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
                out.setPixelRGBA(x, y, abgr);
            }
        }
        return out;
    }

    public static DynamicTexture texture(BufferedImage image) {
        return new DynamicTexture(toNative(image));
    }

    /** Uploads {@code image} and registers it under a name derived from {@code hint}. */
    public static ResourceLocation register(String hint, BufferedImage image) {
        return register(hint, texture(image));
    }

    public static ResourceLocation register(String hint, DynamicTexture texture) {
        String name = hint.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        return Minecraft.getInstance().getTextureManager().register(name, texture);
    }
}
