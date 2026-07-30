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
    private float targetScroll;
    private float scroll;
    private boolean dragging;
    private int dragStartY;
    private float dragStartScroll;

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
        boolean inside = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
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

        drawScrollRail(alpha);
        drawEdgeFade(alpha);
    }

    /** Thin rail on the right, only while there is somewhere to scroll. */
    private void drawScrollRail(float alpha) {
        float max = maxScroll();
        if (max <= 0.0F) {
            return;
        }
        float railX = x + width - 2.0F;
        Draw.rect(railX, y, railX + 1.0F, y + height, Draw.withAlpha(Theme.textDim, 0.12F * alpha));

        float thumbHeight = Math.max(16.0F, height * (height / (float) (rowCount() * rowHeight)));
        float thumbY = y + (height - thumbHeight) * (scroll / max);
        Draw.rect(railX - 1.0F, thumbY, railX + 2.0F, thumbY + thumbHeight,
                Draw.withAlpha(Theme.accent, 0.55F * alpha));
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

    public boolean mouseClicked(int mouseX, int mouseY) {
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

    public void mouseDragged(int mouseY) {
        if (!dragging) {
            return;
        }
        // Drag moves the content with the pointer, the way a touch surface would.
        targetScroll = dragStartScroll - (mouseY - dragStartY);
        clampScroll();
    }

    public void mouseReleased() {
        this.dragging = false;
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
