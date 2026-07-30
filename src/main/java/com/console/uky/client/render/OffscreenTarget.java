package com.console.uky.client.render;

import com.console.uky.UkyUI;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GLContext;

import java.nio.IntBuffer;

/**
 * A colour-only framebuffer for drawing something once at reduced size and
 * stretching the result.
 *
 * The black hole is the most expensive thing on the menu by a wide margin: a
 * light path is integrated for every pixel it covers, so the cost is exactly
 * proportional to area. Halving each axis quarters it, and for a soft glowing
 * object the upscale is close to invisible.
 *
 * <p>Allocation happens once and is reused. It is only rebuilt when the requested
 * size actually changes — recreating textures and framebuffers on every frame, or
 * on every window focus change, is a classic way to make a menu stutter.
 */
public final class OffscreenTarget {

    private int framebuffer = -1;
    private int texture = -1;
    private int width;
    private int height;
    private boolean broken;

    private final int[] previousViewport = new int[4];
    private int previousFramebuffer;
    private boolean previousScissor;

    private static Boolean supported;

    /** EXT_framebuffer_object is the one every driver that runs 1.7.10 has. */
    public static boolean isSupported() {
        if (supported == null) {
            try {
                supported = Boolean.valueOf(
                        GLContext.getCapabilities().GL_EXT_framebuffer_object
                                || GLContext.getCapabilities().OpenGL30);
            } catch (Throwable t) {
                supported = Boolean.FALSE;
            }
        }
        return supported.booleanValue();
    }

    public boolean isUsable() {
        return !this.broken && isSupported();
    }

    /** True once something has been drawn into it, so the texture is safe to sample. */
    public boolean hasContent() {
        return this.texture >= 0 && this.width > 0 && this.height > 0;
    }

    /** Whether the existing buffer is already the requested size. */
    public boolean matches(int width, int height) {
        return this.width == width && this.height == height;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    /**
     * Makes this the render target, allocating or resizing only if needed.
     *
     * @return false if it could not be set up, in which case the caller should
     *         draw straight to the screen
     */
    public boolean begin(int width, int height) {
        if (this.broken || !isSupported() || width <= 0 || height <= 0) {
            return false;
        }
        try {
            if (this.framebuffer < 0 || width != this.width || height != this.height) {
                allocate(width, height);
            }

            scratch.clear();
            GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT, scratch);
            this.previousFramebuffer = scratch.get(0);

            scratch.clear();
            GL11.glGetInteger(GL11.GL_VIEWPORT, scratch);
            for (int i = 0; i < 4; i++) {
                this.previousViewport[i] = scratch.get(i);
            }

            EXTFramebufferObject.glBindFramebufferEXT(
                    EXTFramebufferObject.GL_FRAMEBUFFER_EXT, this.framebuffer);
            GL11.glViewport(0, 0, this.width, this.height);

            // A scissor rect left over from a clipped list would survive into the
            // clear and leave the rest of the buffer holding the last frame.
            this.previousScissor = GL11.glGetBoolean(GL11.GL_SCISSOR_TEST);
            if (this.previousScissor) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }

            GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            return true;
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Offscreen target unavailable; drawing at full size", t);
            this.broken = true;
            release();
            return false;
        }
    }

    /** Restores the previous target, viewport and scissor state. */
    public void end() {
        EXTFramebufferObject.glBindFramebufferEXT(
                EXTFramebufferObject.GL_FRAMEBUFFER_EXT, this.previousFramebuffer);
        GL11.glViewport(this.previousViewport[0], this.previousViewport[1],
                this.previousViewport[2], this.previousViewport[3]);
        if (this.previousScissor) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
    }

    public void bindTexture() {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.texture);
    }

    private void allocate(int width, int height) {
        release();
        this.width = width;
        this.height = height;
        // Logged because it is the single number that decides how the black hole
        // looks, and it is otherwise invisible.
        UkyUI.LOGGER.info("Black hole trace buffer: {}x{}",
                Integer.valueOf(width), Integer.valueOf(height));

        this.texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.texture);
        // Linear, so the upscale is a smooth stretch rather than blocks.
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);

        this.framebuffer = EXTFramebufferObject.glGenFramebuffersEXT();
        EXTFramebufferObject.glBindFramebufferEXT(
                EXTFramebufferObject.GL_FRAMEBUFFER_EXT, this.framebuffer);
        EXTFramebufferObject.glFramebufferTexture2DEXT(
                EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT,
                GL11.GL_TEXTURE_2D, this.texture, 0);

        int status = EXTFramebufferObject.glCheckFramebufferStatusEXT(
                EXTFramebufferObject.GL_FRAMEBUFFER_EXT);
        if (status != EXTFramebufferObject.GL_FRAMEBUFFER_COMPLETE_EXT) {
            throw new IllegalStateException("Framebuffer incomplete: " + status);
        }
        EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, 0);
    }

    private void release() {
        if (this.framebuffer >= 0) {
            EXTFramebufferObject.glDeleteFramebuffersEXT(this.framebuffer);
            this.framebuffer = -1;
        }
        if (this.texture >= 0) {
            GL11.glDeleteTextures(this.texture);
            this.texture = -1;
        }
    }

    // A single reusable buffer: glGetInteger needs one and allocating per frame
    // would defeat the point of the exercise. LWJGL wants at least 16 slots.
    private static final IntBuffer scratch = BufferUtils.createIntBuffer(16);
}
