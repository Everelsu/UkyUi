package com.console.uky.client.gui.screen;

import com.console.uky.client.gui.AchievementLinks;
import com.console.uky.client.gui.ChatOverlay;
import com.console.uky.config.UiConfig;
import com.console.uky.client.gui.CommandLine;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Theme;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.text.ITextComponent;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * The chat screen — the input box and everything that happens while it is open.
 *
 * This is the other half of the chat redesign; {@code ChatOverlay} draws the messages
 * themselves, which are HUD and not a screen at all. Only the box at the bottom is
 * ours: history, tab completion, sending, and every key are still {@link GuiChat}'s,
 * because all of that is behaviour rather than appearance and none of it was wrong.
 *
 * <p>{@code drawScreen} is reimplemented rather than extended, for one reason: the
 * first thing vanilla's does is fill a grey rectangle across the bottom of the screen,
 * and there is no way to have that not happen while still calling it. What follows it
 * — the input field and the hover tooltip — is reproduced here, tooltip kinds and all.
 * The rest of the screen is untouched.
 *
 * <p>It also takes achievement clicks directly, before they become a command. That is
 * belt and braces: the link works through {@code AchievementCommand} whatever chat
 * screen is installed, and this saves the round trip when the installed one is ours.
 */
public class GuiUkyChat extends GuiChat {

    /** Highlighting and completions; built once the input field exists. */
    private CommandLine commandLine;

    public GuiUkyChat() {
        super();
    }

    public GuiUkyChat(String defaultText) {
        super(defaultText);
    }

    @Override
    public void initGui() {
        super.initGui();
        this.commandLine = new CommandLine(this.mc, this.inputField);
        // Pressing "/" in the world opens this screen with the slash already typed, and
        // opens it with the command list too. Without this the box would wait for the
        // second character, which is the one case where the player has already said
        // what they want before the screen existed.
        this.commandLine.afterEdit();
    }

    /**
     * The command line, built on demand.
     *
     * Not simply the field: any mod may cancel {@code InitGuiEvent.Pre}, and
     * {@code setWorldAndResolution} then skips {@code initGui} entirely while still
     * showing the screen — so everything built there has to be allowed to be missing.
     * Null only if the input field is missing too, at which point there is no chat
     * screen to speak of and every path below simply does nothing.
     */
    private CommandLine commandLine() {
        if (this.commandLine == null && this.inputField != null) {
            this.commandLine = new CommandLine(this.mc, this.inputField);
        }
        return this.commandLine;
    }

    /**
     * Where the chat log was drawn from, in scaled units.
     *
     * The same number Forge hands the chat overlay — {@code height - 48} — and it is
     * recomputed here rather than remembered because this screen and that overlay are
     * two different objects that never meet.
     */
    /**
     * The chat line under the pointer, from whoever actually drew the chat.
     *
     * One method for the tooltip and the click both, because the two disagreeing is
     * exactly the bug this replaced: the tooltip came from one hit test and the click
     * from another, so a line could show its tooltip and then not answer the click.
     */
    private ITextComponent hoveredComponent() {
        if (UiConfig.redesignChat) {
            return ChatOverlay.componentAt(this.mc, chatOriginY());
        }
        return this.mc.ingameGUI.getChatGUI().getChatComponent(Mouse.getX(), Mouse.getY());
    }

    private int chatOriginY() {
        return new ScaledResolution(this.mc).getScaledHeight() - 48;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawInputBar();
        CommandLine line = commandLine();
        if (line != null) {
            line.drawField();
            line.drawSuggestions(mouseX, mouseY);
        }

        // Whoever drew the chat is who knows where its lines are. With the redesign on
        // that is ChatOverlay, and asking vanilla instead put the tooltip a line or two
        // off the thing it belonged to.
        ITextComponent hovered = hoveredComponent();
        if (hovered != null && hovered.getStyle().getHoverEvent() != null) {
            // 1.12 draws every kind of hover itself — item, entity and text — so there
            // is nothing here to reproduce. 1.7.10 had no such method, which is why
            // this screen used to carry a copy of all three.
            this.handleComponentHover(hovered, mouseX, mouseY);
            GlStateManager.disableLighting();
        }
        // GuiScreen.drawScreen draws buttons and labels; this screen has neither, and
        // GuiChat's own is what we are replacing — so there is nothing left to call.
    }

    /**
     * Keys go to the completion box first, and to the chat screen for anything it did
     * not want.
     *
     * Tab in particular never reaches {@link GuiChat}: its own completion writes the
     * candidates into the chat log as a message and cycles them one press at a time,
     * which is the behaviour being replaced rather than one to fall back on.
     */
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        CommandLine line = commandLine();
        if (line != null && line.keyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
        // After, not before: the field is what the box narrows against, and this is
        // the first moment it holds what was just typed.
        if (line != null) {
            line.afterEdit();
        }
    }

