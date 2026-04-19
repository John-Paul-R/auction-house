package co.lemee.auctionhouse.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A scissor-clipped, scrollable list widget whose entries may each report a
 * different height.  Unlike {@link net.minecraft.client.gui.components.ObjectSelectionList},
 * which uses a single fixed item-height for all rows, this widget computes row
 * positions by summing individual entry heights — enabling compact rows for
 * simple items and expanded rows for complex ones (e.g. enchanted books).
 *
 * <p>Register it with {@code Screen.addRenderableWidget()} as normal.  The
 * Screen's input-routing will forward {@code mouseScrolled}, {@code
 * mouseDragged}, and {@code mouseReleased} automatically once a click is
 * consumed by this widget.
 */
public class VariableHeightList extends AbstractWidget {

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final int SCROLLBAR_W   = 6;
    /** Pixels scrolled per one notch of the mouse wheel. */
    private static final int SCROLL_SPEED  = 12;
    private static final int SCROLLBAR_BG  = 0x40FFFFFF;
    private static final int SCROLLBAR_FG  = 0xFFAAAAAA;

    // ── State ─────────────────────────────────────────────────────────────────

    private final Font        font;
    private final List<Entry> entries     = new ArrayList<>();
    private       int         totalHeight = 0;
    private       double      scrollAmt   = 0.0;

    /** True while the user is dragging the scrollbar thumb. */
    private boolean scrollDragging  = false;
    private double  dragAnchorY     = 0;
    private double  dragAnchorScroll = 0;

    /** Entry under the cursor this frame; used for narration. */
    private Entry hoveredEntry = null;

    // ── Construction ──────────────────────────────────────────────────────────

    public VariableHeightList(Font font, int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
        this.font = font;
    }

    // ── Entry management ──────────────────────────────────────────────────────

    public void setEntries(List<Entry> newEntries) {
        entries.clear();
        entries.addAll(newEntries);
        recomputeTotalHeight();
        scrollAmt = 0.0;
    }

    private void recomputeTotalHeight() {
        totalHeight = 0;
        for (Entry e : entries) {
            totalHeight += e.getHeight(font);
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int lx = getX();
        int ly = getY();
        int lw = getWidth();
        int lh = getHeight();
        int contentW = lw - SCROLLBAR_W - 2;

        // Clip all entry rendering to our bounds
        g.enableScissor(lx, ly, lx + lw, ly + lh);

        int drawY = ly - (int) scrollAmt;
        hoveredEntry = null;

        for (Entry entry : entries) {
            int eh = entry.getHeight(font);
            // Skip entries fully above or below the visible region
            if (drawY + eh > ly && drawY < ly + lh) {
                boolean hov = mouseX >= lx && mouseX < lx + contentW
                           && mouseY >= drawY && mouseY < drawY + eh;
                if (hov) hoveredEntry = entry;
                entry.render(g, lx, drawY, contentW, mouseX, mouseY, hov, font);
            }
            drawY += eh;
        }

        g.disableScissor();

        // Scrollbar — only drawn when content overflows
        if (totalHeight > lh) {
            int sbX   = lx + lw - SCROLLBAR_W;
            int thumbH = Math.max(16, (int) ((double) lh / totalHeight * lh));
            double maxScroll = totalHeight - lh;
            int thumbY = ly + (int) ((lh - thumbH) * (scrollAmt / maxScroll));

            g.fill(sbX, ly,     sbX + SCROLLBAR_W, ly + lh,       SCROLLBAR_BG);
            g.fill(sbX, thumbY, sbX + SCROLLBAR_W, thumbY + thumbH, SCROLLBAR_FG);
        }
    }

    // ── Mouse input ───────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        double maxScroll = Math.max(0.0, totalHeight - getHeight());
        scrollAmt = Math.clamp(scrollAmt - scrollY * SCROLL_SPEED, 0.0, maxScroll);
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        double mx = event.x();
        double my = event.y();

        if (!isMouseOver(mx, my)) return false;

        // ── Scrollbar thumb drag ──
        int sbX = getX() + getWidth() - SCROLLBAR_W;
        if (mx >= sbX && totalHeight > getHeight()) {
            scrollDragging   = true;
            dragAnchorY      = my;
            dragAnchorScroll = scrollAmt;
            return true;
        }

        // ── Entry click ──
        int contentW = getWidth() - SCROLLBAR_W - 2;
        if (mx >= getX() && mx < getX() + contentW) {
            int drawY = getY() - (int) scrollAmt;
            for (Entry entry : entries) {
                int eh = entry.getHeight(font);
                if (my >= drawY && my < drawY + eh) {
                    return entry.mouseClicked(event, focused);
                }
                drawY += eh;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (scrollDragging && totalHeight > getHeight()) {
            double maxScroll  = totalHeight - getHeight();
            double thumbH     = Math.max(16.0, (double) getHeight() / totalHeight * getHeight());
            double scrollable = getHeight() - thumbH;
            if (scrollable > 0) {
                double delta = (event.y() - dragAnchorY) / scrollable * maxScroll;
                scrollAmt = Math.clamp(dragAnchorScroll + delta, 0.0, maxScroll);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        scrollDragging = false;
        return super.mouseReleased(event);
    }

    // ── Narration ─────────────────────────────────────────────────────────────

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        if (hoveredEntry != null) {
            out.add(NarratedElementType.TITLE, hoveredEntry.getNarration());
        }
    }

    // ── Abstract entry ────────────────────────────────────────────────────────

    /**
     * One row in a {@link VariableHeightList}.  Implementations decide their
     * own height and handle their own rendering and click logic.
     */
    public abstract static class Entry {

        /**
         * Height of this entry in screen pixels.  May vary per entry based on
         * content (e.g. number of enchantments).  {@code font} is provided so
         * implementations can size themselves relative to {@code font.lineHeight}.
         */
        public abstract int getHeight(Font font);

        /**
         * Render the entry.
         *
         * @param g       graphics context
         * @param x       left edge of the entry (list x, scrollbar excluded)
         * @param y       top edge of the entry in screen space (already offset by scroll)
         * @param width   available width (scrollbar already subtracted)
         * @param mouseX  current mouse X
         * @param mouseY  current mouse Y
         * @param hovered whether the mouse is over this entry
         * @param font    the active font
         */
        public abstract void render(GuiGraphics g, int x, int y, int width,
                                    int mouseX, int mouseY, boolean hovered, Font font);

        /** Called when the left mouse button is pressed inside this entry's bounds. */
        public abstract boolean mouseClicked(MouseButtonEvent event, boolean focused);

        /** Screen-reader narration text for this entry. */
        public abstract Component getNarration();
    }
}
