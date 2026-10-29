package com.console.uky.compat;

import net.minecraft.util.StringUtil;
import net.minecraft.client.gui.screens.Screen;

/**
 * 1.7.10's GuiTextField: one line of editable text with a caret and a selection.
 *
 * <p>Takes LWJGL 2 key codes and characters as {@link GuiScreen} delivers them — a key
 * press and the character it types as two separate calls — so a screen that forwards
 * {@code keyTyped} to it keeps working unchanged.
 */
public class GuiTextField extends Gui {

    private final FontRenderer font;
    public int xPosition;
    public int yPosition;
    public int width;
    public int height;
    private String text = "";
    private int maxLength = 32;
    private int cursorCounter;
    private boolean drawBackground = true;
    private boolean focused;
    private boolean enabled = true;
    private boolean visible = true;
    private int lineScroll;
    private int cursor;
    private int selectionEnd;
    private int enabledColour = 0xE0E0E0;
    private int disabledColour = 0x707070;

    public GuiTextField(FontRenderer font, int x, int y, int width, int height) {
        this.font = font;
        this.xPosition = x;
        this.yPosition = y;
        this.width = width;
        this.height = height;
    }

    public void updateCursorCounter() {
        this.cursorCounter++;
    }

    public void setText(String value) {
        this.text = value.length() > this.maxLength ? value.substring(0, this.maxLength) : value;
        setCursorPositionEnd();
    }

    public String getText() {
        return this.text;
    }

    public String getSelectedText() {
        int a = Math.min(this.cursor, this.selectionEnd);
        int b = Math.max(this.cursor, this.selectionEnd);
        return this.text.substring(a, b);
    }

    public void writeText(String typed) {
        StringBuilder clean = new StringBuilder();
        for (char c : typed.toCharArray()) {
            if (StringUtil.isAllowedChatCharacter(c)) {
                clean.append(c);
            }
        }
        int a = Math.min(this.cursor, this.selectionEnd);
        int b = Math.max(this.cursor, this.selectionEnd);
        int room = this.maxLength - this.text.length() + (b - a);
        String insert = clean.length() > room ? clean.substring(0, Math.max(0, room)) : clean.toString();
        this.text = this.text.substring(0, a) + insert + this.text.substring(b);
        moveCursorBy(a - this.selectionEnd + insert.length());
    }

    public void deleteWords(int count) {
        if (this.text.isEmpty()) {
            return;
        }
        if (this.selectionEnd != this.cursor) {
            writeText("");
        } else {
            deleteFromCursor(func_146197_a(count, this.cursor, true) - this.cursor);
        }
    }

    public void deleteFromCursor(int count) {
        if (this.text.isEmpty()) {
            return;
        }
        if (this.selectionEnd != this.cursor) {
            writeText("");
            return;
        }
        boolean back = count < 0;
        int a = back ? this.cursor + count : this.cursor;
        int b = back ? this.cursor : this.cursor + count;
        a = Math.max(0, a);
        b = Math.min(this.text.length(), b);
        this.text = this.text.substring(0, a) + this.text.substring(b);
        if (back) {
            moveCursorBy(count);
        }
    }

    public int getNthWordFromCursor(int n) {
        return func_146197_a(n, this.cursor, true);
    }

    /** The position {@code n} words away from {@code pos}; skips spaces when asked. */
    public int func_146197_a(int n, int pos, boolean skipSpaces) {
        int i = pos;
        boolean back = n < 0;
        for (int k = 0; k < Math.abs(n); k++) {
            if (!back) {
                int len = this.text.length();
                i = this.text.indexOf(' ', i);
                if (i == -1) {
                    i = len;
                } else {
                    while (skipSpaces && i < len && this.text.charAt(i) == ' ') {
                        i++;
                    }
                }
            } else {
                while (skipSpaces && i > 0 && this.text.charAt(i - 1) == ' ') {
                    i--;
                }
                while (i > 0 && this.text.charAt(i - 1) != ' ') {
                    i--;
                }
            }
        }
        return i;
    }

