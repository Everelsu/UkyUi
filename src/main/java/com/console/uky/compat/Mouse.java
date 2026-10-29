package com.console.uky.compat;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * LWJGL 2's Mouse, as this mod's screens read it: the pointer in framebuffer pixels
 * with the origin at the bottom left, and the wheel of the event being handled.
 */
public final class Mouse {

    private static int eventWheel;

    private Mouse() {
    }

    static void setEventWheel(int wheel) {
        eventWheel = wheel;
    }

    /** One notch is 120, as LWJGL 2 reported it on Windows. */
    public static int getEventDWheel() {
        return eventWheel;
    }

    private static double scaleX() {
        Minecraft mc = Minecraft.getInstance();
        return (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getScreenWidth());
    }

    private static double scaleY() {
        Minecraft mc = Minecraft.getInstance();
        return (double) mc.getWindow().getHeight() / Math.max(1, mc.getWindow().getScreenHeight());
    }

    public static int getX() {
        return (int) (Minecraft.getInstance().mouseHandler.xpos() * scaleX());
    }

    public static int getY() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getWindow().getHeight() - 1 - (int) (mc.mouseHandler.ypos() * scaleY());
    }

    public static int getEventX() {
        return getX();
    }

    public static int getEventY() {
        return getY();
    }

    public static boolean isButtonDown(int button) {
        return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), button)
                == GLFW.GLFW_PRESS;
    }
}
