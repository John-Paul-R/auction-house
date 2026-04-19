package co.lemee.auctionhouse.gui;

import co.lemee.auctionhouse.AuctionHouseMod;
import co.lemee.auctionhouse.network.ClientAuctionItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Client-side search screen for the auction house.
 * <p>
 * Layout (roughly):
 * <pre>
 *   ┌─────────────────────────────────────┐
 *   │         Auction House Search        │  ← title
 *   │  [ Search items...               ] │  ← EditBox
 *   │  ┌─────────────────────────────┐   │
 *   │  │ [icon] Item name   $price   │   │
 *   │  │        by seller   time     │   │
 *   │  │ ...  (scrollable)          ↕│   │
 *   │  └─────────────────────────────┘   │
 *   │  Showing X / Y listings            │  ← status
 *   └─────────────────────────────────────┘
 * </pre>
 * The server pushes a full {@link co.lemee.auctionhouse.network.AuctionHouseListingsPayload}
 * snapshot when the screen opens, and again in response to a
 * {@link co.lemee.auctionhouse.network.AuctionHouseRequestListingsPayload} sent every
 * {@value #REFRESH_INTERVAL} ticks (~3 s).  Filtering is applied locally on the client.
 */
public class GUIAuctionHouseSearch extends Screen {

    // --- Constants -----------------------------------------------------------

    private static final int REFRESH_INTERVAL = 60; // ticks ≈ 3 s
    private static final int PANEL_MARGIN     = 10;
    private static final int TITLE_H          = 14;
    private static final int BOX_H            = 20;
    private static final int STATUS_H         = 12;
    private static final int ENTRY_H          = 28;
    private static final int INNER_PAD        = 4;

    // --- State ---------------------------------------------------------------

    private List<ClientAuctionItem> allItems      = new ArrayList<>();
    private List<ClientAuctionItem> filteredItems = new ArrayList<>();
    private String                  lastQuery     = null;

    // --- Widgets -------------------------------------------------------------

    private EditBox  searchBox;
    private ResultList resultList;

    // --- Buy confirmation state -------------------------------------------

    /** Non-null when the player has clicked a listing and is seeing the buy prompt. */
    @Nullable private ClientAuctionItem pendingBuy = null;
    // Bounding boxes set each frame during drawBuyOverlay() — used in mouseClicked()
    private int overlayX, overlayY, overlayW, overlayH;
    private int confirmBX, confirmBY, confirmBW, confirmBH;
    private int cancelBX,  cancelBY,  cancelBW,  cancelBH;

    // --- Timing --------------------------------------------------------------

    private int tickCount = 0;

    // =========================================================================
    // Construction
    // =========================================================================

    public GUIAuctionHouseSearch(Component title) {
        super(title);
    }

    // =========================================================================
    // Static helper — called from the network handler (main thread)
    // =========================================================================

    /**
     * Delivers a fresh listing snapshot to the currently-open search screen.
     * Safe to call from the client-networking thread via {@code context.client().execute(...)}.
     */
    public static void updateListings(List<ClientAuctionItem> newListings) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GUIAuctionHouseSearch screen) {
            screen.onListingsUpdate(newListings);
        }
    }

    private void onListingsUpdate(List<ClientAuctionItem> newListings) {
        this.allItems = new ArrayList<>(newListings);
        this.lastQuery = null; // force a refilter pass
        applyFilter();
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void init() {
        int panelW = Math.min(480, this.width  - PANEL_MARGIN * 2);
        int panelH = this.height - PANEL_MARGIN * 2;
        int panelX = (this.width  - panelW) / 2;
        int panelY = PANEL_MARGIN;

        // -- Search box -------------------------------------------------------
        int boxX = panelX + INNER_PAD;
        int boxY = panelY + TITLE_H + INNER_PAD;
        int boxW = panelW - INNER_PAD * 2;

        searchBox = new EditBox(this.font, boxX, boxY, boxW, BOX_H, Component.literal("Search..."));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.literal("Search items or sellers…").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setResponder(text -> {
            this.lastQuery = null; // trigger refilter
            applyFilter();
        });
        this.addRenderableWidget(searchBox);
        this.setInitialFocus(searchBox);

        // -- Result list ------------------------------------------------------
        int listY = boxY + BOX_H + INNER_PAD;
        int listH = panelY + panelH - listY - STATUS_H - INNER_PAD;

        resultList = new ResultList(this.minecraft, panelW - 2, listH, listY, ENTRY_H);
        resultList.setX(panelX + 1);
        this.addRenderableWidget(resultList);

        applyFilter();
    }

    @Override
    public void tick() {
        super.tick();
        tickCount++;
        if (tickCount >= REFRESH_INTERVAL) {
            tickCount = 0;
            AuctionHouseMod.requestListingsRefresh.run();
        }
    }

    /**
     * Draws the blurred/dimmed game-world backdrop, the panel chrome, and the title.
     * Called exactly once per frame by {@link #render} via {@code super.render()}.
     * Must NOT be called manually — doing so causes "Can only blur once per frame".
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // Blur + dim (vanilla MC 1.21 mechanism — must happen exactly once per frame)
        super.renderBackground(g, mouseX, mouseY, delta);

        int panelW = Math.min(480, this.width  - PANEL_MARGIN * 2);
        int panelH = this.height - PANEL_MARGIN * 2;
        int panelX = (this.width  - panelW) / 2;
        int panelY = PANEL_MARGIN;

        // Panel background
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xC0101010);

        // Panel border (4 thin lines)
        int col = 0xFF444444;
        g.fill(panelX,              panelY,              panelX + panelW, panelY + 1,           col);
        g.fill(panelX,              panelY + panelH - 1, panelX + panelW, panelY + panelH,      col);
        g.fill(panelX,              panelY,              panelX + 1,      panelY + panelH,       col);
        g.fill(panelX + panelW - 1, panelY,              panelX + panelW, panelY + panelH,      col);

        // Title (above the search box — behind no widget so safe to draw here)
        g.drawCenteredString(this.font, this.title, this.width / 2, panelY + 3, 0xFFFFFFFF);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // super.render() calls renderBackground() once, then renders all registered widgets
        super.render(g, mouseX, mouseY, delta);

        // Status line — drawn after widgets so it appears on top if anything overlaps
        int panelW = Math.min(480, this.width  - PANEL_MARGIN * 2);
        int panelH = this.height - PANEL_MARGIN * 2;
        int panelX = (this.width  - panelW) / 2;
        int panelY = PANEL_MARGIN;
        String status = filteredItems.size() + " / " + allItems.size() + " listings";
        g.drawString(this.font, status,
                panelX + INNER_PAD,
                panelY + panelH - STATUS_H,
                0xFF888888, false);

        // Buy confirmation overlay — rendered on top of everything
        if (pendingBuy != null) {
            drawBuyOverlay(g, mouseX, mouseY);
        }
    }

    /** Draws the buy-confirmation modal panel. Must be called after all widget rendering. */
    private void drawBuyOverlay(GuiGraphics g, int mouseX, int mouseY) {
        overlayW = 240;
        overlayH = 94;
        overlayX = (this.width  - overlayW) / 2;
        overlayY = (this.height - overlayH) / 2;

        // Dim the rest of the screen
        g.fill(0, 0, this.width, this.height, 0x80000000);

        // Panel background + border
        g.fill(overlayX, overlayY, overlayX + overlayW, overlayY + overlayH, 0xE0101010);
        int bCol = 0xFF555555;
        g.fill(overlayX,              overlayY,              overlayX + overlayW, overlayY + 1,           bCol);
        g.fill(overlayX,              overlayY + overlayH - 1, overlayX + overlayW, overlayY + overlayH,  bCol);
        g.fill(overlayX,              overlayY,              overlayX + 1,          overlayY + overlayH,   bCol);
        g.fill(overlayX + overlayW - 1, overlayY,            overlayX + overlayW,  overlayY + overlayH,   bCol);

        // Item icon + name
        int iconX = overlayX + 8;
        int iconY = overlayY + 10;
        g.renderItem(pendingBuy.itemStack(), iconX, iconY);
        String name = pendingBuy.itemStack().getHoverName().getString();
        int maxNameW = overlayW - 36;
        if (this.font.width(name) > maxNameW) {
            name = this.font.plainSubstrByWidth(name, maxNameW - 6) + "\u2026";
        }
        g.drawString(this.font, name, iconX + 20, iconY + 4, 0xFFFFFFFF, false);

        // Price line
        String priceLine = String.format("Buy for $%.2f?", pendingBuy.price());
        int priceW = this.font.width(priceLine);
        g.drawString(this.font, priceLine,
                overlayX + (overlayW - priceW) / 2, overlayY + 36, 0xFFFFAA00, false);

        // Buttons
        int btnW = 90;
        int btnH = 20;
        int btnY = overlayY + 58;
        confirmBX = overlayX + 16;               confirmBY = btnY; confirmBW = btnW; confirmBH = btnH;
        cancelBX  = overlayX + overlayW - 16 - btnW; cancelBY = btnY; cancelBW = btnW; cancelBH = btnH;

        boolean confirmHov = mouseX >= confirmBX && mouseX < confirmBX + confirmBW
                          && mouseY >= confirmBY && mouseY < confirmBY + confirmBH;
        boolean cancelHov  = mouseX >= cancelBX  && mouseX < cancelBX  + cancelBW
                          && mouseY >= cancelBY  && mouseY < cancelBY  + cancelBH;

        g.fill(confirmBX, confirmBY, confirmBX + confirmBW, confirmBY + confirmBH,
                confirmHov ? 0xFF2A6E2A : 0xFF1E521E);
        g.drawCenteredString(this.font, "Confirm",
                confirmBX + confirmBW / 2, confirmBY + (confirmBH - this.font.lineHeight) / 2, 0xFF55FF55);

        g.fill(cancelBX, cancelBY, cancelBX + cancelBW, cancelBY + cancelBH,
                cancelHov ? 0xFF6E2A2A : 0xFF521E1E);
        g.drawCenteredString(this.font, "Cancel",
                cancelBX + cancelBW / 2, cancelBY + (cancelBH - this.font.lineHeight) / 2, 0xFFFF5555);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        if (pendingBuy != null && event.button() == 0) {
            int mx = (int) event.x();
            int my = (int) event.y();
            // Confirm
            if (mx >= confirmBX && mx < confirmBX + confirmBW && my >= confirmBY && my < confirmBY + confirmBH) {
                AuctionHouseMod.sendBuy.accept(pendingBuy.id());
                this.onClose();
                return true;
            }
            // Cancel
            if (mx >= cancelBX && mx < cancelBX + cancelBW && my >= cancelBY && my < cancelBY + cancelBH) {
                pendingBuy = null;
                return true;
            }
            // Click outside panel also dismisses
            if (mx < overlayX || mx >= overlayX + overlayW || my < overlayY || my >= overlayY + overlayH) {
                pendingBuy = null;
            }
            return true; // consume all clicks while overlay is visible
        }
        return super.mouseClicked(event, focused);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // =========================================================================
    // Filtering
    // =========================================================================

    private void applyFilter() {
        if (searchBox == null) return;

        String query = searchBox.getValue().toLowerCase(Locale.ROOT).trim();
        if (query.equals(lastQuery)) return;
        lastQuery = query;

        if (query.isEmpty()) {
            filteredItems = new ArrayList<>(allItems);
        } else {
            filteredItems = allItems.stream()
                    .filter(item ->
                            item.itemStack().getHoverName().getString()
                                    .toLowerCase(Locale.ROOT).contains(query)
                            || item.ownerName().toLowerCase(Locale.ROOT).contains(query))
                    .collect(Collectors.toList());
        }

        if (resultList != null) {
            resultList.refresh(filteredItems);
        }
    }

    // =========================================================================
    // Inner: scrollable result list
    // =========================================================================

    private class ResultList extends ObjectSelectionList<ResultList.Entry> {

        ResultList(Minecraft mc, int width, int height, int y, int itemHeight) {
            super(mc, width, height, y, itemHeight);
        }

        void refresh(List<ClientAuctionItem> items) {
            this.clearEntries();
            for (ClientAuctionItem item : items) {
                this.addEntry(new Entry(item));
            }
        }

        @Override
        public int getRowWidth() {
            return this.width - 6;
        }

        @Override
        protected int scrollBarX() {
            return this.getX() + this.getWidth() - 6;
        }

        // ---------------------------------------------------------------------
        // Entry
        // ---------------------------------------------------------------------

        class Entry extends ObjectSelectionList.Entry<Entry> {

            private final ClientAuctionItem item;

            Entry(ClientAuctionItem item) {
                this.item = item;
            }

            @Override
            public Component getNarration() {
                return Component.literal(item.itemStack().getHoverName().getString()
                        + " by " + item.ownerName()
                        + " for $" + String.format("%.2f", item.price()));
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
                if (event.button() == 0) {
                    GUIAuctionHouseSearch.this.pendingBuy = this.item;
                    return true;
                }
                return false;
            }

            @Override
            public void renderContent(GuiGraphics g, int mouseX, int mouseY, boolean hovered, float delta) {
                // Entry's actual screen position — NOT the mouseX/mouseY passed in
                int x = this.getX();
                int y = this.getY();
                int w = this.getWidth();
                int h = this.getHeight();

                // Hover tint + tooltip
                if (hovered) {
                    g.fill(x, y, x + w, y + h, 0x28FFFFFF);
                    // setTooltipForNextFrame defers rendering to after all widgets —
                    // no manual z-ordering needed
                    g.setTooltipForNextFrame(
                            GUIAuctionHouseSearch.this.font,
                            item.itemStack(),
                            mouseX, mouseY);
                }

                // Thin separator line at top of every entry
                g.fill(x, y, x + w, y + 1, 0x30FFFFFF);

                // ── Item icon (16×16, vertically centred) ──────────────────
                int iconX = x + INNER_PAD;
                int iconY = y + (h - 16) / 2;
                g.renderItem(item.itemStack(), iconX, iconY);

                // ── Text columns ───────────────────────────────────────────
                var font   = GUIAuctionHouseSearch.this.font;
                int textLeft = iconX + 18;
                int topRow   = y + 4;
                int botRow   = y + 4 + font.lineHeight + 1;

                // Item name (white, truncated)
                String name = item.itemStack().getHoverName().getString();
                int maxNameW = w - 18 - 80 - INNER_PAD * 2;
                if (font.width(name) > maxNameW) {
                    name = font.plainSubstrByWidth(name, maxNameW - 6) + "…";
                }
                g.drawString(font, name, textLeft, topRow, 0xFFFFFFFF, false);

                // Seller name (gray)
                g.drawString(font, "by " + item.ownerName(), textLeft, botRow, 0xFF999999, false);

                // ── Right column: price + time ─────────────────────────────
                int rightEdge = x + w - INNER_PAD;

                String priceStr = String.format("$%.2f", item.price());
                int priceW = font.width(priceStr);
                g.drawString(font, priceStr, rightEdge - priceW, topRow, 0xFFFFAA00, false);

                String timeStr = item.timeLeft();
                int timeW = font.width(timeStr);
                g.drawString(font, timeStr, rightEdge - timeW, botRow, 0xFFAA55FF, false);
            }
        }
    }
}
