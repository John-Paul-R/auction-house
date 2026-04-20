package co.lemee.auctionhouse.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A simple flat-filled button widget for auction-house screens.
 *
 * <p>Register with {@code Screen.addRenderableWidget()} in {@code init()}.
 * MC's input routing will forward mouse events automatically; no custom
 * iteration loops are needed in the host screen.
 *
 * <p>Visibility and activity can be toggled via the inherited
 * {@code setVisible} / {@code active} members ({@code AbstractWidget} exposes
 * {@code visible} as a public field and {@code active} as a public field in
 * 1.21).  When {@code visible} is {@code false} the widget is neither rendered
 * nor hit-tested.
 *
 * <p>Predefined colour pairs ({@link #GREEN} and {@link #RED}) match the
 * existing overlay button palette; custom palettes can be supplied via the
 * full constructor.
 */
public class UIButton extends AbstractWidget {

    // ── Predefined styles ─────────────────────────────────────────────────────

    /** Style constants for a single button. */
    public record Style(int normalBg, int hoverBg, int textColor) {
        /** Green confirm-style button. */
        public static final Style GREEN = new Style(0xFF1E521E, 0xFF2A6E2A, 0xFF55FF55);
        /** Red cancel/danger-style button. */
        public static final Style RED   = new Style(0xFF521E1E, 0xFF6E2A2A, 0xFFFF5555);
    }

    // ── Instance state ────────────────────────────────────────────────────────

    private final Font     font;
    private final Style    style;
    private final String   label;
    private final Runnable onClick;

    // ── Construction ──────────────────────────────────────────────────────────

    /**
     * @param font    font used to centre the label (stored; not re-queried each frame)
     * @param x       left edge in screen pixels
     * @param y       top edge in screen pixels
     * @param w       width in pixels
     * @param h       height in pixels
     * @param label   button label text
     * @param style   fill + text colour pair
     * @param onClick action invoked when the button is left-clicked
     */
    public UIButton(Font font, int x, int y, int w, int h,
                    String label, Style style, Runnable onClick) {
        super(x, y, w, h, Component.literal(label));
        this.font    = font;
        this.style   = style;
        this.label   = label;
        this.onClick = onClick;
        // Start hidden — caller enables when appropriate
        this.visible = false;
        this.active  = false;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Move the button to a new screen position without recreating the instance
     * (and without rewiring the onClick closure).  Call from the host screen's
     * {@code init()} when overlay geometry depends on {@code this.width /
     * this.height}.
     */
    public void repositionTo(int x, int y) {
        this.setX(x);
        this.setY(y);
    }

    /** Show and enable this button. */
    public void show() {
        this.visible = true;
        this.active  = true;
    }

    /** Hide and disable this button. */
    public void hide() {
        this.visible = false;
        this.active  = false;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        boolean hovered = isMouseOver(mouseX, mouseY);
        g.fill(x, y, x + w, y + h, hovered ? style.hoverBg() : style.normalBg());

        int textX = x + w / 2;
        // Use glyph height (lineHeight - 1) for centering, not lineHeight,
        // which includes 1px of line-spacing and would push the text 1px too high.
        int textY = y + (h - (font.lineHeight - 1)) / 2;
        g.drawCenteredString(font, label, textX, textY, style.textColor());
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        if (!active || !visible) return false;
        if (event.button() != 0) return false;
        if (!isMouseOver(event.x(), event.y())) return false;
        onClick.run();
        return true;
    }

    // ── Narration ─────────────────────────────────────────────────────────────

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, Component.literal(label));
    }
}