    public void moveCursorBy(int by) {
        setCursorPosition(this.selectionEnd + by);
    }

    public void setCursorPosition(int pos) {
        this.cursor = Math.max(0, Math.min(this.text.length(), pos));
        setSelectionPos(this.cursor);
    }

    public void setCursorPositionZero() {
        setCursorPosition(0);
    }

    public void setCursorPositionEnd() {
        setCursorPosition(this.text.length());
    }

    public boolean textboxKeyTyped(char c, int key) {
        if (!this.focused) {
            return false;
        }
        boolean ctrl = Screen.hasControlDown();
        boolean shift = Screen.hasShiftDown();
        if (ctrl && key == Keyboard.KEY_A) {
            setCursorPositionEnd();
            setSelectionPos(0);
            return true;
        }
        if (ctrl && key == Keyboard.KEY_C) {
            GuiScreen.setClipboardString(getSelectedText());
            return true;
        }
        if (ctrl && key == Keyboard.KEY_V) {
            if (this.enabled) {
                writeText(GuiScreen.getClipboardString());
            }
            return true;
        }
        if (ctrl && key == Keyboard.KEY_X) {
            GuiScreen.setClipboardString(getSelectedText());
            if (this.enabled) {
                writeText("");
            }
            return true;
        }
        switch (key) {
            case Keyboard.KEY_BACK:
                if (this.enabled) {
                    if (ctrl) {
                        deleteWords(-1);
                    } else {
                        deleteFromCursor(-1);
                    }
                }
                return true;
            case Keyboard.KEY_DELETE:
                if (this.enabled) {
                    if (ctrl) {
                        deleteWords(1);
                    } else {
                        deleteFromCursor(1);
                    }
                }
                return true;
            case Keyboard.KEY_HOME:
                if (shift) {
                    setSelectionPos(0);
                } else {
                    setCursorPositionZero();
                }
                return true;
            case Keyboard.KEY_END:
                if (shift) {
                    setSelectionPos(this.text.length());
                } else {
                    setCursorPositionEnd();
                }
                return true;
            case Keyboard.KEY_LEFT:
            case Keyboard.KEY_RIGHT: {
                int dir = key == Keyboard.KEY_LEFT ? -1 : 1;
                if (shift) {
                    setSelectionPos(ctrl ? func_146197_a(dir, this.selectionEnd, true)
                            : this.selectionEnd + dir);
                } else if (ctrl) {
                    setCursorPosition(getNthWordFromCursor(dir));
                } else {
                    moveCursorBy(dir);
                }
                return true;
            }
            default:
                if (c != 0 && StringUtil.isAllowedChatCharacter(c)) {
                    if (this.enabled) {
                        writeText(Character.toString(c));
                    }
                    return true;
                }
                return false;
        }
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        boolean inside = mouseX >= this.xPosition && mouseX < this.xPosition + this.width
                && mouseY >= this.yPosition && mouseY < this.yPosition + this.height;
        setFocused(inside);
        if (this.focused && inside && button == 0) {
            int local = mouseX - this.xPosition;
            if (this.drawBackground) {
                local -= 4;
            }
            String shown = this.font.trimStringToWidth(this.text.substring(this.lineScroll), getWidth());
            setCursorPosition(this.font.trimStringToWidth(shown, local).length() + this.lineScroll);
        }
    }

