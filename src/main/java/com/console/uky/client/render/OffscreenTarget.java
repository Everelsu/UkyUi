package com.console.uky.client.render;

import com.console.uky.UkyUI;
import com.console.uky.compat.GL11;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;

/**
 * A texture the black hole is traced into, at a resolution of its own.
 *
 * <p>Tracing at a resolution other than the window's is what lets the trace be
 * supersampled — or cheapened — independently of everything drawn around it, and
 * tracing a few times a second rather than every frame is what lets the menu stay
 * smooth on hardware that cannot afford the trace at sixty frames. Both need the
 * picture to outlive the frame it was drawn on, which is what this holds.
 *
 * <p>A vanilla {@link TextureTarget} underneath; framebuffers are core in 1.21, so
 * there is no unsupported case left to fall back from.
 */
public final class OffscreenTarget {

    private TextureTarget target;
    private boolean broken;

    public static boolean isSupported() {
        return true;
    }

    public boolean isUsable() {
        return !this.broken;
    }

    public boolean hasContent() {
        return this.target != null;
    }

    public boolean matches(int width, int height) {
        return this.target != null && this.target.width == width && this.target.height == height;
    }

    public int getWidth() {
        return this.target == null ? 0 : this.target.width;
    }

    public int getHeight() {
        return this.target == null ? 0 : this.target.height;
    }

    /**
     * Starts drawing into the target, resizing it first if needed.
     *
     * @return false if the target is unusable; draw straight to the screen instead
     */
    public boolean begin(int width, int height) {
        if (this.broken || width <= 0 || height <= 0) {
            return false;
        }
        try {
            if (this.target == null) {
                UkyUI.LOGGER.info("Black hole trace buffer: {}x{}", width, height);
                this.target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
                this.target.setFilterMode(GL11.GL_LINEAR);
            } else if (!matches(width, height)) {
                UkyUI.LOGGER.info("Black hole trace buffer: {}x{}", width, height);
                this.target.resize(width, height, Minecraft.ON_OSX);
                this.target.setFilterMode(GL11.GL_LINEAR);
            }
            this.target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            this.target.clear(Minecraft.ON_OSX);
            this.target.bindWrite(true);
            return true;
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Offscreen target unavailable; drawing at full size", t);
            this.broken = true;
            release();
            return false;
        }
    }

    /** Goes back to drawing on the screen. */
    public void end() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        main.bindWrite(true);
    }

    public void bindTexture() {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.target.getColorTextureId());
    }

    private void release() {
        if (this.target != null) {
            this.target.destroyBuffers();
            this.target = null;
        }
    }
}
