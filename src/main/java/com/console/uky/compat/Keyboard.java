package com.console.uky.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * LWJGL 2's Keyboard, as this mod's screens read it.
 *
 * <p>Key codes stay LWJGL 2's: the screens compare against them as bare numbers —
 * {@code keyCode == 1} for Escape, 28 for Enter, 200 for Up — so {@link GuiScreen}
 * translates GLFW's codes into these on the way in, and {@link #isKeyDown} translates
 * them back.
 */
public final class Keyboard {

    public static final int KEY_NONE = 0;
    public static final int KEY_ESCAPE = 1;
    public static final int KEY_1 = 2, KEY_2 = 3, KEY_3 = 4, KEY_4 = 5, KEY_5 = 6,
            KEY_6 = 7, KEY_7 = 8, KEY_8 = 9, KEY_9 = 10, KEY_0 = 11;
    public static final int KEY_MINUS = 12, KEY_EQUALS = 13, KEY_BACK = 14, KEY_TAB = 15;
    public static final int KEY_Q = 16, KEY_W = 17, KEY_E = 18, KEY_R = 19, KEY_T = 20,
            KEY_Y = 21, KEY_U = 22, KEY_I = 23, KEY_O = 24, KEY_P = 25;
    public static final int KEY_LBRACKET = 26, KEY_RBRACKET = 27, KEY_RETURN = 28, KEY_LCONTROL = 29;
    public static final int KEY_A = 30, KEY_S = 31, KEY_D = 32, KEY_F = 33, KEY_G = 34,
            KEY_H = 35, KEY_J = 36, KEY_K = 37, KEY_L = 38;
    public static final int KEY_SEMICOLON = 39, KEY_APOSTROPHE = 40, KEY_GRAVE = 41,
            KEY_LSHIFT = 42, KEY_BACKSLASH = 43;
    public static final int KEY_Z = 44, KEY_X = 45, KEY_C = 46, KEY_V = 47, KEY_B = 48,
            KEY_N = 49, KEY_M = 50;
    public static final int KEY_COMMA = 51, KEY_PERIOD = 52, KEY_SLASH = 53, KEY_RSHIFT = 54,
            KEY_MULTIPLY = 55, KEY_LMENU = 56, KEY_SPACE = 57, KEY_CAPITAL = 58;
    public static final int KEY_F1 = 59, KEY_F2 = 60, KEY_F3 = 61, KEY_F4 = 62, KEY_F5 = 63,
            KEY_F6 = 64, KEY_F7 = 65, KEY_F8 = 66, KEY_F9 = 67, KEY_F10 = 68,
            KEY_F11 = 87, KEY_F12 = 88;
    public static final int KEY_NUMPADENTER = 156, KEY_RCONTROL = 157, KEY_RMENU = 184;
    public static final int KEY_HOME = 199, KEY_UP = 200, KEY_PRIOR = 201, KEY_LEFT = 203,
            KEY_RIGHT = 205, KEY_END = 207, KEY_DOWN = 208, KEY_NEXT = 209, KEY_INSERT = 210,
            KEY_DELETE = 211, KEY_LMETA = 219, KEY_RMETA = 220;

    private static final int[] FROM_GLFW = new int[GLFW.GLFW_KEY_LAST + 1];
    private static final int[] TO_GLFW = new int[256];

    private static void map(int glfw, int lwjgl) {
        FROM_GLFW[glfw] = lwjgl;
        TO_GLFW[lwjgl] = glfw;
    }