    /**
     * The server's completions, taken before {@link GuiChat} can put them in the chat.
     *
     * Deliberately does not call through. The vanilla method inserts the longest
     * common prefix and then prints every candidate as a chat message with a line id
     * of 1 so that the next one replaces it — a workaround for not having anywhere
     * else to show them, and this now has somewhere.
     */
    @Override
    public void setCompletions(String... suggestions) {
        CommandLine line = commandLine();
        if (line != null) {
            line.onServerSuggestions(suggestions);
        }
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        CommandLine line = commandLine();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && line != null && line.isOpen()) {
            ScaledResolution res = new ScaledResolution(this.mc);
            int mouseX = Mouse.getEventX() * res.getScaledWidth() / this.mc.displayWidth;
            int mouseY = res.getScaledHeight()
                    - Mouse.getEventY() * res.getScaledHeight() / this.mc.displayHeight - 1;
            // Only when the pointer is actually over the box; everywhere else the
            // wheel still scrolls the chat log, which is what it is for.
            if (line.mouseWheel(mouseX, mouseY, wheel)) {
                return;
            }
        }
        super.handleMouseInput();
    }

    /**
     * The box the text is typed into: our panel, in the space vanilla filled grey.
     *
     * Exactly the same rectangle, because the field inside it is positioned by
     * {@code GuiChat.initGui} and moving the box without moving the field would put
     * the text over the edge of it.
     */
    private void drawInputBar() {
        float x1 = 2.0F;
        float x2 = this.width - 2.0F;
        float y1 = this.height - 14.0F;
        float y2 = this.height - 2.0F;

        String text = this.inputField == null ? "" : this.inputField.getText();
        boolean command = text.startsWith("/");

        Draw.gradientV(x1, y1, x2, y2,
                Draw.withAlpha(Theme.background, 0.88F),
                Draw.withAlpha(Theme.background, 0.72F));
        // The rule along the top edge is what separates the box from the messages
        // sitting directly above it; without it the two run together at low opacity.
        Draw.gradientH(x1, y1 - 1.0F, x2 * 0.7F, y1,
                Draw.withAlpha(Theme.accent, 0.55F), Draw.withAlpha(Theme.accent, 0.0F));

        // The rail says which of the two things is being typed. A command and a
        // sentence go to entirely different places, and knowing which one is in the
        // box before pressing enter is worth two pixels of colour.
        int rail = command ? Theme.accent : Draw.mix(Theme.text, Theme.accent, 0.25F);
        Draw.rect(x1, y1, x1 + 2.0F, y2, Draw.withAlpha(rail, command ? 1.0F : 0.55F));
        Draw.gradientH(x1 + 2.0F, y1, x1 + 60.0F, y2,
                Draw.withAlpha(rail, command ? 0.16F : 0.07F), Draw.withAlpha(rail, 0.0F));
        if (command) {
            Draw.gradientH(x1, y2 - 1.0F, x2 * 0.5F, y2,
                    Draw.withAlpha(Theme.accent, 0.5F), Draw.withAlpha(Theme.accent, 0.0F));
        }

        // How much room is left, once there is little enough of it to matter. The
        // limit is 100 characters and hitting it silently — the field simply stops
        // accepting keys — is the kind of thing that reads as the game having frozen.
        int max = this.inputField == null ? 100 : this.inputField.getMaxStringLength();
        if (text.length() > max - 30) {
            String left = String.valueOf(max - text.length());
            int width = this.fontRenderer.getStringWidth(left);
            // Above the bar rather than inside it: a long line has already scrolled to
            // fill the box, so anything drawn in there lands on top of the text it is
            // counting. The strip above is empty — the chat's own lines stop higher.
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            this.fontRenderer.drawString(left, (int) (x2 - 2 - width), (int) (y1 - 10),
                    Draw.withAlpha(text.length() >= max ? Theme.danger : Theme.textDim, 0.75F));
        }
    }


    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        // The completion box is on top of everything else here, so it is asked first.
        CommandLine line = commandLine();
        if (line != null && line.mouseClicked(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (mouseButton == 0 && !isShiftKeyDown()) {
            ITextComponent clicked = hoveredComponent();
            if (AchievementLinks.handleClick(clicked)) {
                return;
            }
            // Vanilla's own click handling asks vanilla where the line is, which is the
            // wrong answer while we are drawing the chat — so with the redesign on the
            // click is resolved here and handled by the same method vanilla would have
            // used, rather than left to a second, disagreeing hit test.
            if (UiConfig.redesignChat && clicked != null
                    && handleComponentClick(clicked)) {
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
}
