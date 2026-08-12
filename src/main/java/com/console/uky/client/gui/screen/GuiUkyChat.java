package com.console.uky.client.gui.screen;

import com.console.uky.client.gui.AchievementLinks;
import com.console.uky.client.gui.CommandLine;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Theme;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.event.HoverEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.Achievement;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
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

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawInputBar();
        CommandLine line = commandLine();
        if (line != null) {
            line.drawField();
            line.drawSuggestions(mouseX, mouseY);
        }

        IChatComponent hovered =
                this.mc.ingameGUI.getChatGUI().func_146236_a(Mouse.getX(), Mouse.getY());
        if (hovered != null && hovered.getChatStyle().getChatHoverEvent() != null) {
            drawHover(hovered.getChatStyle().getChatHoverEvent(), mouseX, mouseY);
            GL11.glDisable(GL11.GL_LIGHTING);
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
    protected void keyTyped(char typedChar, int keyCode) {
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
    public void func_146406_a(String[] suggestions) {
        CommandLine line = commandLine();
        if (line != null) {
            line.onServerSuggestions(suggestions);
        }
    }

    @Override
    public void handleMouseInput() {
        CommandLine line = commandLine();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && line != null && line.isOpen()) {
            ScaledResolution res = new ScaledResolution(this.mc,
                    this.mc.displayWidth, this.mc.displayHeight);
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
            int width = this.fontRendererObj.getStringWidth(left);
            // Above the bar rather than inside it: a long line has already scrolled to
            // fill the box, so anything drawn in there lands on top of the text it is
            // counting. The strip above is empty — the chat's own lines stop higher.
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            this.fontRendererObj.drawString(left, (int) (x2 - 2 - width), (int) (y1 - 10),
                    Draw.withAlpha(text.length() >= max ? Theme.danger : Theme.textDim, 0.75F));
        }
    }

    /**
     * The three hover kinds chat carries, reproduced from {@link GuiChat}.
     *
     * Item hovers are wrapped: the NBT in one comes off the network as text, and a
     * malformed one should show nothing rather than take the chat screen down.
     */
    private void drawHover(HoverEvent hover, int mouseX, int mouseY) {
        if (hover.getAction() == HoverEvent.Action.SHOW_ITEM) {
            ItemStack stack = null;
            try {
                NBTBase parsed = JsonToNBT.func_150315_a(hover.getValue().getUnformattedText());
                if (parsed instanceof NBTTagCompound) {
                    stack = ItemStack.loadItemStackFromNBT((NBTTagCompound) parsed);
                }
            } catch (Throwable t) {
                stack = null;
            }
            if (stack != null) {
                this.renderToolTip(stack, mouseX, mouseY);
            } else {
                this.drawCreativeTabHoveringText(EnumChatFormatting.RED + "Invalid Item!",
                        mouseX, mouseY);
            }
            return;
        }

        if (hover.getAction() == HoverEvent.Action.SHOW_TEXT) {
            this.func_146283_a(Splitter.on("\n").splitToList(
                    hover.getValue().getFormattedText()), mouseX, mouseY);
            return;
        }

        if (hover.getAction() != HoverEvent.Action.SHOW_ACHIEVEMENT) {
            return;
        }
        StatBase stat = StatList.func_151177_a(hover.getValue().getUnformattedText());
        if (stat == null) {
            this.drawCreativeTabHoveringText(
                    EnumChatFormatting.RED + "Invalid statistic/achievement!", mouseX, mouseY);
            return;
        }
        ChatComponentTranslation kind = new ChatComponentTranslation(
                "stats.tooltip.type." + (stat.isAchievement() ? "achievement" : "statistic"),
                new Object[0]);
        kind.getChatStyle().setItalic(Boolean.TRUE);
        List<String> lines = Lists.newArrayList(
                stat.func_150951_e().getFormattedText(), kind.getFormattedText());
        if (stat instanceof Achievement) {
            String description = ((Achievement) stat).getDescription();
            if (description != null) {
                lines.addAll(new ArrayList<String>(
                        this.fontRendererObj.listFormattedStringToWidth(description, 150)));
            }
        }
        // One more line than vanilla shows, because this tooltip now has somewhere to
        // go and nothing else says so.
        lines.add(EnumChatFormatting.DARK_GRAY
                + net.minecraft.client.resources.I18n.format("uky.achievement.open", new Object[0]));
        this.func_146283_a(lines, mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        // The completion box is on top of everything else here, so it is asked first.
        CommandLine line = commandLine();
        if (line != null && line.mouseClicked(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (mouseButton == 0 && !isShiftKeyDown()) {
            IChatComponent clicked =
                    this.mc.ingameGUI.getChatGUI().func_146236_a(Mouse.getX(), Mouse.getY());
            if (AchievementLinks.handleClick(clicked)) {
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
}
