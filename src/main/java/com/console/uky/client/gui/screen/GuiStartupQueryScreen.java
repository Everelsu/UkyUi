package com.console.uky.client.gui.screen;

import com.console.uky.client.gui.widget.MenuButton;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import com.console.uky.config.Quality;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.client.GuiNotification;
import cpw.mods.fml.common.StartupQuery;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

/**
 * The question FML asks part-way through a world load, in this pack's style.
 *
 * FML puts these up from {@code StartupQuery} — "there are missing blocks in this
 * save, continue?", "the world backup could not be created" — at a point where the
 * client is not running its normal game loop at all. Everything about how this
 * screen is driven follows from that, and it is why it extends
 * {@link GuiNotification} rather than {@code MenuScreen}: the only code that draws
 * it and reads its input is {@code FMLClientHandler.handleLoadingScreen}, which
 * looks for exactly that type. Replace it with anything else and the screen goes
 * dark and stops responding.
 *
 * <p>{@link com.console.uky.client.world.UkyLoadingScreen} is what calls into that
 * path, and how often it does is what this screen's frame rate is.
 */
public class GuiStartupQueryScreen extends GuiNotification {

    private static final int BUTTON_W = 150;
    private static final int BUTTON_H = 20;
    private static final int LINE_H = 10;

    /** True when the query wants an answer rather than an acknowledgement. */
    private final boolean confirmation;

    private String heading = "";
    private final List<String> body = new ArrayList<String>();
    /** End of the opening paragraph — the part that is the actual question. */
    private int lead;

    private long lastFrameNanos = System.nanoTime();
    private float elapsed;
    /** First body line drawn, moved by the wheel when the list does not fit. */
    private int scroll;
    private int visibleLines;
    private boolean answered;

    public GuiStartupQueryScreen(StartupQuery query) {
        super(query);
        this.confirmation = query.getResult() != null;
    }

