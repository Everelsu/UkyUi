package com.console.uky.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.7.10's GuiScreen, on top of 1.21's {@link Screen}.
 *
 * <p>The life cycle maps one to one — init to {@link #initGui()}, render to
 * {@link #drawScreen}, tick to {@link #updateScreen()}, removed to
 * {@link #onGuiClosed()} — and so does input, after two translations. Key codes are
 * turned into LWJGL 2's (see {@link Keyboard}), and a key press and the character it
 * types, which GLFW reports as two events, reach {@link #keyTyped} as two calls: one
 * with the key and no character, one with the character and no key. Mouse positions
 * are passed through {@link #localX}/{@link #localY}, for screens that lay out in a
 * space of their own.
 */
public abstract class GuiScreen extends Screen {

    protected Minecraft mc = Minecraft.getInstance();
    protected FontRenderer fontRendererObj = FontRenderer.get();
    protected List<GuiButton> buttonList = new ArrayList<GuiButton>();
    private GuiButton selectedButton;
    private long lastClickTime;
    private int eventButton = -1;

    /** The graphics of the frame being drawn, for code that wants vanilla's helpers. */
    private static GuiGraphics graphics;

    protected GuiScreen() {
        super(Component.empty());
    }

    public static GuiGraphics graphics() {
        return graphics;
    }

    // ------------------------------------------------------------ lifecycle --

    @Override
    protected final void init() {
        this.mc = this.minecraft;
        this.buttonList.clear();
        adjustSize();
        initGui();
    }

    /** Lets a screen replace the width and height it was given, before it lays out. */
    protected void adjustSize() {
    }

    public void initGui() {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        GuiGraphics previous = graphics;
        graphics = g;
        try {
            drawScreen(mouseX, mouseY, partialTicks);
        } finally {
            graphics = previous;
        }
    }

    /** Draws the buttons, as vanilla's own drawScreen did. */
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        for (int i = 0; i < this.buttonList.size(); i++) {
            this.buttonList.get(i).drawButton(this.mc, mouseX, mouseY);
        }
    }

    /** Nothing: 1.21 draws its blur from here, and these screens bring their own. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
    }

    public void drawDefaultBackground() {
    }

    @Override
    public void tick() {
        updateScreen();
    }

    public void updateScreen() {
    }

    @Override
    public void removed() {
        onGuiClosed();
    }

    public void onGuiClosed() {
    }

    @Override
    public boolean isPauseScreen() {
        return doesGuiPauseGame();
    }

    public boolean doesGuiPauseGame() {
        return true;
    }

    // ---------------------------------------------------------------- mouse --

    protected double localX(double x) {
        return x;
    }

    protected double localY(double y) {
        return y;
    }

    /** Called before every input event is handed on; see MenuScreen. */
    public void handleInput() {
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        handleInput();
        this.eventButton = button;
        this.lastClickTime = System.currentTimeMillis();
        mouseClicked((int) localX(x), (int) localY(y), button);
        return true;
    }

    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return;
        }
        for (int i = 0; i < this.buttonList.size(); i++) {
            GuiButton b = this.buttonList.get(i);
            if (b.mousePressed(this.mc, mouseX, mouseY)) {
                this.selectedButton = b;
                b.func_146113_a(this.mc.getSoundManager());
                actionPerformed(b);
                // The action may have replaced this screen, or rebuilt its buttons.
                if (this.mc.screen != this || !this.buttonList.contains(b)) {
                    break;
                }
            }
        }
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        handleInput();
        this.eventButton = -1;
        int mx = (int) localX(x);
        int my = (int) localY(y);
        if (this.selectedButton != null && button == 0) {
            this.selectedButton.mouseReleased(mx, my);
            this.selectedButton = null;
        }
        mouseMovedOrUp(mx, my, button);
        return true;
    }

    @Override
    public void mouseMoved(double x, double y) {
        if (this.eventButton == -1) {
            mouseMovedOrUp((int) localX(x), (int) localY(y), -1);
        }
    }

    protected void mouseMovedOrUp(int mouseX, int mouseY, int which) {
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        handleInput();
        mouseClickMove((int) localX(x), (int) localY(y), button,
                System.currentTimeMillis() - this.lastClickTime);
        return true;
    }

    protected void mouseClickMove(int mouseX, int mouseY, int button, long sinceClick) {
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        handleInput();
        Mouse.setEventWheel((int) Math.round(scrollY * 120.0D));
        try {
            handleMouseInput();
        } finally {
            Mouse.setEventWheel(0);
        }
        return true;
    }

    /** The wheel, through {@link Mouse#getEventDWheel()}; clicks arrive separately. */
    public void handleMouseInput() {
    }

    // ------------------------------------------------------------- keyboard --

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        handleInput();
        Keyboard.setEvent(Keyboard.fromGlfw(key), '\0', true);
        handleKeyboardInput();
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        handleInput();
        Keyboard.setEvent(Keyboard.KEY_NONE, c, true);
        handleKeyboardInput();
        return true;
    }

    public void handleKeyboardInput() {
        if (Keyboard.getEventKeyState()) {
            keyTyped(Keyboard.getEventCharacter(), Keyboard.getEventKey());
        }
    }

    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.setScreen(null);
        }
    }

    protected void actionPerformed(GuiButton button) {
    }

    // -------------------------------------------------------------- helpers --

    public void drawCenteredString(FontRenderer font, String text, int x, int y, int colour) {
        Gui.drawCenteredString(font, text, x, y, colour);
    }

    public void drawString(FontRenderer font, String text, int x, int y, int colour) {
        Gui.drawString(font, text, x, y, colour);
    }

    public static void drawRect(int x1, int y1, int x2, int y2, int argb) {
        Gui.drawRect(x1, y1, x2, y2, argb);
    }

    public void drawGradientRect(int x1, int y1, int x2, int y2, int top, int bottom) {
        Gui.drawGradientRect(x1, y1, x2, y2, top, bottom);
    }

    public static boolean isCtrlKeyDown() {
        return Screen.hasControlDown();
    }

    public static boolean isShiftKeyDown() {
        return Screen.hasShiftDown();
    }

    public static String getClipboardString() {
        return Minecraft.getInstance().keyboardHandler.getClipboard();
    }

    public static void setClipboardString(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }
}
