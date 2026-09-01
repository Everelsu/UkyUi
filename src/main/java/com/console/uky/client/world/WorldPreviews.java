package com.console.uky.client.world;

import com.console.uky.UkyUI;
import net.minecraft.client.renderer.GlStateManager;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
        return new File(Minecraft.getMinecraft().gameDir, "saves");
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
            ResourceLocation location = new ResourceLocation("uky", texturePath(folderName));
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

    /**
     * A texture path belonging to this folder and no other.
     *
     * A {@link ResourceLocation} accepts only {@code [a-z0-9_./-]}, so the folder name
     * has to be scrubbed — and scrubbing on its own is not enough, which is the bug
     * this exists to fix. A world named in any script but Latin reduces to nothing but
     * underscores, so every Cyrillic-named world produced the same path, and the second
     * one loaded got handed the first one's picture: create a world and the loading
     * screen showed the world you had just left. The hash is what makes the path the
     * folder's own; it is allowed to be negative, and hex of a negative int is still
     * only characters a path may contain.
     */
    private static String texturePath(String folderName) {
        return "worldpreview/" + folderName.toLowerCase().replaceAll("[^a-z0-9_-]", "_")
                + "_" + Integer.toHexString(folderName.hashCode());
    }

    /**
     * Forgets a folder's picture without freeing it, for a world that has been deleted.
     *
     * <p><b>Why this is separate from {@link #invalidate}.</b> Both caches are keyed by
     * folder name, and Minecraft hands folder names back out: creating a world picks the
     * first name that is free, so deleting "Новый мир 2" makes that exact folder
     * available again and the next world created takes it. Nothing was dropping the
     * cache on deletion, so the new world found the old one's uploaded texture still
     * sitting under its name — and the loading screen showed a picture of a world that
     * no longer existed.
     *
     * <p>The texture itself is deliberately <em>not</em> freed here, which is the whole
     * reason this is not just a call to {@code invalidate}. Deleting a world plays the
     * tile apart into shards, and those shards are drawn from this very texture for the
     * next second — freeing it now would leave the animation drawing a texture that had
     * been thrown away. Holding it costs a few hundred kilobytes until either the name is
     * reused, at which point {@code loadTexture} replaces it and frees the old one, or
     * the game closes. That is a bounded, self-healing leak in exchange for not having to
     * thread the shards' lifetime through this class.
     */
    public static void forget(String folderName) {
        TEXTURES.remove(folderName);
        MISSING.remove(folderName);
    }

    /**
     * Forgets every folder that is not in {@code live} — the worlds that still exist.
     *
     * <p>{@link #forget} covers a world deleted through this mod's own list, which is
     * where it usually happens and not where it can only happen: vanilla's world screen
     * is still reachable with {@code replaceWorldList} off, another mod may remove a
     * save, and a folder can simply be deleted on disk while the game is running. Every
     * one of those leaves an entry behind under a name Minecraft will hand out again.
     *
     * <p>Called when the world list is read rather than when a picture is looked up. That
     * is the point: checking the file still exists on every cache hit would mean a disk
     * lookup per tile per frame, which is the entire thing these caches were added to
     * avoid. Once per opening of the list costs nothing and catches all of it.
     *
     * <p>Unlike {@link #forget} this does free the textures, because here there is nothing
     * still drawing them — a world dropped by this path went while the list was closed,
     * so it has no shards in flight.
     */
    public static void retainOnly(Set<String> live) {
        MISSING.retainAll(live);
        // Collected before removing: taking entries out of the map while walking its own
        // key set through an iterator that is not the one doing the removing is how a
        // ConcurrentModificationException happens.
        List<String> stale = new ArrayList<String>();
        for (String folder : TEXTURES.keySet()) {
            if (!live.contains(folder)) {
                stale.add(folder);
            }
        }
        for (int i = 0; i < stale.size(); i++) {
            invalidate(stale.get(i));
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
            GlStateManager.bindTexture(fb.framebufferTexture);
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