    /**
     * Restyles one of FML's own query screens, or returns null to leave it alone.
     *
     * Only its exact screens are taken over — a mod that subclasses them has added
     * something, and throwing that away to restyle a dialog is not a trade worth
     * making. The query itself is read by field type rather than name because
     * {@code GuiNotification} declares it protected, which a class outside its
     * hierarchy cannot reach even though every subclass can.
     */
    public static GuiStartupQueryScreen wrap(net.minecraft.client.gui.GuiScreen screen) {
        Class<?> type = screen.getClass();
        if (type != cpw.mods.fml.client.GuiNotification.class
                && type != cpw.mods.fml.client.GuiConfirmation.class) {
            return null;
        }
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field field : c.getDeclaredFields()) {
                if (field.getType() != StartupQuery.class) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    StartupQuery query = (StartupQuery) field.get(screen);
                    return query == null ? null : new GuiStartupQueryScreen(query);
                } catch (IllegalAccessException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** Size {@link #initGui()} last ran at; -1 until it has run. */
    private int laidOutWidth = -1;
    private int laidOutHeight = -1;

    /**
     * Runs the layout if nothing else did.
     *
     * Forge lets a mod cancel {@code GuiScreenEvent.InitGuiEvent.Pre}, and
     * {@code setWorldAndResolution} then skips {@code initGui} outright. Here that
     * would leave a startup question with no text and no buttons, at a point in the
     * launch where the game loop is parked waiting for an answer that can no longer
     * be given.
     */
    private void ensureLayout() {
        if (this.laidOutWidth != this.width || this.laidOutHeight != this.height) {
            initGui();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        this.laidOutWidth = this.width;
        this.laidOutHeight = this.height;
        this.buttonList.clear();
        splitText();

        int y = this.height - 34;
        if (this.confirmation) {
            int gap = 8;
            int left = this.width / 2 - BUTTON_W - gap / 2;
            this.buttonList.add(new MenuButton(0, left, y, BUTTON_W, BUTTON_H,
                    I18n.format("gui.yes"), MenuButton.Style.PRIMARY)
                    .align(MenuButton.Align.CENTER));
            this.buttonList.add(new MenuButton(1, left + BUTTON_W + gap, y, BUTTON_W, BUTTON_H,
                    I18n.format("gui.no"))
                    .align(MenuButton.Align.CENTER));
        } else {
            this.buttonList.add(new MenuButton(1, (this.width - BUTTON_W) / 2, y, BUTTON_W, BUTTON_H,
                    I18n.format("gui.done"), MenuButton.Style.PRIMARY)
                    .align(MenuButton.Align.CENTER));
        }

        // Room between the heading block and the buttons, in whole lines.
        this.visibleLines = Math.max(1, (this.height - 96) / LINE_H);
    }

    /**
     * Splits the query into a heading and a body.
     *
     * FML hands over one string with newlines in it, written as a paragraph with the
     * headline first. Drawing it as one undifferentiated block — which is what the
     * stock screen does — is why a list of thirty missing item ids reads as a wall
     * of white text with the actual question buried in it.
     */
    private void splitText() {
        this.heading = "";
        this.body.clear();
        String text = this.query.getText();
        if (text == null) {
            return;
        }
        // Wrapped rather than drawn as given: one of these queries is built from an
        // exception's message, and a single line of that is longer than any window.
        int room = Math.max(80, this.width - 60);
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (this.heading.isEmpty()) {
                if (!line.isEmpty()) {
                    this.heading = this.fontRendererObj.trimStringToWidth(line, room);
                }
                continue;
            }
            // Runs of blank lines collapse: they are paragraph breaks in a layout
            // that has none, and each one costs a line of the list.
            if (line.isEmpty()) {
                if (!this.body.isEmpty() && !this.body.get(this.body.size() - 1).isEmpty()) {
                    this.body.add("");
                }
                continue;
            }
            List<?> wrapped = this.fontRendererObj.listFormattedStringToWidth(line, room);
            for (int j = 0; j < wrapped.size(); j++) {
                this.body.add(String.valueOf(wrapped.get(j)));
            }
        }
        while (!this.body.isEmpty() && this.body.get(this.body.size() - 1).isEmpty()) {
            this.body.remove(this.body.size() - 1);
        }

        this.lead = this.body.indexOf("");
        if (this.lead < 0) {
            this.lead = this.body.size();
        }
    }

    // ----------------------------------------------------------------- drawing --

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ensureLayout();
        long now = System.nanoTime();
        float delta = Math.min((now - this.lastFrameNanos) / 1_000_000_000.0F, 0.1F);
        this.lastFrameNanos = now;
        this.elapsed += delta;

        float fade = Ease.outCubic(this.elapsed / 0.35F);

        // Opaque, and overdrawn: this covers a loading screen mid-paint, and any gap
        // at the edge shows a strip of it.
        Draw.rect(-this.width, -this.height, this.width * 2, this.height * 2, Theme.background);

        drawHeading(fade);
        drawBody(fade);

        if (Quality.vignette()) {
            Draw.vignette(this.width, this.height, 0.8F * fade, 0xFF000000);
        }
        if (Quality.scanlines()) {
            Draw.scanlines(this.width, this.height, 3.0F, Draw.withAlpha(0x000000, 0.10F * fade));
        }

        // Not super.drawScreen: GuiNotification's own body draws the dirt background
        // and the raw text block this screen exists to replace. Only the widget pass
        // is wanted, so it is done here.
        for (int i = 0; i < this.buttonList.size(); i++) {
            GuiButton button = (GuiButton) this.buttonList.get(i);
            if (button instanceof MenuButton) {
                ((MenuButton) button).advance(delta, fade);
            }
            button.drawButton(this.mc, mouseX, mouseY);
        }
    }

    private void drawHeading(float fade) {
        int centerX = this.width / 2;
        int y = 30;

        // An accent bar rather than an icon: the pack ships no artwork, and the one
        // thing this screen has to say before anything else is "stop and read".
        int accent = this.confirmation ? Theme.danger : Theme.accent;
        Draw.rect(centerX - 14, y - 12, centerX + 14, y - 11, Draw.withAlpha(accent, 0.9F * fade));

        this.drawCenteredString(this.fontRendererObj, this.heading, centerX, y,
                Draw.withAlpha(Theme.text, fade));
    }

    private void drawBody(float fade) {
        int centerX = this.width / 2;
        int top = 52;
        int maxScroll = Math.max(0, this.body.size() - this.visibleLines);
        if (this.scroll > maxScroll) {
            this.scroll = maxScroll;
        }

        int drawn = 0;
        for (int i = this.scroll; i < this.body.size() && drawn < this.visibleLines; i++, drawn++) {
            String line = this.body.get(i);
            if (line.isEmpty()) {
                continue;
            }
            // The first paragraph is what the player has to decide on; everything
            // after it is the evidence. Drawing both at the same weight is what made
            // the stock screen read as an undifferentiated wall.
            int colour = i < this.lead
                    ? Draw.withAlpha(Theme.text, 0.92F * fade)
                    : Draw.withAlpha(Theme.textDim, 0.85F * fade);
            this.drawCenteredString(this.fontRendererObj, line, centerX, top + drawn * LINE_H,
                    colour);
        }

        if (maxScroll > 0) {
            // Says both that there is more and that the wheel is what reaches it —
            // the stock screen printed "..." and left the rest unreadable.
            String hint = (this.scroll + drawn) + " / " + this.body.size();
            this.drawCenteredString(this.fontRendererObj, hint, centerX,
                    top + this.visibleLines * LINE_H + 4,
                    Draw.withAlpha(Theme.accent, 0.7F * fade));
        }
    }

    // ------------------------------------------------------------------- input --

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int maxScroll = Math.max(0, this.body.size() - this.visibleLines);
            this.scroll = Math.max(0, Math.min(maxScroll, this.scroll + (wheel > 0 ? -2 : 2)));
        }
    }

    /**
     * Escape answers the question instead of walking away from it.
     *
     * {@code GuiScreen} closes on Escape, and closing this one leaves the thread that
     * asked waiting on an answer that can no longer arrive — the load hangs with no
     * screen up and no way to get one back. Escape therefore means "no" where there
     * is a choice, and "acknowledged" where there is not.
     */
    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            answer(!this.confirmation);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            answer(true);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) {
            return;
        }
        // No click sound here: MenuButton plays its own from func_146113_a, which
        // GuiScreen calls on the way to this method.
        answer(button.id == 0);
    }

    /**
     * Hands the answer back and lets the waiting thread go.
     *
     * Guarded because both the button and the key can arrive in the same batch of
     * input events, and counting down a latch twice for one question puts the load
     * past a query nobody answered.
     */
    private void answer(boolean yes) {
        if (this.answered) {
            return;
        }
        this.answered = true;
        FMLClientHandler.instance().showGuiScreen(null);
        if (this.confirmation) {
            this.query.setResult(yes);
        }
        this.query.finish();
    }
}
