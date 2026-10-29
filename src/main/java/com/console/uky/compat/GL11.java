package com.console.uky.compat;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.VertexSorting;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * The fixed-function half of OpenGL 1.1, on top of a core-profile context.
 *
 * <p>This mod was written against 1.7.10, which draws through glBegin/glVertex and the
 * matrix stack. 1.21 has neither: the context is core profile, and every draw goes
 * through a shader that reads its matrices and colour from {@link RenderSystem}. Rather
 * than rewrite every primitive in the interface, the calls that no longer exist are
 * emulated here and everything else falls through to LWJGL's own GL11, which this class
 * extends — so swapping the import is the whole of porting a file that only draws.
 *
 * <ul>
 *   <li>glBegin..glEnd collects vertices and draws them through the position-colour or
 *       position-texture-colour shader on glEnd. Quads, triangles, fans, strips, quad
 *       strips and lines are understood; lines become thin quads, since the core
 *       profile has no wide lines.</li>
 *   <li>The matrix calls act on {@link RenderSystem#getModelViewStack()}, which is what
 *       every shader — text included — reads its model-view matrix from.</li>
 *   <li>glColor4f is the shader colour, multiplied into whatever glColor said per vertex.</li>
 *   <li>Capabilities that only exist in the fixed-function pipeline — texturing, alpha
 *       test, lighting, fog, smooth shading — are remembered or ignored. The ones that
 *       still exist go through {@link RenderSystem} so its state cache stays true.</li>
 * </ul>
 */
public class GL11 extends org.lwjgl.opengl.GL11 {

    protected GL11() {
    }

    // ---------------------------------------------------------- capabilities --

    private static boolean texturing = true;
    private static boolean scissoring;
    private static int scissorX, scissorY, scissorW, scissorH;

    public static void glEnable(int cap) {
        switch (cap) {
            case GL_TEXTURE_2D: texturing = true; break;
            case GL_BLEND: RenderSystem.enableBlend(); break;
            case GL_DEPTH_TEST: RenderSystem.enableDepthTest(); break;
            case GL_CULL_FACE: RenderSystem.enableCull(); break;
            case GL_SCISSOR_TEST:
                scissoring = true;
                RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);
                break;
            default:
                // Alpha test, lighting, fog, line smoothing, rescale-normal, colour
                // material: fixed-function only. The shaders do their own.
                break;
        }
    }

    public static void glDisable(int cap) {
        switch (cap) {
            case GL_TEXTURE_2D: texturing = false; break;
            case GL_BLEND: RenderSystem.disableBlend(); break;
            case GL_DEPTH_TEST: RenderSystem.disableDepthTest(); break;
            case GL_CULL_FACE: RenderSystem.disableCull(); break;
            case GL_SCISSOR_TEST:
                scissoring = false;
                RenderSystem.disableScissor();
                break;
            default:
                break;
        }
    }

    public static boolean glIsEnabled(int cap) {
        switch (cap) {
            case GL_TEXTURE_2D: return texturing;
            case GL_SCISSOR_TEST: return scissoring;
            case GL_BLEND:
            case GL_DEPTH_TEST:
            case GL_CULL_FACE:
            case GL_STENCIL_TEST:
            case GL_POLYGON_OFFSET_FILL:
                return org.lwjgl.opengl.GL11.glIsEnabled(cap);
            default:
                // Alpha test, lighting, fog: there is no such state to ask about, and
                // asking is an INVALID_ENUM.
                return false;
        }
    }

    public static boolean glGetBoolean(int cap) {
        return glIsEnabled(cap);
    }

    public static void glScissor(int x, int y, int w, int h) {
        scissorX = x;
        scissorY = y;
        scissorW = Math.max(0, w);
        scissorH = Math.max(0, h);
        if (scissoring) {
            RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);
        }
    }

    public static void glBlendFunc(int src, int dst) {
        RenderSystem.blendFunc(src, dst);
    }

    public static void glDepthMask(boolean flag) {
        RenderSystem.depthMask(flag);
    }

    public static void glDepthFunc(int func) {
        RenderSystem.depthFunc(func);
    }

    public static void glColorMask(boolean r, boolean g, boolean b, boolean a) {
        RenderSystem.colorMask(r, g, b, a);
    }

    public static void glBindTexture(int target, int texture) {
        RenderSystem.bindTexture(texture);
        boundTexture = texture;
    }

    public static void glShadeModel(int mode) {
    }

    public static void glAlphaFunc(int func, float ref) {
    }

    private static float lineWidth = 1.0F;

    public static void glLineWidth(float width) {
        lineWidth = width;
    }

    // ---------------------------------------------------------------- colour --

    /**
     * The current colour, as the fixed-function pipeline had it: every glVertex after
     * this takes it, inside or outside glBegin. The shader colour is left alone, since
     * vanilla's own drawing relies on it staying white.
     */
    public static void glColor4f(float r, float g, float b, float a) {
        colour = (channel(a) << 24) | (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    public static void glColor3f(float r, float g, float b) {
        glColor4f(r, g, b, 1.0F);
    }

    private static int channel(float v) {
        return v <= 0.0F ? 0 : (v >= 1.0F ? 255 : (int) (v * 255.0F + 0.5F));
    }

    /** The current colour, ARGB. */
    static int currentColour() {
        return colour;
    }

    // -------------------------------------------------------------- matrices --

    private static int matrixMode = GL_MODELVIEW;
    private static final java.util.ArrayDeque<Matrix4f> projections = new java.util.ArrayDeque<Matrix4f>();

    private static Matrix4fStack stack() {
        return RenderSystem.getModelViewStack();
    }

    private static boolean projection() {
        return matrixMode == GL_PROJECTION;
    }

    /** Applies {@code op} to the current projection and hands the result back to RenderSystem. */
    private static void editProjection(java.util.function.Consumer<Matrix4f> op) {
        Matrix4f m = new Matrix4f(RenderSystem.getProjectionMatrix());
        op.accept(m);
        RenderSystem.setProjectionMatrix(m, VertexSorting.ORTHOGRAPHIC_Z);
    }

    public static void glMatrixMode(int mode) {
        matrixMode = mode;
    }

    public static void glPushMatrix() {
        if (projection()) {
            projections.push(new Matrix4f(RenderSystem.getProjectionMatrix()));
            return;
        }
        stack().pushMatrix();
    }

    public static void glPopMatrix() {
        if (projection()) {
            Matrix4f m = projections.poll();
            if (m != null) {
                RenderSystem.setProjectionMatrix(m, VertexSorting.ORTHOGRAPHIC_Z);
            }
            return;
        }
        stack().popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    public static void glLoadIdentity() {
        if (projection()) {
            RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
            return;
        }
        stack().identity();
        RenderSystem.applyModelViewMatrix();
    }

    public static void glOrtho(double l, double r, double b, double t, double n, double f) {
        if (projection()) {
            editProjection(m -> m.ortho((float) l, (float) r, (float) b, (float) t, (float) n, (float) f));
            return;
        }
        stack().ortho((float) l, (float) r, (float) b, (float) t, (float) n, (float) f);
        RenderSystem.applyModelViewMatrix();
    }

    public static void glTranslatef(float x, float y, float z) {
        if (projection()) {
            editProjection(m -> m.translate(x, y, z));
            return;
        }
        stack().translate(x, y, z);
        RenderSystem.applyModelViewMatrix();
    }

    public static void glTranslated(double x, double y, double z) {
        glTranslatef((float) x, (float) y, (float) z);
    }

    public static void glScalef(float x, float y, float z) {
        if (projection()) {
            editProjection(m -> m.scale(x, y, z));
            return;
        }
        stack().scale(x, y, z);
        RenderSystem.applyModelViewMatrix();
    }

    public static void glScaled(double x, double y, double z) {
        glScalef((float) x, (float) y, (float) z);
    }

    public static void glRotatef(float degrees, float x, float y, float z) {
        float rad = (float) Math.toRadians(degrees);
        if (projection()) {
            editProjection(m -> m.rotate(rad, x, y, z));
            return;
        }
        stack().rotate(rad, x, y, z);
        RenderSystem.applyModelViewMatrix();
    }

    // ------------------------------------------------------------ framebuffer --

    public static void glViewport(int x, int y, int w, int h) {
        RenderSystem.viewport(x, y, w, h);
    }

    public static void glClearColor(float r, float g, float b, float a) {
        RenderSystem.clearColor(r, g, b, a);
    }

    public static void glClear(int mask) {
        RenderSystem.clear(mask, Minecraft.ON_OSX);
    }

    // -------------------------------------------------------- immediate mode --

    private static int boundTexture;

    /** Binds a game texture for the next textured draw. */
    public static void bindTexture(ResourceLocation texture) {
        boundTexture = Minecraft.getInstance().getTextureManager().getTexture(texture).getId();
        RenderSystem.bindTexture(boundTexture);
    }

    private static final int MAX = 1 << 16;
    private static final float[] xs = new float[MAX];
    private static final float[] ys = new float[MAX];
    private static final float[] zs = new float[MAX];
    private static final float[] us = new float[MAX];
    private static final float[] vs = new float[MAX];
    private static final int[] cs = new int[MAX];
    private static int count;
    private static int mode = -1;
    private static float u, v;
    private static int colour = 0xFFFFFFFF;

    public static void glBegin(int glMode) {
        checkErrors("before glBegin");
        mode = glMode;
        count = 0;
    }

    /**
     * -Duky.glCheck=true: logs where a GL error was first seen, with the stack, a few
     * times. Errors are sticky until read, so "before glBegin" means some call since the
     * previous check raised it.
     */
    private static final boolean CHECK = Boolean.getBoolean("uky.glCheck");
    private static int reported;

    static void checkErrors(String where) {
        if (!CHECK || reported > 8) {
            return;
        }
        int error = org.lwjgl.opengl.GL11.glGetError();
        if (error != GL_NO_ERROR) {
            reported++;
            com.console.uky.UkyUI.LOGGER.warn("GL error 0x{} {}", Integer.toHexString(error), where,
                    new Throwable("trace"));
        }
    }

    /** Sets the current colour from ARGB, as glColor4f does. */
    public static void vertexColour(int argb) {
        colour = argb;
    }

    public static void glTexCoord2f(float s, float t) {
        u = s;
        v = t;
    }

    public static void glTexCoord2d(double s, double t) {
        glTexCoord2f((float) s, (float) t);
    }

    public static void glVertex2f(float x, float y) {
        glVertex3f(x, y, 0.0F);
    }

    public static void glVertex2d(double x, double y) {
        glVertex3f((float) x, (float) y, 0.0F);
    }

    public static void glVertex3f(float x, float y, float z) {
        if (count >= MAX) {
            return;
        }
        xs[count] = x;
        ys[count] = y;
        zs[count] = z;
        us[count] = u;
        vs[count] = v;
        cs[count] = colour;
        count++;
    }

    public static void glVertex3d(double x, double y, double z) {
        glVertex3f((float) x, (float) y, (float) z);
    }

    public static void glEnd() {
        int m = mode;
        mode = -1;
        if (count == 0) {
            return;
        }
        // A program of our own reads position and texture coordinate, nothing else.
        boolean textured = texturing || program != 0;
        VertexFormat.Mode vm;
        switch (m) {
            case GL_QUADS: vm = VertexFormat.Mode.QUADS; break;
            case GL_TRIANGLES: vm = VertexFormat.Mode.TRIANGLES; break;
            case GL_TRIANGLE_FAN: vm = VertexFormat.Mode.TRIANGLE_FAN; break;
            // A quad strip's vertices come in exactly the order a triangle strip wants.
            case GL_TRIANGLE_STRIP:
            case GL_QUAD_STRIP: vm = VertexFormat.Mode.TRIANGLE_STRIP; break;
            case GL_LINES: vm = VertexFormat.Mode.QUADS; break;
            case GL_LINE_STRIP:
            case GL_LINE_LOOP: vm = VertexFormat.Mode.QUADS; break;
            default: count = 0; return;
        }
        BufferBuilder buffer = Tesselator.getInstance().begin(vm,
                textured ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR);
        if (m == GL_LINES || m == GL_LINE_STRIP || m == GL_LINE_LOOP) {
            int step = m == GL_LINES ? 2 : 1;
            int last = m == GL_LINE_LOOP ? count : count - 1;
            for (int i = 0; i + (m == GL_LINES ? 1 : 0) < count && i < last; i += step) {
                int j = (i + 1) % count;
                lineQuad(buffer, i, j, textured);
            }
        } else {
            for (int i = 0; i < count; i++) {
                put(buffer, i, xs[i], ys[i], textured);
            }
        }
        count = 0;
        draw(buffer, textured);
        checkErrors("after glEnd");
    }

    private static void put(BufferBuilder buffer, int i, float x, float y, boolean textured) {
        int c = cs[i];
        buffer.addVertex(x, y, zs[i]);
        if (textured) {
            buffer.setUv(us[i], vs[i]);
        }
        buffer.setColor(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, c >>> 24);
    }

    /** Lines are emulated as quads lineWidth/2 units either side, in GUI units. */
    private static void lineQuad(BufferBuilder buffer, int a, int b, boolean textured) {
        float dx = xs[b] - xs[a];
        float dy = ys[b] - ys[a];
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-4F) {
            return;
        }
        float half = Math.max(0.5F, lineWidth * 0.5F) / pixelsPerUnit();
        float nx = -dy / len * half;
        float ny = dx / len * half;
        put(buffer, a, xs[a] - nx, ys[a] - ny, textured);
        put(buffer, b, xs[b] - nx, ys[b] - ny, textured);
        put(buffer, b, xs[b] + nx, ys[b] + ny, textured);
        put(buffer, a, xs[a] + nx, ys[a] + ny, textured);
    }

    private static float pixelsPerUnit() {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        return scale <= 0.0D ? 1.0F : (float) scale;
    }

    /**
     * A program of this mod's own, bound with {@link #useProgram}. While one is, the
     * immediate-mode draws go through it instead of vanilla's shaders; it is handed the
     * same two matrices vanilla's are, under the same names.
     */
    private static int program;

    public static void useProgram(int id) {
        program = id;
        GlStateManager._glUseProgram(id);
        if (id != 0) {
            setMatrix(id, "ProjMat", RenderSystem.getProjectionMatrix());
            setMatrix(id, "ModelViewMat", RenderSystem.getModelViewMatrix());
        }
    }

    private static final java.nio.FloatBuffer MATRIX = org.lwjgl.BufferUtils.createFloatBuffer(16);

    private static void setMatrix(int id, String name, Matrix4f m) {
        int loc = org.lwjgl.opengl.GL20.glGetUniformLocation(id, name);
        if (loc >= 0) {
            MATRIX.clear();
            m.get(MATRIX);
            org.lwjgl.opengl.GL20.glUniformMatrix4fv(loc, false, MATRIX);
        }
    }

    static void draw(BufferBuilder buffer, boolean textured) {
        MeshData mesh = buffer.build();
        if (mesh == null) {
            return;
        }
        if (program != 0) {
            // Matrices re-sent per draw: the caller may have moved them since binding.
            setMatrix(program, "ProjMat", RenderSystem.getProjectionMatrix());
            setMatrix(program, "ModelViewMat", RenderSystem.getModelViewMatrix());
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            BufferUploader.draw(mesh);
            return;
        }
        if (textured) {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, boundTexture);
        } else {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
        }
        // Everything this interface draws is flat and drawn in order; the depth buffer
        // only ever got in the way of it, and with culling on, the triangle fans — wound
        // clockwise on a y-down screen — would be thrown away.
        // Every render type sets the depth test it wants for itself, so leaving it off
        // here disturbs nothing drawn after.
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        BufferUploader.drawWithShader(mesh);
    }
}
