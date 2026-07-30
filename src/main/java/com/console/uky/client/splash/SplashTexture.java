package com.console.uky.client.splash;

import cpw.mods.fml.client.SplashProgress;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;

/**
 * A single still image uploaded straight to GL, bypassing Minecraft's
 * {@code TextureManager}.
 *
 * The splash runs before the resource system exists, so nothing higher-level is
 * available. Unlike FML's equivalent this only handles one frame (no animated
 * GIF sheets) and keeps the source image's own dimensions instead of forcing a
 * square power-of-two atlas — the artwork here is a full-screen backdrop, not a
 * sprite sheet.
 */
final class SplashTexture {

    private final int name;
    private final int width;
    private final int height;
    /** Padded power-of-two dimensions; the image occupies the top-left corner. */
    private final int potWidth;
    private final int potHeight;

    SplashTexture(InputStream stream, boolean smooth) throws IOException {
        BufferedImage image = javax.imageio.ImageIO.read(stream);
        if (image == null) {
            throw new IOException("Unsupported or corrupt splash image");
        }
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.potWidth = nextPowerOfTwo(this.width);
        this.potHeight = nextPowerOfTwo(this.height);

        IntBuffer buffer = BufferUtils.createIntBuffer(this.width * this.height);
        int[] row = new int[this.width];
        for (int y = 0; y < this.height; y++) {
            image.getRGB(0, y, this.width, 1, row, 0, this.width);
            buffer.put(row);
        }
        buffer.flip();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        // TextureUtil synchronizes on SplashProgress.class while the main thread
        // uploads textures during mod loading; share that monitor so a texture
        // upload never lands between our glGenTextures and glBindTexture.
        synchronized (SplashProgress.class) {
            this.name = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.name);
        }
        // Photographic art wants linear; the bitmap font must stay crisp.
        int filter = smooth ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, this.potWidth, this.potHeight, 0,
                GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, (IntBuffer) null);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, this.width, this.height,
                GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    private static int nextPowerOfTwo(int value) {
        int result = 1;
        while (result < value) {
            result <<= 1;
        }
        return result;
    }

    int getWidth() {
        return this.width;
    }

    int getHeight() {
        return this.height;
    }

    /** Right edge of the image in texture coordinates (< 1 when padded). */
    float maxU() {
        return (float) this.width / this.potWidth;
    }

    /** Bottom edge of the image in texture coordinates (< 1 when padded). */
    float maxV() {
        return (float) this.height / this.potHeight;
    }

    void bind() {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.name);
    }

    void delete() {
        GL11.glDeleteTextures(this.name);
    }
}
