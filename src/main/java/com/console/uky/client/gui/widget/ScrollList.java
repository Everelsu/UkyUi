package com.console.uky.client.gui.widget;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;

/**
 * Scrolling list of fixed-height rows.
 *
 * Vanilla's {@code GuiSlot} snaps to the wheel and draws its own dirt-textured
 * chrome, neither of which fits here, so this is a small replacement: scrolling
 * eases toward its target, rows report their own hover, and the only chrome is a
 * thin rail that appears when there is something to scroll.
 *
 * Subclasses supply the row count and draw each row; the list handles clipping,
 * momentum and hit-testing.
 */
public abstract class ScrollList {

    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected int rowHeight;

    /** Where the list wants to be scrolled to, and where it currently is. */
    /** How far in from the right edge the rail is drawn. */
    private static final float RAIL_WIDTH = 2.0F;
    /**
     * How much of that edge answers to the mouse.
     *
     * Deliberately several times the width of what is drawn. A three-pixel target is
     * one most people miss on the first try and some never hit at all, and the cost of
     * being generous here is a strip of empty panel beside the rows that scrolls
     * instead of selecting — which is what it looks like it should do anyway.
     */
    private static final float GRAB_WIDTH = 9.0F;

    private float targetScroll;
    private float scroll;
    private boolean dragging;
    private int dragStartY;
    private float dragStartScroll;
    /** True while the thumb itself is being dragged, as opposed to the content. */
    private boolean railDragging;
    /** Where on the thumb it was taken hold of, so it does not jump on grab. */
    private float railGrabOffset;

    /** Row under the pointer, or -1. */
    protected int hovered = -1;
    /** Row the user has selected, or -1. */
    protected int selected = -1;

