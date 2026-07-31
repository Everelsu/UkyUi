package com.console.uky.client.world;

import com.console.uky.UkyUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-world screenshots: captured on the way out, shown on the way back in.
 *
 * The image lives inside the world's own save folder, so it travels with the
 * world if it is copied or moved and disappears when the world is deleted —
 * nothing to keep in sync, no orphaned files in a config directory.
 *
 * Worlds that have never been left have no image; their tile stays black, which
 * is exactly the right signal for "you have not been here yet".
 */
public final class WorldPreviews {

    private static final String FILE_NAME = "uky_preview.png";
    /**
     * Stored at 960x540.
     *
     * It was half this, on the reasoning that a tile is small. A tile is not small:
     * four across a 1080p window makes each one roughly six hundred pixels wide, so
     * 480 was being stretched past its own resolution and read as soft — and the same
     * image is used full-screen as the loading backdrop, where 480 is very soft
     * indeed. At 960 the tile is a downscale rather than an upscale on any ordinary
     * window, which is the side of 1:1 to be on.
     *
     * Four times the pixels, and still only a couple of hundred kilobytes of PNG per
     * world, written once when you leave.
     */
    private static final int WIDTH = 960;
    private static final int HEIGHT = 540;

    private static final Map<String, ResourceLocation> TEXTURES =
            new HashMap<String, ResourceLocation>();
    /** Folders already checked and found to have no preview; avoids re-reading. */
    private static final Set<String> MISSING = new HashSet<String>();

    /** Folder of the world being entered, so the loading screen knows what to show. */
    private static String enteringWorld;

    private static IntBuffer pixelBuffer;

    private WorldPreviews() {
    }

    // ------------------------------------------------------------------ paths --

    public static File savesDir() {
        return new File(Minecraft.getMinecraft().mcDataDir, "saves");
    }

    public static File previewFile(String folderName) {
        return new File(new File(savesDir(), folderName), FILE_NAME);
    }

    public static void setEnteringWorld(String folderName) {
        enteringWorld = folderName;
    }

    public static String getEnteringWorld() {
        return enteringWorld;
    }

    // --------------------------------------------------------------- textures --

    /**
     * Texture for a world's preview, or null if it has none.
     *
     * Results are cached both ways: a hit keeps the uploaded texture, a miss is
     * remembered so the disk is not hit again every frame the tile is on screen.
     */
    public static ResourceLocation texture(String folderName) {
        ResourceLocation cached = TEXTURES.get(folderName);
        if (cached != null) {
            return cached;
        }
        if (MISSING.contains(folderName)) {
            return null;
        }

        File file = previewFile(folderName);
        if (!file.isFile()) {
            MISSING.add(folderName);
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                MISSING.add(folderName);
                return null;
            }
            ResourceLocation location = new ResourceLocation("uky",
                    "worldpreview/" + folderName.toLowerCase().replaceAll("[^a-z0-9_-]", "_"));
            Minecraft.getMinecraft().getTextureManager()
                    .loadTexture(location, new DynamicTexture(image));
            TEXTURES.put(folderName, location);
            return location;
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not read world preview for " + folderName, t);
            MISSING.add(folderName);
            return null;
        }
    }

    /** Drops the cached texture so the next read picks up a freshly written file. */
    public static void invalidate(String folderName) {
        ResourceLocation location = TEXTURES.remove(folderName);
        MISSING.remove(folderName);
        if (location != null) {
            Minecraft.getMinecraft().getTextureManager().deleteTexture(location);
        }
    }

    // ---------------------------------------------------------------- capture --

    /**
     * Grabs the current frame and writes it as {@code folderName}'s preview.
     *
     * Must run on the render thread with the world's frame still in the buffer —
     * see {@link com.console.uky.handler.WorldCaptureHandler} for the timing.
     */
    public static void capture(String folderName) {
        try {
            BufferedImage frame = readFrame();
            if (frame == null) {
                return;
            }
            File file = previewFile(folderName);
            File parent = file.getParentFile();
            if (parent == null || !parent.isDirectory()) {
                return;
            }
            ImageIO.write(scaleToCover(frame), "png", file);
            invalidate(folderName);
        } catch (Throwable t) {
            // A missing thumbnail is cosmetic; never let it interfere with quitting.
            UkyUI.LOGGER.warn("Could not capture world preview for " + folderName, t);
        }
    }

    /**
     * Reads the framebuffer back into an image.
     *
     * Two paths because Minecraft may or may not be rendering into an FBO: with one
     * the finished frame is in a texture, without one it is in the front buffer.
     * Both come out bottom-up, hence the flip.
     */
    private static BufferedImage readFrame() {
        Minecraft mc = Minecraft.getMinecraft();
        int width;
        int height;

        if (OpenGlHelper.isFramebufferEnabled()) {
            Framebuffer fb = mc.getFramebuffer();
            width = fb.framebufferTextureWidth;
            height = fb.framebufferTextureHeight;
            int size = width * height;
            ensureBuffer(size);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, fb.framebufferTexture);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA,
                    GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixelBuffer);
        } else {
            width = mc.displayWidth;
            height = mc.displayHeight;
            ensureBuffer(width * height);
            GL11.glReadBuffer(GL11.GL_FRONT);
            GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA,
                    GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixelBuffer);
        }

        if (width <= 0 || height <= 0) {
            return null;
        }

        int[] pixels = new int[width * height];
        pixelBuffer.position(0);
        pixelBuffer.get(pixels);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            // GL rows run bottom-up.
            int source = (height - 1 - y) * width;
            image.setRGB(0, y, width, 1, pixels, source, width);
        }
        return image;
    }

    private static void ensureBuffer(int size) {
        if (pixelBuffer == null || pixelBuffer.capacity() < size) {
            pixelBuffer = BufferUtils.createIntBuffer(size);
        }
        pixelBuffer.clear();
    }

    /** Centre-crops to 16:9 and scales down, so tiles never letterbox. */
    private static BufferedImage scaleToCover(BufferedImage source) {
        float targetRatio = (float) WIDTH / HEIGHT;
        int cropWidth = source.getWidth();
        int cropHeight = source.getHeight();
        if ((float) cropWidth / cropHeight > targetRatio) {
            cropWidth = Math.round(cropHeight * targetRatio);
        } else {
            cropHeight = Math.round(cropWidth / targetRatio);
        }
        int x = (source.getWidth() - cropWidth) / 2;
        int y = (source.getHeight() - cropHeight) / 2;

        BufferedImage out = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source.getSubimage(x, y, cropWidth, cropHeight),
                0, 0, WIDTH, HEIGHT, null);
        g.dispose();
        return out;
    }
}
