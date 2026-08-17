package com.console.uky.client.render;

import com.console.uky.UkyUI;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal GLSL program wrapper.
 *
 * Everything here fails soft. Shaders are the fast path, not a requirement: if
 * the driver is too old, the source does not compile, or anything else goes
 * wrong, {@link #isUsable()} stays false and the caller falls back to the
 * precomputed tables. A menu backdrop is never worth a black screen.
 */
public final class ShaderProgram {

    private int program = -1;
    private boolean usable;
    private final Map<String, Integer> uniforms = new HashMap<String, Integer>();

    /** Whether this machine can run shaders at all. Cached: it cannot change. */
    private static Boolean supported;

    public static boolean isSupported() {
        if (supported == null) {
            try {
                supported = Boolean.valueOf(GLContext.getCapabilities().OpenGL21
                        || GLContext.getCapabilities().GL_ARB_shader_objects);
            } catch (Throwable t) {
                // Deliberately not cached. The usual way to land here is to ask off the
                // render thread: LWJGL keeps capabilities per thread, and during mod
                // loading the thread that owns the GL context is the loading screen's,
                // so the question has no answer yet rather than the answer "no".
                // Remembering that as a "no" would send the whole session down the
                // traced-table path on hardware that runs the shader perfectly well.
                return false;
            }
        }
        return supported.booleanValue();
    }

    /**
     * Whether we have actually asked the driver and been told no.
     *
     * Distinct from {@code !isSupported()}, which is also false when nobody could ask
     * yet. Callers deciding whether to spend CPU on a fallback want this one: doing
     * that work on a maybe is how a menu backdrop ends up costing a minute of the
     * loading it was supposed to be shown during.
     */
    public static boolean isKnownUnsupported() {
        return supported != null && !supported.booleanValue();
    }

    public ShaderProgram(ResourceLocation vertex, ResourceLocation fragment) {
        if (!isSupported()) {
            return;
        }
        int vs = -1;
        int fs = -1;
        try {
            vs = compile(GL20.GL_VERTEX_SHADER, read(vertex), vertex);
            fs = compile(GL20.GL_FRAGMENT_SHADER, read(fragment), fragment);
            if (vs < 0 || fs < 0) {
                return;
            }
            this.program = GL20.glCreateProgram();
            GL20.glAttachShader(this.program, vs);
            GL20.glAttachShader(this.program, fs);
            GL20.glLinkProgram(this.program);
            if (GL20.glGetProgrami(this.program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                UkyUI.LOGGER.error("Black hole shader link failed: "
                        + GL20.glGetProgramInfoLog(this.program, 4096));
                GL20.glDeleteProgram(this.program);
                this.program = -1;
                return;
            }
            this.usable = true;
        } catch (Throwable t) {
            UkyUI.LOGGER.error("Black hole shader unavailable", t);
            this.usable = false;
        } finally {
            // The program keeps its own copy once linked.
            if (vs >= 0) {
                GL20.glDeleteShader(vs);
            }
            if (fs >= 0) {
                GL20.glDeleteShader(fs);
            }
        }
    }

    /**
     * Reads a shader, from a resource pack if one supplies it and from the mod's own
     * jar otherwise.
     *
     * The resource manager alone is not enough. It only serves a domain once FML has
     * registered the owning jar as a resource pack and the manager has been reloaded,
     * and before that every lookup for {@code uky} misses. In a development run that
     * never shows, because the assets sit loose on the classpath where the default
     * pack finds them at any time; from a built jar they exist only inside the jar,
     * so an early load failed, burnt its retries and left the hole on the baked
     * lensing tables for the rest of the session — which is why the release build had
     * no photon ring while the development one did.
     *
     * <p>The classpath is where the file certainly is, so it is the backstop. The
     * resource manager is still tried first, so a resource pack can still override
     * the shader.
     */
    private static String read(ResourceLocation location) throws Exception {
        InputStream stream = null;
        try {
            stream = Minecraft.getMinecraft().getResourceManager()
                    .getResource(location).getInputStream();
        } catch (Exception fromPacks) {
            String path = "/assets/" + location.getResourceDomain() + "/"
                    + location.getResourcePath();
            stream = ShaderProgram.class.getResourceAsStream(path);
            if (stream == null) {
                throw fromPacks;
            }
        }
        try {
            return IOUtils.toString(stream, Charset.forName("UTF-8"));
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }

    private static int compile(int type, String source, ResourceLocation name) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            UkyUI.LOGGER.error("Shader " + name + " failed to compile: "
                    + GL20.glGetShaderInfoLog(shader, 4096));
            GL20.glDeleteShader(shader);
            return -1;
        }
        return shader;
    }

    public boolean isUsable() {
        return this.usable;
    }

    public void bind() {
        GL20.glUseProgram(this.program);
    }

    public static void unbind() {
        GL20.glUseProgram(0);
    }

    private int location(String name) {
        Integer cached = this.uniforms.get(name);
        if (cached == null) {
            cached = Integer.valueOf(GL20.glGetUniformLocation(this.program, name));
            this.uniforms.put(name, cached);
        }
        return cached.intValue();
    }

    public void set(String name, float value) {
        GL20.glUniform1f(location(name), value);
    }

    public void set(String name, int value) {
        GL20.glUniform1i(location(name), value);
    }

    public void set(String name, float x, float y) {
        GL20.glUniform2f(location(name), x, y);
    }

    public void set(String name, float r, float g, float b) {
        GL20.glUniform3f(location(name), r, g, b);
    }

    /** Frees the program. Safe to call when it was never created. */
    public void dispose() {
        if (this.program >= 0) {
            GL20.glDeleteProgram(this.program);
            this.program = -1;
        }
        this.usable = false;
    }

    /**
     * ARB entry points exist on some drivers where the core ones do not; touching
     * the class here keeps the import meaningful and the dependency explicit.
     */
    static void ensureArbLoaded() {
        ARBShaderObjects.class.getName();
    }
}
