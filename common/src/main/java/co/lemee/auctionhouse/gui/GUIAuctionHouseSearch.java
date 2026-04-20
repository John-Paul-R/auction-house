package co.lemee.auctionhouse.gui;

import co.lemee.auctionhouse.AuctionHouseMod;
import co.lemee.auctionhouse.network.ClientAuctionItem;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
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
 *   │  │ [icon] Enchanted Book $p    │   │  ← expanded for enchanted items
 *   │  │        • Sharpness V        │   │
 *   │  │        • Unbreaking III     │   │
 *   │  │        by seller   time    ↕│   │
 *   │  └─────────────────────────────┘   │
 *   │  Showing X / Y listings            │  ← status
 *   └─────────────────────────────────────┘
 * </pre>
 * The server sends a full {@link co.lemee.auctionhouse.network.AuctionHouseListingsPayload}
 * snapshot when the screen opens, and pushes further snapshots whenever the auction house
 * state changes (item added, bought, or expired).  Filtering is applied locally on the client.
 * <p>
 * Entries are rendered by {@link VariableHeightList}, which supports variable row heights.
 * Enchanted books (and regular enchanted items) get an expanded row listing each
 * enchantment by name, while all other items use the compact two-line layout.
 * <p>
 * Interactive elements (Confirm / Cancel in the buy overlay) are managed as
 * {@link UIButton} instances registered with {@code addRenderableWidget()} in
 * {@link #init()}.  MC's widget routing handles hover detection and click dispatch
 * automatically — no manual bounding-box fields or custom iteration loops.
 */
public class GUIAuctionHouseSearch extends Screen {

    // --- Constants -----------------------------------------------------------

    private static final int PANEL_MARGIN = 10;
    private static final int TITLE_H      = 14;
    private static final int BOX_H        = 20;
    private static final int STATUS_H     = 12;
    /** Base entry height used for plain items and as the minimum for expanded entries. */
    static final int BASE_ENTRY_H         = 28;
    private static final int INNER_PAD    = 4;

    /** Width / height of the buy-confirmation overlay panel. */
    private static final int OVERLAY_W = 240;
    private static final int OVERLAY_H = 94;
    /** Button dimensions inside the overlay. */
    private static final int BTN_W = 90;
    private static final int BTN_H = 20;

    // --- State ---------------------------------------------------------------

    private List<ClientAuctionItem> allItems      = new ArrayList<>();
    private List<ClientAuctionItem> filteredItems = new ArrayList<>();
    /**
     * The last query string that was actually applied to {@link #allItems}.
     * {@code null} means no filter has been run yet.
     */
    private String  lastAppliedQuery = null;
    /**
     * Set to {@code true} by {@link #onListingsUpdate} to signal that the
     * underlying data changed and a re-filter is required even if the query
     * text hasn't changed.  Does NOT trigger a scroll reset.
     */
    private boolean dataStale        = false;

    // --- Widgets -------------------------------------------------------------

    private EditBox            searchBox;
    private VariableHeightList resultList;

    // --- Buy confirmation state ----------------------------------------------

    /** Non-null when the player has clicked a listing and is seeing the buy prompt. */
    @Nullable private ClientAuctionItem pendingBuy = null;

    /**
     * Overlay panel rect — set once in {@link #init()} and used by
     * {@link #mouseClicked} to detect click-outside-to-dismiss.
     * Only 4 values needed; button positions are owned by {@link UIButton}.
     */
    private int overlayX, overlayY;   // overlayW/H are constants above

    /** Confirm and Cancel buttons for the buy overlay. */
    private UIButton confirmButton;
    private UIButton cancelButton;

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
        this.dataStale = true; // re-filter needed; scroll position is preserved
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
        searchBox.setResponder(text -> applyFilter());
        this.addRenderableWidget(searchBox);
        this.setInitialFocus(searchBox);

        // -- Result list ------------------------------------------------------
        int listY = boxY + BOX_H + INNER_PAD;
        int listH = panelY + panelH - listY - STATUS_H - INNER_PAD;

        resultList = new VariableHeightList(this.font, panelX + 1, listY, panelW - 2, listH);
        this.addRenderableWidget(resultList);

        // -- Buy overlay buttons ----------------------------------------------
        // Compute overlay position from current screen dimensions (constant size).
        overlayX = (this.width  - OVERLAY_W) / 2;
        overlayY = (this.height - OVERLAY_H) / 2;

        int btnY        = overlayY + 58;
        int confirmBtnX = overlayX + 16;
        int cancelBtnX  = overlayX + OVERLAY_W - 16 - BTN_W;

        if (confirmButton == null) {
            // First init — create the button instances with their onClick closures.
            confirmButton = new UIButton(this.font, confirmBtnX, btnY, BTN_W, BTN_H,
                    "Confirm", UIButton.Style.GREEN, () -> {
                        if (pendingBuy != null) {
                            AuctionHouseMod.sendBuy.accept(pendingBuy.id());
                            this.onClose();
                        }
                    });
            cancelButton = new UIButton(this.font, cancelBtnX, btnY, BTN_W, BTN_H,
                    "Cancel", UIButton.Style.RED, () -> setPendingBuy(null));
        } else {
            // Subsequent init (resize) — reposition existing instances.
            confirmButton.repositionTo(confirmBtnX, btnY);
            cancelButton.repositionTo(cancelBtnX, btnY);
        }

        // Register for input routing only — rendering is done manually in render()
        // AFTER drawBuyOverlayChrome() so the buttons appear on top of the dim layer.
        this.addWidget(confirmButton);
        this.addWidget(cancelButton);

        // Sync button visibility with current pendingBuy state (handles resize
        // while the overlay is open).
        updateOverlayButtonVisibility();

        // Force applyFilter() to repopulate the freshly-created resultList widget.
        // Without this, a window resize would call init() → applyFilter() but the
        // filter would exit early ("nothing changed") and leave the list empty.
        dataStale = true;
        applyFilter();
    }

    /**
     * Set or clear the pending buy item, and sync overlay button visibility.
     */
    private void setPendingBuy(@Nullable ClientAuctionItem item) {
        this.pendingBuy = item;
        updateOverlayButtonVisibility();
    }

    /**
     * Show / hide and enable / disable the overlay buttons based on whether
     * there is a pending buy.  Called from {@link #init()} and whenever
     * {@link #pendingBuy} changes.
     */
    private void updateOverlayButtonVisibility() {
        if (confirmButton == null || cancelButton == null) return;
        if (pendingBuy != null) {
            confirmButton.show();
            cancelButton.show();
        } else {
            confirmButton.hide();
            cancelButton.hide();
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
        drawBorder(g, panelX, panelY, panelW, panelH, 0xFF444444);

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

        // Buy confirmation overlay chrome + buttons — rendered after all widgets
        // so they appear on top of the dim layer.
        if (pendingBuy != null) {
            drawBuyOverlayChrome(g);
            // Render buttons manually here so they appear above the dim overlay.
            confirmButton.render(g, mouseX, mouseY, delta);
            cancelButton.render(g, mouseX, mouseY, delta);
        }
    }

    /**
     * Draws the buy-confirmation modal panel chrome: dim, background, border,
     * item icon/name, and price text.  The Confirm/Cancel buttons are
     * {@link UIButton} instances rendered automatically by the widget system.
     */
    private void drawBuyOverlayChrome(GuiGraphics g) {
        // Dim the rest of the screen
        g.fill(0, 0, this.width, this.height, 0x80000000);

        // Panel background + border
        g.fill(overlayX, overlayY, overlayX + OVERLAY_W, overlayY + OVERLAY_H, 0xE0101010);
        drawBorder(g, overlayX, overlayY, OVERLAY_W, OVERLAY_H, 0xFF555555);

        // Item icon + name
        int iconX = overlayX + 8;
        int iconY = overlayY + 10;
        g.renderItem(pendingBuy.itemStack(), iconX, iconY);
        String name = pendingBuy.itemStack().getHoverName().getString();
        int maxNameW = OVERLAY_W - 36;
        if (this.font.width(name) > maxNameW) {
            name = this.font.plainSubstrByWidth(name, maxNameW - 6) + "\u2026";
        }
        g.drawString(this.font, name, iconX + 20, iconY + 4, 0xFFFFFFFF, false);

        // Price line
        String priceLine = String.format("Buy for $%.2f?", pendingBuy.price());
        int priceW = this.font.width(priceLine);
        g.drawString(this.font, priceLine,
                overlayX + (OVERLAY_W - priceW) / 2, overlayY + 36, 0xFFFFAA00, false);
    }

    /**
     * Draws a 1-pixel border rectangle using four {@code g.fill} calls.
     *
     * @param g   graphics context
     * @param x   left edge
     * @param y   top edge
     * @param w   total width (border included)
     * @param h   total height (border included)
     * @param col ARGB border colour
     */
    private static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int col) {
        g.fill(x,         y,         x + w, y + 1,     col); // top
        g.fill(x,         y + h - 1, x + w, y + h,     col); // bottom
        g.fill(x,         y,         x + 1, y + h,     col); // left
        g.fill(x + w - 1, y,         x + w, y + h,     col); // right
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        // While the overlay is visible, UIButton widgets handle Confirm and Cancel
        // automatically.  We only need to dismiss when the player clicks OUTSIDE
        // the overlay panel.
        if (pendingBuy != null && event.button() == 0) {
            double mx = event.x();
            double my = event.y();
            boolean insideOverlay = mx >= overlayX && mx < overlayX + OVERLAY_W
                                 && my >= overlayY && my < overlayY + OVERLAY_H;
            if (!insideOverlay) {
                setPendingBuy(null);
                return true;
            }
            // Dispatch to buttons directly so they get priority over the result
            // list (which is earlier in the children list and would otherwise
            // consume the event first).  UIButton.mouseClicked self-filters via
            // isMouseOver, so only the button actually under the cursor fires.
            confirmButton.mouseClicked(event, false);
            cancelButton.mouseClicked(event, false);
            // Consume regardless — clicks inside the overlay must never reach
            // the item entries behind it.
            return true;
        }
        return super.mouseClicked(event, focused);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // ESC while overlay is open → close overlay only, not the whole screen.
        if (event.key() == 256 /* GLFW_KEY_ESCAPE */ && pendingBuy != null) {
            setPendingBuy(null);
            return true;
        }
        return super.keyPressed(event);
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
        boolean queryChanged = !query.equals(lastAppliedQuery);

        // Nothing to do if neither the query nor the underlying data changed.
        if (!queryChanged && !dataStale) return;

        lastAppliedQuery = query;
        dataStale        = false;

        if (query.isEmpty()) {
            filteredItems = new ArrayList<>(allItems);
        } else {
            filteredItems = allItems.stream()
                    .filter(item ->
                            item.itemStack().getHoverName().getString()
                                    .toLowerCase(Locale.ROOT).contains(query)
                            || item.ownerName().toLowerCase(Locale.ROOT).contains(query)
                            || enchantmentNamesContain(item, query))
                    .collect(Collectors.toList());
        }

        if (resultList != null) {
            resultList.setEntries(buildEntries(filteredItems));
            // Only reset scroll when the user changes their query, not on a
            // server-pushed data refresh with the same query.
            if (queryChanged) resultList.resetScroll();
        }
    }

    /**
     * Returns {@code true} if any stored or applied enchantment name on the
     * item's stack contains {@code query} (already lower-cased).
     */
    private static boolean enchantmentNamesContain(ClientAuctionItem item, String query) {
        ItemEnchantments stored  = item.itemStack().get(DataComponents.STORED_ENCHANTMENTS);
        ItemEnchantments applied = item.itemStack().get(DataComponents.ENCHANTMENTS);
        for (ItemEnchantments ie : new ItemEnchantments[]{ stored, applied }) {
            if (ie == null) continue;
            for (var entry : ie.entrySet()) {
                String name = Enchantment.getFullname(entry.getKey(), entry.getIntValue())
                                         .getString().toLowerCase(Locale.ROOT);
                if (name.contains(query)) return true;
            }
        }
        return false;
    }

    /** Converts a list of auction items into typed {@link VariableHeightList.Entry} objects. */
    private List<VariableHeightList.Entry> buildEntries(List<ClientAuctionItem> items) {
        List<VariableHeightList.Entry> entries = new ArrayList<>(items.size());
        for (ClientAuctionItem item : items) {
            ItemEnchantments enchants = resolveEnchantments(item);
            if (enchants != null && !enchants.isEmpty()) {
                entries.add(new EnchantedItemEntry(item, enchants));
            } else {
                entries.add(new DefaultItemEntry(item));
            }
        }
        return entries;
    }

    /**
     * Returns the relevant {@link ItemEnchantments} for an item, preferring
     * {@code STORED_ENCHANTMENTS} (enchanted books) over {@code ENCHANTMENTS}
     * (tools/armour). Returns {@code null} if the item has no enchantments.
     */
    @Nullable
    private static ItemEnchantments resolveEnchantments(ClientAuctionItem item) {
        if (item.itemStack().is(Items.ENCHANTED_BOOK)) {
            return item.itemStack().get(DataComponents.STORED_ENCHANTMENTS);
        }
        ItemEnchantments applied = item.itemStack().get(DataComponents.ENCHANTMENTS);
        return (applied != null && !applied.isEmpty()) ? applied : null;
    }

    // =========================================================================
    // Entry: compact two-line row for plain items
    // =========================================================================

    private class DefaultItemEntry extends VariableHeightList.Entry {

        private final ClientAuctionItem item;

        DefaultItemEntry(ClientAuctionItem item) {
            this.item = item;
        }

        @Override
        public int getHeight(Font font) {
            return BASE_ENTRY_H;
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
                GUIAuctionHouseSearch.this.setPendingBuy(this.item);
                return true;
            }
            return false;
        }

        @Override
        public void render(GuiGraphics g, int x, int y, int width,
                           int mouseX, int mouseY, boolean hovered, Font font) {
            int h = getHeight(font);

            if (hovered) {
                g.fill(x, y, x + width, y + h, 0x28FFFFFF);
                // Suppress item tooltip while the buy overlay is open.
                if (GUIAuctionHouseSearch.this.pendingBuy == null) {
                    g.setTooltipForNextFrame(font, item.itemStack(), mouseX, mouseY);
                }
            }

            // Top separator
            g.fill(x, y, x + width, y + 1, 0x30FFFFFF);

            // Icon — centred vertically
            int iconX = x + INNER_PAD;
            int iconY = y + (h - 16) / 2;
            g.renderItem(item.itemStack(), iconX, iconY);

            int textLeft  = iconX + 18;
            int rightEdge = x + width - INNER_PAD;
            int topRow    = y + 4;
            int botRow    = y + 4 + font.lineHeight + 1;

            // Item name (white, truncated)
            String name = item.itemStack().getHoverName().getString();
            int maxNameW = width - 18 - 80 - INNER_PAD * 2;
            if (font.width(name) > maxNameW) {
                name = font.plainSubstrByWidth(name, maxNameW - 6) + "…";
            }
            g.drawString(font, name, textLeft, topRow, 0xFFFFFFFF, false);

            // Seller name (gray)
            g.drawString(font, "by " + item.ownerName(), textLeft, botRow, 0xFF999999, false);

            // Price (gold, right-aligned to top row)
            String priceStr = String.format("$%.2f", item.price());
            g.drawString(font, priceStr, rightEdge - font.width(priceStr), topRow, 0xFFFFAA00, false);

            // Time left (purple, right-aligned to bottom row)
            String timeStr = item.timeLeft();
            g.drawString(font, timeStr, rightEdge - font.width(timeStr), botRow, 0xFFAA55FF, false);
        }
    }

    // =========================================================================
    // Entry: expanded row for enchanted books and enchanted equipment
    // =========================================================================

    /**
     * Renders an expanded row that lists every enchantment by name below the
     * item name.  The row height grows dynamically with the number of enchants.
     *
     * <pre>
     * [icon] Enchanted Book                 $12.50
     *        • Sharpness V
     *        • Unbreaking III
     *        • Mending
     *        by seller                      2d:04h
     * </pre>
     */
    private class EnchantedItemEntry extends VariableHeightList.Entry {

        private static final int ENCHANT_COLOR = 0xFFAB78FF; // soft purple
        /** Vertical padding: top + between sections + bottom. */
        private static final int PAD_V = 4;
        /** Extra left indent for enchantment bullet lines. */
        private static final int ENCHANT_INDENT = 8;

        private final ClientAuctionItem item;
        /** Snapshot of enchantments at entry-creation time — avoids repeated component lookups. */
        private final List<Object2IntMap.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>>> enchantList;

        EnchantedItemEntry(ClientAuctionItem item, ItemEnchantments enchants) {
            this.item = item;
            this.enchantList = new ArrayList<>(enchants.entrySet());
        }

        @Override
        public int getHeight(Font font) {
            // name row + enchant rows + seller row, each separated by 2 px gaps
            int rows = 1 + enchantList.size() + 1; // name, enchants..., seller
            return PAD_V + rows * font.lineHeight + (rows - 1) * 2 + PAD_V;
        }

        @Override
        public Component getNarration() {
            StringBuilder sb = new StringBuilder();
            sb.append(item.itemStack().getHoverName().getString());
            for (var e : enchantList) {
                sb.append(", ").append(
                        Enchantment.getFullname(e.getKey(), e.getIntValue()).getString());
            }
            sb.append(" by ").append(item.ownerName());
            sb.append(String.format(" for $%.2f", item.price()));
            return Component.literal(sb.toString());
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
            if (event.button() == 0) {
                GUIAuctionHouseSearch.this.setPendingBuy(this.item);
                return true;
            }
            return false;
        }

        @Override
        public void render(GuiGraphics g, int x, int y, int width,
                           int mouseX, int mouseY, boolean hovered, Font font) {
            int h         = getHeight(font);
            int lineH     = font.lineHeight;
            int lineStep  = lineH + 2;
            int textLeft  = x + INNER_PAD + 18;
            int rightEdge = x + width - INNER_PAD;

            if (hovered) {
                g.fill(x, y, x + width, y + h, 0x28FFFFFF);
                // Suppress item tooltip while the buy overlay is open.
                if (GUIAuctionHouseSearch.this.pendingBuy == null) {
                    g.setTooltipForNextFrame(font, item.itemStack(), mouseX, mouseY);
                }
            }

            // Top separator
            g.fill(x, y, x + width, y + 1, 0x30FFFFFF);

            // Icon — centred vertically in the full entry height
            int iconX = x + INNER_PAD;
            int iconY = y + (h - 16) / 2;
            g.renderItem(item.itemStack(), iconX, iconY);

            // ── Row 1: item name + price ──────────────────────────────────────
            int rowY = y + PAD_V;

            String name = item.itemStack().getHoverName().getString();
            // Reserve space for the price on the right
            String priceStr = String.format("$%.2f", item.price());
            int priceW  = font.width(priceStr);
            int maxNameW = width - 18 - priceW - INNER_PAD * 3;
            if (font.width(name) > maxNameW) {
                name = font.plainSubstrByWidth(name, maxNameW - 6) + "…";
            }
            g.drawString(font, name, textLeft, rowY, 0xFFFFFFFF, false);
            g.drawString(font, priceStr, rightEdge - priceW, rowY, 0xFFFFAA00, false);
            rowY += lineStep;

            // ── Rows 2..N+1: enchantment list ────────────────────────────────
            for (var entry : enchantList) {
                Component fullName = Enchantment.getFullname(entry.getKey(), entry.getIntValue());
                String enchStr = "\u2022 " + fullName.getString(); // bullet
                g.drawString(font, enchStr, textLeft + ENCHANT_INDENT, rowY, ENCHANT_COLOR, false);
                rowY += lineStep;
            }

            // ── Last row: seller + time ───────────────────────────────────────
            g.drawString(font, "by " + item.ownerName(), textLeft, rowY, 0xFF999999, false);
            String timeStr = item.timeLeft();
            g.drawString(font, timeStr, rightEdge - font.width(timeStr), rowY, 0xFFAA55FF, false);
        }
    }
}
