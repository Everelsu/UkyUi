package com.console.uky.client.gui;

/**
 * Arrow keys over a grid of cards — the world tiles, the server tiles.
 *
 * <p>Those screens draw their cards rather than building buttons, so the base screen's
 * navigation never sees them. This keeps the one thing they need: which card the
 * keyboard is on, moved by the arrows, and handed back to the mouse the moment the
 * pointer moves — two lit cards, one for each, would leave nobody sure which one Enter
 * opens.
 */
public final class GridKeys {

    private int slot = -1;
    private boolean active;
    private int lastX = Integer.MIN_VALUE;
    private int lastY = Integer.MIN_VALUE;

    /** Call with the pointer every frame; moving it hands control back to the mouse. */
    public void pointer(int mouseX, int mouseY) {
        if (this.lastX != Integer.MIN_VALUE && (mouseX != this.lastX || mouseY != this.lastY)) {
            this.active = false;
        }
        this.lastX = mouseX;
        this.lastY = mouseY;
    }

    public boolean active() {
        return this.active && this.slot >= 0;
    }

    /** The card the keyboard is on, as a slot index (0 is the first card). */
    public int slot() {
        return this.slot;
    }

    /**
     * Moves with an arrow key (LWJGL 2 codes). The first press only lights a card —
     * the one under the pointer if there is one, else the first.
     *
     * @return whether the key was an arrow
     */
    public boolean move(int keyCode, int total, int columns, int hoveredSlot) {
        int dx = keyCode == 203 ? -1 : (keyCode == 205 ? 1 : 0);
        int dy = keyCode == 200 ? -1 : (keyCode == 208 ? 1 : 0);
        if ((dx == 0 && dy == 0) || total <= 0) {
            return false;
        }
        if (!this.active) {
            this.active = true;
            this.slot = hoveredSlot >= 0 && hoveredSlot < total ? hoveredSlot : 0;
            return true;
        }
        int next = this.slot + dx + dy * Math.max(1, columns);
        if (next >= 0 && next < total) {
            this.slot = next;
        } else if (dy > 0 && this.slot / Math.max(1, columns) < (total - 1) / Math.max(1, columns)) {
            // Down from a short last row's neighbour lands on the last card.
            this.slot = total - 1;
        }
        return true;
    }

    /** Keeps the slot inside a list that just shrank. */
    public void clamp(int total) {
        if (this.slot >= total) {
            this.slot = total - 1;
        }
    }
}