    static {
        map(GLFW.GLFW_KEY_ESCAPE, KEY_ESCAPE);
        for (int i = 1; i <= 9; i++) {
            map(GLFW.GLFW_KEY_0 + i, KEY_1 + i - 1);
        }
        map(GLFW.GLFW_KEY_0, KEY_0);
        map(GLFW.GLFW_KEY_MINUS, KEY_MINUS);
        map(GLFW.GLFW_KEY_EQUAL, KEY_EQUALS);
        map(GLFW.GLFW_KEY_BACKSPACE, KEY_BACK);
        map(GLFW.GLFW_KEY_TAB, KEY_TAB);
        String row1 = "QWERTYUIOP";
        for (int i = 0; i < row1.length(); i++) {
            map(row1.charAt(i), KEY_Q + i);
        }
        map(GLFW.GLFW_KEY_LEFT_BRACKET, KEY_LBRACKET);
        map(GLFW.GLFW_KEY_RIGHT_BRACKET, KEY_RBRACKET);
        map(GLFW.GLFW_KEY_ENTER, KEY_RETURN);
        map(GLFW.GLFW_KEY_LEFT_CONTROL, KEY_LCONTROL);
        String row2 = "ASDFGHJKL";
        for (int i = 0; i < row2.length(); i++) {
            map(row2.charAt(i), KEY_A + i);
        }
        map(GLFW.GLFW_KEY_SEMICOLON, KEY_SEMICOLON);
        map(GLFW.GLFW_KEY_APOSTROPHE, KEY_APOSTROPHE);
        map(GLFW.GLFW_KEY_GRAVE_ACCENT, KEY_GRAVE);
        map(GLFW.GLFW_KEY_LEFT_SHIFT, KEY_LSHIFT);
        map(GLFW.GLFW_KEY_BACKSLASH, KEY_BACKSLASH);
        String row3 = "ZXCVBNM";
        for (int i = 0; i < row3.length(); i++) {
            map(row3.charAt(i), KEY_Z + i);
        }
        map(GLFW.GLFW_KEY_COMMA, KEY_COMMA);
        map(GLFW.GLFW_KEY_PERIOD, KEY_PERIOD);
        map(GLFW.GLFW_KEY_SLASH, KEY_SLASH);
        map(GLFW.GLFW_KEY_RIGHT_SHIFT, KEY_RSHIFT);
        map(GLFW.GLFW_KEY_KP_MULTIPLY, KEY_MULTIPLY);
        map(GLFW.GLFW_KEY_LEFT_ALT, KEY_LMENU);
        map(GLFW.GLFW_KEY_SPACE, KEY_SPACE);
        map(GLFW.GLFW_KEY_CAPS_LOCK, KEY_CAPITAL);
        for (int i = 0; i < 10; i++) {
            map(GLFW.GLFW_KEY_F1 + i, KEY_F1 + i);
        }
        map(GLFW.GLFW_KEY_F11, KEY_F11);
        map(GLFW.GLFW_KEY_F12, KEY_F12);
        map(GLFW.GLFW_KEY_KP_ENTER, KEY_NUMPADENTER);
        map(GLFW.GLFW_KEY_RIGHT_CONTROL, KEY_RCONTROL);
        map(GLFW.GLFW_KEY_RIGHT_ALT, KEY_RMENU);
        map(GLFW.GLFW_KEY_HOME, KEY_HOME);
        map(GLFW.GLFW_KEY_UP, KEY_UP);
        map(GLFW.GLFW_KEY_PAGE_UP, KEY_PRIOR);
        map(GLFW.GLFW_KEY_LEFT, KEY_LEFT);
        map(GLFW.GLFW_KEY_RIGHT, KEY_RIGHT);
        map(GLFW.GLFW_KEY_END, KEY_END);
        map(GLFW.GLFW_KEY_DOWN, KEY_DOWN);
        map(GLFW.GLFW_KEY_PAGE_DOWN, KEY_NEXT);
        map(GLFW.GLFW_KEY_INSERT, KEY_INSERT);
        map(GLFW.GLFW_KEY_DELETE, KEY_DELETE);
        map(GLFW.GLFW_KEY_LEFT_SUPER, KEY_LMETA);
        map(GLFW.GLFW_KEY_RIGHT_SUPER, KEY_RMETA);
    }

    private static int eventKey;
    private static char eventChar;
    private static boolean eventState;

    private Keyboard() {
    }

    public static int fromGlfw(int glfw) {
        return glfw >= 0 && glfw < FROM_GLFW.length ? FROM_GLFW[glfw] : KEY_NONE;
    }

    public static int toGlfw(int lwjgl) {
        return lwjgl > 0 && lwjgl < TO_GLFW.length && TO_GLFW[lwjgl] != 0
                ? TO_GLFW[lwjgl] : GLFW.GLFW_KEY_UNKNOWN;
    }

    static void setEvent(int key, char c, boolean down) {
        eventKey = key;
        eventChar = c;
        eventState = down;
    }

    public static int getEventKey() {
        return eventKey;
    }

    public static char getEventCharacter() {
        return eventChar;
    }

    public static boolean getEventKeyState() {
        return eventState;
    }

    public static boolean isKeyDown(int key) {
        int glfw = toGlfw(key);
        return glfw != GLFW.GLFW_KEY_UNKNOWN
                && InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), glfw);
    }

    public static String getKeyName(int key) {
        int glfw = toGlfw(key);
        return glfw == GLFW.GLFW_KEY_UNKNOWN ? "NONE"
                : InputConstants.Type.KEYSYM.getOrCreate(glfw).getDisplayName().getString();
    }

    public static void enableRepeatEvents(boolean enable) {
        // GLFW repeats keys on its own; screens get every repeat as a press.
    }
}