    public void drawTextBox() {
        if (!this.visible) {
            return;
        }
        if (this.drawBackground) {
            drawRect(this.xPosition - 1, this.yPosition - 1, this.xPosition + this.width + 1,
                    this.yPosition + this.height + 1, 0xFFA0A0A0);
            drawRect(this.xPosition, this.yPosition, this.xPosition + this.width,
                    this.yPosition + this.height, 0xFF000000);
        }
        int colour = this.enabled ? this.enabledColour : this.disabledColour;
        int caret = this.cursor - this.lineScroll;
        int sel = this.selectionEnd - this.lineScroll;
        String shown = this.font.trimStringToWidth(this.text.substring(this.lineScroll), getWidth());
        boolean caretInView = caret >= 0 && caret <= shown.length();
        boolean blink = this.focused && this.cursorCounter / 6 % 2 == 0 && caretInView;
        int x = this.drawBackground ? this.xPosition + 4 : this.xPosition;
        int y = this.drawBackground ? this.yPosition + (this.height - 8) / 2 : this.yPosition;
        int drawn = x;
        sel = Math.min(sel, shown.length());
        if (!shown.isEmpty()) {
            String before = caretInView ? shown.substring(0, caret) : shown;
            drawn = this.font.drawStringWithShadow(before, x, y, colour);
        }
        boolean atEnd = this.cursor < this.text.length() || this.text.length() >= this.maxLength;
        int caretX = drawn;
        if (!caretInView) {
            caretX = caret > 0 ? x + this.width : x;
        } else if (atEnd) {
            caretX = drawn - 1;
            drawn--;
        }
        if (!shown.isEmpty() && caretInView && caret < shown.length()) {
            this.font.drawStringWithShadow(shown.substring(caret), drawn, y, colour);
        }
        if (blink) {
            if (atEnd) {
                drawRect(caretX, y - 1, caretX + 1, y + 1 + this.font.FONT_HEIGHT, 0xFFD0D0D0);
            } else {
                this.font.drawStringWithShadow("_", caretX, y, colour);
            }
        }
        if (sel != caret) {
            int selX = x + this.font.getStringWidth(shown.substring(0, Math.max(0, sel)));
            int a = Math.min(caretX, selX);
            int b = Math.max(caretX, selX);
            drawRect(a, y - 1, b, y + 1 + this.font.FONT_HEIGHT, 0x600000FF);
        }
    }

    public void setMaxStringLength(int length) {
        this.maxLength = length;
        if (this.text.length() > length) {
            this.text = this.text.substring(0, length);
        }
    }

    public int getMaxStringLength() {
        return this.maxLength;
    }

    public int getCursorPosition() {
        return this.cursor;
    }

    public boolean getEnableBackgroundDrawing() {
        return this.drawBackground;
    }

    public void setEnableBackgroundDrawing(boolean value) {
        this.drawBackground = value;
    }

    public void setTextColor(int colour) {
        this.enabledColour = colour;
    }

    public void setDisabledTextColour(int colour) {
        this.disabledColour = colour;
    }

    public void setFocused(boolean value) {
        if (value && !this.focused) {
            this.cursorCounter = 0;
        }
        this.focused = value;
    }

    public boolean isFocused() {
        return this.focused;
    }

    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    public int getSelectionEnd() {
        return this.selectionEnd;
    }

    public int getWidth() {
        return this.drawBackground ? this.width - 8 : this.width;
    }

    public void setSelectionPos(int pos) {
        int len = this.text.length();
        pos = Math.max(0, Math.min(len, pos));
        this.selectionEnd = pos;
        if (this.lineScroll > len) {
            this.lineScroll = len;
        }
        int w = getWidth();
        String shown = this.font.trimStringToWidth(this.text.substring(this.lineScroll), w);
        int end = shown.length() + this.lineScroll;
        if (pos == this.lineScroll) {
            this.lineScroll -= this.font.trimStringToWidth(this.text, w, true).length();
        }
        if (pos > end) {
            this.lineScroll += pos - end;
        } else if (pos <= this.lineScroll) {
            this.lineScroll -= this.lineScroll - pos;
        }
        this.lineScroll = Math.max(0, Math.min(len, this.lineScroll));
    }

    public boolean getVisible() {
        return this.visible;
    }

    public void setVisible(boolean value) {
        this.visible = value;
    }
}
