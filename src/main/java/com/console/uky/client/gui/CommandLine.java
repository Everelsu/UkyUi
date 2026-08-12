package com.console.uky.client.gui;

import com.console.uky.UkyUI;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.network.play.client.C14PacketTabComplete;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.ClientCommandHandler;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * The command line: what a modern client's chat box does, on 1.7.10's.
 *
 * Two things, both of which arrived in the game years after this version and neither
 * of which needs anything the server has to send:
 *
 * <ul>
 * <li><b>The text is coloured as you type it.</b> The slash and the command name in
 *     the accent, then each argument in its own colour, cycling — with numbers,
 *     quoted strings and {@code @} selectors picked out on sight. Which is not
 *     decoration: the single most common mistake in a long command is a missing or an
 *     extra space, and that is invisible in one colour and obvious in six.
 * <li><b>Completions are a list you pick from</b> rather than a line of text shoved
 *     into the chat. Vanilla's tab completion writes every candidate into the chat
 *     log as a comma-separated message and then cycles them one press at a time; this
 *     puts them in a box above the cursor, arrow keys move through it, Tab takes the
 *     one that is highlighted, and the rest of what you have already typed appears
 *     greyed out ahead of the cursor while you decide.
 * </ul>
 *
 * <p>Where the candidates come from is unchanged: Forge's client commands answer
 * immediately, the server is asked over the same packet vanilla uses, and its reply
 * arrives at {@link #onServerSuggestions}. Nothing here talks to a server in a way
 * 1.7.10 does not already.
 *
 * <p>Typing after the list arrives narrows it here, without asking anything again —
 * a client this old has no local copy of the server's commands, so a request per
 * keystroke would be a request per keystroke on somebody else's machine.
 */
public final class CommandLine {

    /** Rows of the suggestion box shown at once; the rest scrolls. */
    private static final int VISIBLE_ROWS = 8;
    private static final int ROW_HEIGHT = 12;

    private final Minecraft mc;
    private final FontRenderer font;
    private final GuiTextField field;

    /** Everything the last request produced, before the word narrowed it. */
    private final List<String> candidates = new ArrayList<String>();
    /** What is actually in the box, filtered by what has been typed since. */
    private final List<String> shown = new ArrayList<String>();
    private int selected;
    private int scroll;
    /** True between asking the server and hearing back from it. */
    private boolean awaitingServer;

    /** Bounds of the box, kept from the last draw so a click can be tested against it. */
    private float boxX1;
    private float boxY1;
    private float boxX2;
    private float boxY2;

    public CommandLine(Minecraft mc, GuiTextField field) {
        this.mc = mc;
        this.font = mc.fontRenderer;
        this.field = field;
    }

    public boolean isOpen() {
        return !this.shown.isEmpty();
    }

    public void close() {
        this.candidates.clear();
        this.shown.clear();
        this.selected = 0;
        this.scroll = 0;
        this.awaitingServer = false;
    }

    // ------------------------------------------------------------------- input --

    /**
     * @return whether this took the key — the chat screen must not also act on it
     */
    public boolean keyTyped(char typed, int keyCode) {
        switch (keyCode) {
            case 15: // tab
                if (isOpen()) {
                    apply(this.shown.get(this.selected));
                } else {
                    request();
                }
                return true;
            case 1: // escape
                // Closes the list before it closes the chat, which is the behaviour
                // of every box like this and the reason it is safe to have one.
                if (isOpen()) {
                    close();
                    return true;
                }
                return false;
            case 200: // up
                if (isOpen()) {
                    move(-1);
                    return true;
                }
                return false;
            case 208: // down
                if (isOpen()) {
                    move(1);
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    /**
     * Re-narrows the list after the field changed.
     *
     * Called for every edit rather than only for typed characters: a backspace widens
     * what matches, and a list that only ever shrank would go empty and stay empty.
     */
    public void afterEdit() {
        if (this.candidates.isEmpty()) {
            return;
        }
        narrow();
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || !isOpen() || !inBox(mouseX, mouseY)) {
            return false;
        }
        int row = (int) ((mouseY - this.boxY1) / ROW_HEIGHT) + this.scroll;
        if (row >= 0 && row < this.shown.size()) {
            apply(this.shown.get(row));
        }
        return true;
    }

    /** @return whether the wheel belonged to the box rather than to the chat log */
    public boolean mouseWheel(int mouseX, int mouseY, int notches) {
        if (!isOpen() || !inBox(mouseX, mouseY)) {
            return false;
        }
        move(notches > 0 ? -1 : 1);
        return true;
    }

    private boolean inBox(int mouseX, int mouseY) {
        return mouseX >= this.boxX1 && mouseX <= this.boxX2
                && mouseY >= this.boxY1 && mouseY <= this.boxY2;
    }

    private void move(int delta) {
        int count = this.shown.size();
        if (count == 0) {
            return;
        }
        // Wraps, because a list of three that stops at the third is a list you have to
        // look at to use.
        this.selected = (this.selected + delta % count + count) % count;
        if (this.selected < this.scroll) {
            this.scroll = this.selected;
        } else if (this.selected >= this.scroll + VISIBLE_ROWS) {
            this.scroll = this.selected - VISIBLE_ROWS + 1;
        }
        this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, count - VISIBLE_ROWS)));
    }

    // -------------------------------------------------------------- completion --

    /**
     * Asks for completions of the word the cursor is in.
     *
     * Both sources at once, and they answer at different speeds: Forge's client
     * commands are resolved in this call, so the box can be on screen before the key
     * is released, while the server's reply is a packet away and merges into the same
     * list when it lands.
     */
    private void request() {
        if (this.mc.thePlayer == null) {
            return;
        }
        int cursor = this.field.getCursorPosition();
        String beforeCursor = this.field.getText().substring(0, cursor);
        // Nothing typed yet, and two reasons not to ask: there is nothing to complete,
        // and Forge's client-command completion reads the first character without
        // checking there is one.
        if (beforeCursor.isEmpty()) {
            return;
        }

        this.candidates.clear();
        if (beforeCursor.charAt(0) == '/') {
            ClientCommandHandler.instance.autoComplete(beforeCursor, currentWord());
            String[] local = ClientCommandHandler.instance.latestAutoComplete;
            if (local != null) {
                addAll(local);
            }
        }
        // Sent for plain messages too, not only commands: on a server this is what
        // completes a player's name, and a box of names is worth as much as a box of
        // commands. Vanilla's answer to the same reply is to write the names into the
        // chat log.
        this.mc.thePlayer.sendQueue.addToSendQueue(new C14PacketTabComplete(beforeCursor));
        this.awaitingServer = true;
        narrow();
    }

    /** The server's reply, handed over by the chat screen. */
    public void onServerSuggestions(String[] values) {
        // Only ever a reply to something asked for here. A late one — the box was
        // closed while it was in flight — must not reopen it under the cursor.
        if (values == null || !this.awaitingServer) {
            return;
        }
        this.awaitingServer = false;
        addAll(values);
        narrow();
        // One candidate and nothing to choose between: complete it and get out of the
        // way, which is what pressing Tab meant.
        if (this.shown.size() == 1) {
            apply(this.shown.get(0));
        }
    }

    private void addAll(String[] values) {
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String clean = EnumChatFormatting.getTextWithoutFormattingCodes(value);
            if (clean == null || clean.isEmpty() || this.candidates.contains(clean)) {
                continue;
            }
            this.candidates.add(clean);
        }
    }

    private void narrow() {
        String word = currentWord().toLowerCase();
        this.shown.clear();
        for (int i = 0; i < this.candidates.size(); i++) {
            String candidate = this.candidates.get(i);
            if (candidate.toLowerCase().startsWith(word)) {
                this.shown.add(candidate);
            }
        }
        this.selected = 0;
        this.scroll = 0;
    }

    /** The word the cursor is inside, which is what a completion replaces. */
    private String currentWord() {
        String text = this.field.getText();
        int cursor = Math.min(this.field.getCursorPosition(), text.length());
        return text.substring(wordStart(), cursor);
    }

    private int wordStart() {
        return this.field.func_146197_a(-1, this.field.getCursorPosition(), false);
    }

    private void apply(String suggestion) {
        int cursor = this.field.getCursorPosition();
        this.field.deleteFromCursor(wordStart() - cursor);
        this.field.writeText(suggestion);
        close();
    }

    // ----------------------------------------------------------------- drawing --

    /**
     * The text itself: coloured, with the selection, the cursor and the ghost of
     * whatever completion is currently highlighted.
     *
     * This replaces {@code GuiTextField.drawTextBox} rather than adding to it — one
     * colour for the whole line is exactly the thing being fixed. The field is still
     * what holds the text, the cursor and the scroll offset; only the drawing moves.
     */
    public void drawField() {
        String text = this.field.getText();
        int offset = scrollOffset();
        if (offset > text.length()) {
            offset = text.length();
        }
        String visible = this.font.trimStringToWidth(text.substring(offset),
                this.field.getWidth());
        int visibleEnd = offset + visible.length();
        int x = this.field.xPosition;
        int y = this.field.yPosition;

        drawSelection(text, offset, visibleEnd, x, y);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        List<Span> spans = spans(text);
        for (int i = 0; i < spans.size(); i++) {
            Span span = spans.get(i);
            int from = Math.max(span.start, offset);
            int to = Math.min(span.end, visibleEnd);
            if (to <= from) {
                continue;
            }
            int px = x + this.font.getStringWidth(text.substring(offset, from));
            this.font.drawStringWithShadow(text.substring(from, to), px, y, span.colour);
        }

        int cursorX = x + this.font.getStringWidth(
                text.substring(offset, Math.max(offset, Math.min(this.field.getCursorPosition(),
                        visibleEnd))));
        drawGhost(cursorX, y);
        drawCursor(cursorX, y);
    }

    /**
     * The rest of the highlighted completion, ahead of the cursor and greyed.
     *
     * The one piece of this that is purely a courtesy: it says what Tab is about to
     * do before it is pressed, so the box does not have to be read at all in the
     * common case where the first candidate is the right one.
     */
    private void drawGhost(int cursorX, int y) {
        if (!isOpen()) {
            return;
        }
        String suggestion = this.shown.get(this.selected);
        String word = currentWord();
        if (word.length() >= suggestion.length()
                || !suggestion.toLowerCase().startsWith(word.toLowerCase())) {
            return;
        }
        this.font.drawStringWithShadow(suggestion.substring(word.length()), cursorX, y,
                Draw.withAlpha(Theme.textDim, 0.55F));
    }

    private void drawCursor(int cursorX, int y) {
        if (!this.field.isFocused()) {
            return;
        }
        // Blinked off the wall clock rather than off the field's own tick counter,
        // which is private. Same rhythm, and it does not stutter when the game does.
        if (System.currentTimeMillis() % 1060L > 530L) {
            return;
        }
        Draw.rect(cursorX, y - 1.0F, cursorX + 1.0F, y + this.font.FONT_HEIGHT,
                Draw.withAlpha(Theme.accent, 0.95F));
    }

    private void drawSelection(String text, int offset, int visibleEnd, int x, int y) {
        int cursor = this.field.getCursorPosition();
        int other = this.field.getSelectionEnd();
        if (cursor == other) {
            return;
        }
        int from = Math.max(Math.min(cursor, other), offset);
        int to = Math.min(Math.max(cursor, other), visibleEnd);
        if (to <= from) {
            return;
        }
        float x1 = x + this.font.getStringWidth(text.substring(offset, from));
        float x2 = x + this.font.getStringWidth(text.substring(offset, to));
        Draw.rect(x1, y - 1.0F, x2, y + this.font.FONT_HEIGHT,
                Draw.withAlpha(Theme.accent, 0.30F));
    }

    /**
     * The suggestion box, above the word it is completing.
     *
     * Above rather than below because the chat box is at the bottom of the screen and
     * there is nothing below it; anchored to the word rather than to the field
     * because that is what it is about, and a box floating over the far end of a long
     * command reads as belonging to nothing.
     */
    public void drawSuggestions(int mouseX, int mouseY) {
        this.boxX1 = 0.0F;
        this.boxY1 = 0.0F;
        this.boxX2 = 0.0F;
        this.boxY2 = 0.0F;
        if (!isOpen()) {
            return;
        }
        int rows = Math.min(VISIBLE_ROWS, this.shown.size());
        int widest = 0;
        for (int i = 0; i < this.shown.size(); i++) {
            widest = Math.max(widest, this.font.getStringWidth(this.shown.get(i)));
        }

        String text = this.field.getText();
        int offset = Math.min(scrollOffset(), text.length());
        int start = Math.max(wordStart(), offset);
        float anchor = this.field.xPosition
                + this.font.getStringWidth(text.substring(offset, Math.min(start, text.length())));

        float width = widest + 14.0F;
        float x1 = Math.min(anchor - 3.0F, this.mc.currentScreen.width - width - 4.0F);
        x1 = Math.max(2.0F, x1);
        float x2 = x1 + width;
        float y2 = this.field.yPosition - 5.0F;
        float y1 = y2 - rows * ROW_HEIGHT - 2.0F;

        this.boxX1 = x1;
        this.boxY1 = y1 + 1.0F;
        this.boxX2 = x2;
        this.boxY2 = y2;

        Draw.rect(x1, y1, x2, y2, Draw.withAlpha(Theme.background, 0.94F));
        Draw.border(x1, y1, x2, y2, 1.0F, Draw.withAlpha(Theme.text, 0.10F));
        Draw.rect(x1, y1, x1 + 1.0F, y2, Draw.withAlpha(Theme.accent, 0.85F));

        for (int row = 0; row < rows; row++) {
            int index = row + this.scroll;
            if (index >= this.shown.size()) {
                break;
            }
            float rowY = y1 + 1.0F + row * ROW_HEIGHT;
            boolean hovered = mouseX >= x1 && mouseX <= x2
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            boolean active = index == this.selected;

            if (active) {
                Draw.gradientH(x1 + 1.0F, rowY, x2, rowY + ROW_HEIGHT,
                        Draw.withAlpha(Theme.accent, 0.28F), Draw.withAlpha(Theme.accent, 0.06F));
            } else if (hovered) {
                Draw.rect(x1 + 1.0F, rowY, x2, rowY + ROW_HEIGHT,
                        Draw.withAlpha(Theme.text, 0.07F));
            }

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            String entry = this.shown.get(index);
            // The part already typed is dimmed and the part being offered is not, so
            // the box shows what it is about to add rather than what is already there.
            String typed = currentWord();
            int matched = entry.toLowerCase().startsWith(typed.toLowerCase())
                    ? typed.length() : 0;
            float textX = x1 + 6.0F;
            if (matched > 0) {
                this.font.drawString(entry.substring(0, matched), (int) textX, (int) (rowY + 2),
                        Draw.withAlpha(Theme.textDim, 0.8F));
                textX += this.font.getStringWidth(entry.substring(0, matched));
            }
            this.font.drawString(entry.substring(matched), (int) textX, (int) (rowY + 2),
                    Draw.withAlpha(active ? Theme.textHover : Theme.text, active ? 1.0F : 0.8F));
        }

        // How much of the list is off the end of the box, when there is any.
        if (this.shown.size() > VISIBLE_ROWS) {
            float track = y2 - y1 - 2.0F;
            float thumb = Math.max(8.0F, track * VISIBLE_ROWS / (float) this.shown.size());
            float t = this.scroll / (float) (this.shown.size() - VISIBLE_ROWS);
            float top = y1 + 1.0F + (track - thumb) * t;
            Draw.rect(x2 - 2.0F, top, x2 - 1.0F, top + thumb,
                    Draw.withAlpha(Theme.accent, 0.6F));
        }

        if (this.awaitingServer) {
            // Three dots that fill in turn, in the corner, while the server is still
            // being waited on. Without it a slow reply looks like a list that is
            // simply short.
            drawWaiting(x2 - 12.0F, y1 - 4.0F);
        }
    }

    private void drawWaiting(float x, float y) {
        long step = System.currentTimeMillis() / 220L % 3L;
        for (int i = 0; i < 3; i++) {
            Draw.rect(x + i * 4.0F, y, x + i * 4.0F + 2.0F, y + 2.0F,
                    Draw.withAlpha(Theme.accent, i == step ? 0.9F : 0.25F));
        }
    }

    // ------------------------------------------------------------ highlighting --

    /** A run of characters sharing one colour. */
    private static final class Span {

        final int start;
        final int end;
        final int colour;

        Span(int start, int end, int colour) {
            this.start = start;
            this.end = end;
            this.colour = colour;
        }
    }

    /**
     * Splits the line into coloured runs.
     *
     * The scheme is the one 1.13 introduced, in this palette: the command itself in
     * the accent, arguments cycling through three colours so that where one ends and
     * the next begins is never in doubt, and three kinds of argument called out
     * wherever they appear — numbers and coordinates, quoted strings, and {@code @}
     * selectors, which are the three worth checking twice before pressing enter.
     */
    private List<Span> spans(String text) {
        List<Span> spans = new ArrayList<Span>();
        if (!text.startsWith("/")) {
            // An ordinary message. One colour, because it has no structure to show.
            spans.add(new Span(0, text.length(), Draw.withAlpha(Theme.text, 1.0F)));
            return spans;
        }

        int i = 1;
        while (i < text.length() && text.charAt(i) != ' ') {
            i++;
        }
        spans.add(new Span(0, i, Draw.withAlpha(Theme.accent, 1.0F)));

        int argument = 0;
        while (i < text.length()) {
            int gap = i;
            while (i < text.length() && text.charAt(i) == ' ') {
                i++;
            }
            if (i > gap) {
                spans.add(new Span(gap, i, Draw.withAlpha(Theme.textDim, 0.6F)));
            }
            if (i >= text.length()) {
                break;
            }
            int start = i;
            if (text.charAt(i) == '"') {
                i++;
                while (i < text.length() && text.charAt(i) != '"') {
                    i++;
                }
                if (i < text.length()) {
                    i++;
                }
            } else {
                while (i < text.length() && text.charAt(i) != ' ') {
                    i++;
                }
            }
            spans.add(new Span(start, i, argumentColour(text.substring(start, i), argument++)));
        }
        return spans;
    }

    private static int argumentColour(String argument, int index) {
        if (argument.startsWith("@")) {
            return Draw.withAlpha(Theme.danger, 1.0F);
        }
        if (argument.startsWith("\"")) {
            return Draw.withAlpha(Draw.mix(Theme.text, Theme.accentAlt, 0.75F), 1.0F);
        }
        if (isNumeric(argument)) {
            return Draw.withAlpha(Draw.mix(Theme.accent, Theme.textHover, 0.35F), 1.0F);
        }
        switch (index % 3) {
            case 0:
                return Draw.withAlpha(Theme.text, 1.0F);
            case 1:
                return Draw.withAlpha(Draw.mix(Theme.text, Theme.accent, 0.55F), 1.0F);
            default:
                return Draw.withAlpha(Draw.mix(Theme.text, Theme.accentAlt, 0.45F), 1.0F);
        }
    }

    /** Numbers, and the relative and local coordinates that are written like them. */
    private static boolean isNumeric(String argument) {
        int i = 0;
        if (i < argument.length() && (argument.charAt(i) == '~' || argument.charAt(i) == '^')) {
            i++;
            if (i == argument.length()) {
                return true;
            }
        }
        if (i < argument.length() && (argument.charAt(i) == '-' || argument.charAt(i) == '+')) {
            i++;
        }
        boolean digit = false;
        for (; i < argument.length(); i++) {
            char c = argument.charAt(i);
            if (c >= '0' && c <= '9') {
                digit = true;
            } else if (c != '.') {
                return false;
            }
        }
        return digit;
    }

    // -------------------------------------------------------------- reflection --

    /**
     * The field's horizontal scroll — which character the visible text starts at.
     *
     * Private, and needed because this draws the text the field would have drawn: get
     * it wrong on a line longer than the box and every colour, the cursor and the
     * selection are all offset by the same wrong amount.
     *
     * <p>Two names are tried because only one of them exists at a time: a development
     * workspace has MCP's, a built jar has SRG's. Everything else this mod reflects on
     * was never named by MCP and so needs only one.
     */
    private static Field scrollField;
    private static boolean scrollSearched;

    private int scrollOffset() {
        if (!scrollSearched) {
            scrollSearched = true;
            scrollField = find("field_146225_q", "lineScrollOffset");
        }
        if (scrollField == null) {
            return 0;
        }
        try {
            return scrollField.getInt(this.field);
        } catch (Throwable t) {
            scrollField = null;
            return 0;
        }
    }

    private static Field find(String... names) {
        for (String name : names) {
            try {
                Field found = GuiTextField.class.getDeclaredField(name);
                found.setAccessible(true);
                return found;
            } catch (Throwable ignored) {
                // Try the next spelling.
            }
        }
        // Long text will scroll under the highlighting rather than with it. Worth a
        // line in the log and not worth giving up the feature over.
        UkyUI.LOGGER.warn("GuiTextField's scroll offset was not found;"
                + " the chat input will not follow a line wider than its box");
        return null;
    }
}