    public void setBounds(int x, int y, int width, int height, int rowHeight) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.rowHeight = rowHeight;
        clampScroll();
    }

    public abstract int rowCount();

    /**
     * Draws one row. Coordinates are absolute; the list has already clipped to its
     * bounds and worked out {@code hovered}/{@code selected}.
     */
    protected abstract void drawRow(int index, int rowX, int rowY, int rowWidth, int rowHeight,
                                    boolean isHovered, boolean isSelected, float alpha);

    /** Called when a row is clicked. */
    protected void onRowClicked(int index) {
    }

    public int getSelected() {
        return this.selected;
    }

    public void setSelected(int index) {
        this.selected = index;
    }

    /** Scrolls so that {@code index} is inside the viewport. */
    public void scrollTo(int index) {
        float rowTop = index * rowHeight;
        float rowBottom = rowTop + rowHeight;
        if (rowTop < targetScroll) {
            targetScroll = rowTop;
        } else if (rowBottom > targetScroll + height) {
            targetScroll = rowBottom - height;
        }
        clampScroll();
    }

    /**
     * Scrolls so that {@code index} sits in the middle of the viewport.
     *
     * For arriving at a row rather than merely being able to see it: a list opened
     * from a link elsewhere has to answer "where is it" before it answers "is it
     * visible", and a row that has just been scrolled to the very bottom edge of the
     * list looks like one that happened to be there already.
     */
    public void scrollToCenter(int index) {
        targetScroll = index * rowHeight + rowHeight * 0.5F - height * 0.5F;
        clampScroll();
    }

    private float maxScroll() {
        return Math.max(0.0F, rowCount() * rowHeight - height);
    }

    private void clampScroll() {
        float max = maxScroll();
        if (targetScroll > max) {
            targetScroll = max;
        }
        if (targetScroll < 0.0F) {
            targetScroll = 0.0F;
        }
    }

    public void update(float deltaSeconds) {
        this.scroll = Ease.approach(this.scroll, this.targetScroll, 0.055F, deltaSeconds);
    }

    public void draw(int mouseX, int mouseY, float alpha) {
        // The rail's strip is not part of the rows: a row that highlights while the
        // pointer is on the scrollbar invites a click that will not select it.
        boolean inside = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
                && !overRail(mouseX, mouseY);
        this.hovered = -1;

        Draw.beginClip(x, y, width, height);
        int count = rowCount();
        // Only the rows actually on screen are drawn; a mod list can be hundreds long.
        int first = Math.max(0, (int) (scroll / rowHeight));
        int last = Math.min(count, (int) ((scroll + height) / rowHeight) + 1);

        for (int i = first; i < last; i++) {
            int rowY = (int) (y + i * rowHeight - scroll);
            boolean isHovered = inside && mouseY >= rowY && mouseY < rowY + rowHeight;
            if (isHovered) {
                this.hovered = i;
            }
            drawRow(i, x, rowY, width, rowHeight, isHovered, i == selected, alpha);
        }
        Draw.endClip();

        drawEdgeFade(alpha);
        // After the edge fade, not before: the rail is a control and must not be
        // faded out at the ends of its own travel.
        drawScrollRail(mouseX, mouseY, alpha);
    }

    /**
     * The rail on the right, and the thumb you can actually take hold of.
     *
     * It used to be three pixels wide and purely an indicator — the click that landed
     * on it was treated as a click on the list behind it, so grabbing it scrolled the
     * content the wrong way and selected whatever row happened to be under the
     * pointer. It is now a control: {@link #GRAB_WIDTH} units of it respond to the
     * mouse, which is wider than it is drawn, because a bar you have to hit within
     * three pixels is a bar you miss.
     */
    private void drawScrollRail(int mouseX, int mouseY, float alpha) {
        float max = maxScroll();
        if (max <= 0.0F) {
            return;
        }
        float railX = x + width - RAIL_WIDTH;
        Draw.rect(railX, y, railX + 1.0F, y + height, Draw.withAlpha(Theme.textDim, 0.12F * alpha));

        float thumbHeight = thumbHeight();
        float thumbY = thumbY(max, thumbHeight);
        boolean hot = this.railDragging || overRail(mouseX, mouseY);

        // Wider and brighter under the pointer, so it is clear it can be taken hold
        // of before it is.
        float half = hot ? 2.5F : 1.5F;
        Draw.rect(railX - half + 0.5F, thumbY, railX + half + 0.5F, thumbY + thumbHeight,
                Draw.withAlpha(Theme.accent, (hot ? 0.95F : 0.55F) * alpha));
        if (hot) {
            Draw.rect(railX - half - 1.0F, thumbY, railX - half + 0.5F, thumbY + thumbHeight,
                    Draw.withAlpha(Theme.accent, 0.25F * alpha));
        }
    }

    private float thumbHeight() {
        float content = rowCount() * (float) rowHeight;
        return Math.max(16.0F, height * (height / content));
    }

    private float thumbY(float max, float thumbHeight) {
        return y + (height - thumbHeight) * (scroll / max);
    }

    /** Whether the pointer is in the strip the rail answers to. */
    private boolean overRail(int mouseX, int mouseY) {
        return maxScroll() > 0.0F
                && mouseX >= x + width - GRAB_WIDTH && mouseX <= x + width + 2
                && mouseY >= y && mouseY < y + height;
    }

    /** Softens the clip edges so rows dissolve rather than being sliced off. */
    private void drawEdgeFade(float alpha) {
        float fade = 10.0F;
        Draw.gradientV(x, y, x + width, y + fade,
                Draw.withAlpha(Theme.background, 0.85F * alpha),
                Draw.withAlpha(Theme.background, 0.0F));
        Draw.gradientV(x, y + height - fade, x + width, y + height,
                Draw.withAlpha(Theme.background, 0.0F),
                Draw.withAlpha(Theme.background, 0.85F * alpha));
    }

    // ----------------------------------------------------------------- input --

    /**
     * Index of the row under the pointer, or -1 if outside the list.
     *
     * Split out from {@link #mouseClicked} so a screen can test what a click landed
     * on before letting the list act on it — a row with its own controls inside needs
     * to claim the click first.
     */
    public int rowIndexAt(int mouseX, int mouseY) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return -1;
        }
        // Nothing is under the scrollbar as far as the screens are concerned. They use
        // this to spot a click on a control inside a row before handing it to the
        // list, and a delete button that fires because the bar was grabbed over the
        // top of it is the worst version of this bug.
        if (overRail(mouseX, mouseY)) {
            return -1;
        }
        int index = (int) ((mouseY - y + scroll) / rowHeight);
        return index >= 0 && index < rowCount() ? index : -1;
    }

    /** Right edge of a row, for placing controls inside one. */
    public int rowRight() {
        return x + width;
    }

    /** Screen y of a row's top edge, accounting for the current scroll. */
    public int rowTop(int index) {
        return (int) (y + index * rowHeight - scroll);
    }

    public boolean mouseClicked(int mouseX, int mouseY) {
        // The rail first, and over its own wider strip: it sits on top of the rows,
        // and a click meant for it must not also land on the row behind it.
        if (overRail(mouseX, mouseY)) {
            grabRail(mouseY);
            return true;
        }
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }
        int index = (int) ((mouseY - y + scroll) / rowHeight);
        if (index >= 0 && index < rowCount()) {
            this.selected = index;
            onRowClicked(index);
        }
        this.dragging = true;
        this.dragStartY = mouseY;
        this.dragStartScroll = targetScroll;
        return true;
    }

    /**
     * Takes hold of the thumb, or jumps to where the track was clicked.
     *
     * Clicking the track above or below the thumb centres it on the pointer and then
     * carries on as a drag, which is what every scrollbar does and what makes a single
     * click on a long list land somewhere useful.
     */
    private void grabRail(int mouseY) {
        float max = maxScroll();
        float thumbHeight = thumbHeight();
        float thumbY = thumbY(max, thumbHeight);

        this.railDragging = true;
        this.dragging = false;
        if (mouseY >= thumbY && mouseY <= thumbY + thumbHeight) {
            // Grabbed where it was held, so the thumb does not jump under the pointer.
            this.railGrabOffset = mouseY - thumbY;
        } else {
            this.railGrabOffset = thumbHeight * 0.5F;
            dragRailTo(mouseY);
        }
    }

    private void dragRailTo(int mouseY) {
        float max = maxScroll();
        float thumbHeight = thumbHeight();
        float travel = height - thumbHeight;
        if (travel <= 0.0F) {
            return;
        }
        float t = (mouseY - this.railGrabOffset - y) / travel;
        targetScroll = max * (t < 0.0F ? 0.0F : (t > 1.0F ? 1.0F : t));
        clampScroll();
        // The bar has to track the pointer exactly, so the easing that makes the wheel
        // feel smooth is skipped here — a thumb that lags behind the mouse it is being
        // dragged by reads as the list being stuck.
        this.scroll = targetScroll;
    }

    public void mouseDragged(int mouseY) {
        if (this.railDragging) {
            dragRailTo(mouseY);
            return;
        }
        if (!dragging) {
            return;
        }
        // Drag moves the content with the pointer, the way a touch surface would.
        targetScroll = dragStartScroll - (mouseY - dragStartY);
        clampScroll();
    }

    public void mouseReleased() {
        this.dragging = false;
        this.railDragging = false;
    }

    /** @param notches positive scrolls up, matching LWJGL's wheel sign */
    public void mouseWheel(int notches) {
        targetScroll -= notches * rowHeight * 1.5F;
        clampScroll();
    }

    public void moveSelection(int delta) {
        int count = rowCount();
        if (count == 0) {
            return;
        }
        int next = selected < 0 ? 0 : selected + delta;
        if (next < 0) {
            next = 0;
        } else if (next >= count) {
            next = count - 1;
        }
        selected = next;
        scrollTo(next);
    }
}
