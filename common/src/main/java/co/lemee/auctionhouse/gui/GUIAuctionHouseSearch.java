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
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
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
 *
 * <p>Layout (list view):
 * <pre>
 *   ┌──────────────────────────────────────┐
 *   │        Auction House Search          │
 *   │  [ Search items...                ] │
 *   │  ┌────────────────────────────────┐  │
 *   │  │ [icon] Name             $price │  │  ← DefaultItemEntry
 *   │  │        by seller        time   │  │
 *   │  │ [icon] Enchanted Book   $price │  │  ← EnchantedItemEntry
 *   │  │        • Sharpness V           │  │
 *   │  │        by seller        time  ↕│  │
 *   │  └────────────────────────────────┘  │
 *   │  Showing X / Y listings              │
 *   └──────────────────────────────────────┘
 * </pre>
 *
 * <p>Buy-confirmation overlay layout:
 * <pre>
 *   ┌──────────────────────────────────┐
 *   │  [icon] Name                     │  ← item info
 *   │         • Sharpness V            │
 *   │         • Mending                │
 *   │  ──────────────────────────────  │  ← divider
 *   │          Buy for $12.50          │  ← price (centered)
 *   │  by seller              2d:04h   │  ← sale meta
 *   │  [ Confirm ]       [ Cancel ]    │  ← buttons
 *   └──────────────────────────────────┘
 * </pre>
 *
 * <p>Shared rendering helpers ({@link #renderName}, {@link #renderEnchants},
 * {@link #renderInlinePrice}, {@link #renderSeller}, {@link #renderTimeRight})
 * are used by both list entries and the overlay so layout rules stay consistent.
 */
public class GUIAuctionHouseSearch extends Screen {

    // ── Layout constants ──────────────────────────────────────────────────────

    private static final int PANEL_MARGIN = 10;
    private static final int TITLE_H      = 14;
    private static final int BOX_H        = 20;
    private static final int STATUS_H     = 12;
    /** Base entry height for plain (non-enchanted) list rows. */
    static final int BASE_ENTRY_H         = 28;
    private static final int INNER_PAD    = 4;

    /** Fixed overlay width; height is computed dynamically from content. */
    private static final int OVERLAY_W   = 240;
    /** Inner padding inside the overlay panel. */
    private static final int OVERLAY_PAD = 10;
    /** Vertical gap between overlay sections. */
    private static final int SECTION_GAP = 6;
    private static final int BTN_W       = 90;
    private static final int BTN_H       = 20;

    // ── Shared colour palette ─────────────────────────────────────────────────
    // Used by both list entries and the overlay chrome.

    static final int C_NAME      = 0xFFFFFFFF;
    static final int C_PRICE     = 0xFFFFAA00;
    static final int C_SELLER    = 0xFF999999;
    static final int C_TIME      = 0xFFAA55FF;
    static final int C_ENCHANT   = 0xFFAB78FF;
    static final int C_SEPARATOR = 0x30FFFFFF;
    static final int C_HOVER_BG  = 0x28FFFFFF;

    // ── State ─────────────────────────────────────────────────────────────────

    private List<ClientAuctionItem> allItems      = new ArrayList<>();
    private List<ClientAuctionItem> filteredItems = new ArrayList<>();
    /**
     * The last query string actually applied to {@link #allItems}.
     * {@code null} means no filter has run yet.
     */
    private String  lastAppliedQuery = null;
    /**
     * Set by {@link #onListingsUpdate} when data changes without a query change.
     * Triggers a re-filter that preserves scroll position.
     */
    private boolean dataStale        = false;

    // ── Widgets ───────────────────────────────────────────────────────────────

    private EditBox            searchBox;
    private VariableHeightList resultList;

    // ── Buy overlay state ─────────────────────────────────────────────────────

    /** Non-null while the buy-confirmation overlay is visible. */
    @Nullable private ClientAuctionItem pendingBuy = null;

    /**
     * Overlay panel geometry — updated by {@link #recomputeOverlayGeometry()}
     * whenever {@link #pendingBuy} changes or the window is resized.
     * {@code overlayH} is dynamic: it grows with the enchantment count.
     */
    private int overlayX, overlayY, overlayH;

    private UIButton confirmButton;
    private UIButton cancelButton;

    // =========================================================================
    // Construction
    // =========================================================================

    public GUIAuctionHouseSearch(Component title) {
        super(title);
    }

    // =========================================================================
    // Network callback (called from platform packet handler on main thread)
    // =========================================================================

    /**
     * Delivers a fresh listing snapshot to the currently-open search screen.
     */
    public static void updateListings(List<ClientAuctionItem> newListings) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GUIAuctionHouseSearch screen) {
            screen.onListingsUpdate(newListings);
        }
    }

    private void onListingsUpdate(List<ClientAuctionItem> newListings) {
        this.allItems  = new ArrayList<>(newListings);
        this.dataStale = true;
        applyFilter();
    }

    // =========================================================================
    // Screen lifecycle
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

        // -- Overlay geometry + buttons ---------------------------------------
        // recomputeOverlayGeometry handles both first-init and resize cases.
        // On first init confirmButton is null so buttons are created afterwards;
        // on resize the existing instances are repositioned by the method.
        recomputeOverlayGeometry();

        if (confirmButton == null) {
            int btnY = overlayY + overlayH - BTN_H - OVERLAY_PAD;
            confirmButton = new UIButton(this.font,
                    overlayX + OVERLAY_PAD, btnY, BTN_W, BTN_H,
                    "Confirm", UIButton.Style.GREEN, () -> {
                        if (pendingBuy != null) {
                            AuctionHouseMod.sendBuy.accept(pendingBuy.id());
                            this.onClose();
                        }
                    });
            cancelButton = new UIButton(this.font,
                    overlayX + OVERLAY_W - OVERLAY_PAD - BTN_W, btnY, BTN_W, BTN_H,
                    "Cancel", UIButton.Style.RED, () -> setPendingBuy(null));
        }
        // Buttons registered for input routing only — rendered manually after
        // the overlay chrome so they appear on top of the dim layer.
        this.addWidget(confirmButton);
        this.addWidget(cancelButton);
        updateOverlayButtonVisibility();

        // Ensure the freshly-created resultList widget is populated even if
        // the query hasn't changed (covers the window-resize case).
        dataStale = true;
        applyFilter();
    }

    // ── Overlay geometry management ───────────────────────────────────────────

    private void setPendingBuy(@Nullable ClientAuctionItem item) {
        this.pendingBuy = item;
        recomputeOverlayGeometry();
        updateOverlayButtonVisibility();
    }

    private void updateOverlayButtonVisibility() {
        if (confirmButton == null || cancelButton == null) return;
        if (pendingBuy != null) { confirmButton.show(); cancelButton.show(); }
        else                    { confirmButton.hide(); cancelButton.hide(); }
    }

    /**
     * Recomputes {@link #overlayX}, {@link #overlayY}, {@link #overlayH} and
     * repositions the confirm/cancel buttons.  Must be called whenever
     * {@link #pendingBuy} changes or the window is resized ({@link #init}).
     */
    private void recomputeOverlayGeometry() {
        overlayH = computeOverlayH();
        overlayX = (this.width  - OVERLAY_W) / 2;
        overlayY = (this.height - overlayH)  / 2;
        if (confirmButton != null) {
            int btnY = overlayY + overlayH - BTN_H - OVERLAY_PAD;
            confirmButton.repositionTo(overlayX + OVERLAY_PAD, btnY);
            cancelButton.repositionTo(overlayX + OVERLAY_W - OVERLAY_PAD - BTN_W, btnY);
        }
    }

    /**
     * Returns the dynamic overlay height for the current {@link #pendingBuy}.
     * Grows by one line per enchantment on the item.
     */
    private int computeOverlayH() {
        int enchantCount = 0;
        if (pendingBuy != null) {
            ItemEnchantments enc = resolveEnchantments(pendingBuy);
            if (enc != null) enchantCount = enc.entrySet().size();
        }
        int lineH      = this.font.lineHeight;
        int lineStep   = lineH + 2;
        int itemBlockH = Math.max(16, (1 + enchantCount) * lineStep);
        //         top-pad  item-block  gap  divider  gap  price  gap  meta   gap  btn  bot-pad
        return OVERLAY_PAD + itemBlockH + SECTION_GAP + 1 + SECTION_GAP
                + lineH + SECTION_GAP + lineH + SECTION_GAP + BTN_H + OVERLAY_PAD;
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderBackground(g, mouseX, mouseY, delta);

        int panelW = Math.min(480, this.width  - PANEL_MARGIN * 2);
        int panelH = this.height - PANEL_MARGIN * 2;
        int panelX = (this.width  - panelW) / 2;
        int panelY = PANEL_MARGIN;

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xC0101010);
        drawBorder(g, panelX, panelY, panelW, panelH, 0xFF444444);
        g.drawCenteredString(this.font, this.title, this.width / 2, panelY + 3, 0xFFFFFFFF);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);

        // Status line
        int panelW = Math.min(480, this.width  - PANEL_MARGIN * 2);
        int panelH = this.height - PANEL_MARGIN * 2;
        int panelX = (this.width  - panelW) / 2;
        int panelY = PANEL_MARGIN;
        g.drawString(this.font,
                filteredItems.size() + " / " + allItems.size() + " listings",
                panelX + INNER_PAD, panelY + panelH - STATUS_H, 0xFF888888, false);

        // Overlay — rendered last so it sits above everything
        if (pendingBuy != null) {
            drawBuyOverlayChrome(g, mouseX, mouseY);
            confirmButton.render(g, mouseX, mouseY, delta);
            cancelButton.render(g, mouseX, mouseY, delta);
        }
    }

    /**
     * Renders the buy-confirmation overlay: dim, panel chrome, item info
     * (icon + name + enchants), a divider, the price centered prominently,
     * and sale metadata (seller + time left).
     *
     * <p>Confirm/Cancel buttons are rendered separately by {@link #render}
     * after this call so they appear above the panel background.
     *
     * <p>Layout order mirrors the priority described in the class Javadoc:
     * <ol>
     *   <li>Item identity (icon, name, enchantments)</li>
     *   <li>Price</li>
     *   <li>Sale metadata (seller, time left)</li>
     * </ol>
     */
    private void drawBuyOverlayChrome(GuiGraphics g, int mouseX, int mouseY) {
        // Full-screen dim
        g.fill(0, 0, this.width, this.height, 0x80000000);

        // Panel background + border
        g.fill(overlayX, overlayY, overlayX + OVERLAY_W, overlayY + overlayH, 0xE0101010);
        drawBorder(g, overlayX, overlayY, OVERLAY_W, overlayH, 0xFF555555);

        int innerX    = overlayX + OVERLAY_PAD;
        int innerW    = OVERLAY_W - OVERLAY_PAD * 2;
        int rightEdge = overlayX + OVERLAY_W - OVERLAY_PAD;
        int curY      = overlayY + OVERLAY_PAD;

        // ── Section 1: item identity ──────────────────────────────────────────
        ItemEnchantments enc = resolveEnchantments(pendingBuy);
        List<Object2IntMap.Entry<Holder<Enchantment>>> enchants =
                enc != null ? new ArrayList<>(enc.entrySet()) : List.of();

        int lineStep     = this.font.lineHeight + 2;
        int enchantCount = enchants.size();
        int itemBlockH   = Math.max(16, (1 + enchantCount) * lineStep);

        // Icon — vertically centred within the item block
        int iconX = innerX;
        int iconY = curY + (itemBlockH - 16) / 2;
        g.renderItem(pendingBuy.itemStack(), iconX, iconY);
        // Tooltip on icon hover
        if (mouseX >= iconX && mouseX < iconX + 16 && mouseY >= iconY && mouseY < iconY + 16) {
            g.setTooltipForNextFrame(this.font, pendingBuy.itemStack(), mouseX, mouseY);
        }

        int textX      = innerX + 20;
        int textW      = innerW - 20;
        // Centre the text rows vertically within the item block (handles case
        // where icon is taller than a single name line with no enchants).
        int textBlockH = (1 + enchantCount) * this.font.lineHeight + enchantCount * 2;
        int rowY       = curY + Math.max(0, (itemBlockH - textBlockH) / 2);

        renderName(g, this.font, pendingBuy.itemStack(), textX, rowY, textW);
        rowY += lineStep;
        rowY = renderEnchants(g, this.font, enchants, textX + 6, rowY);

        curY += itemBlockH + SECTION_GAP;

        // ── Divider ───────────────────────────────────────────────────────────
        g.fill(innerX, curY, innerX + innerW, curY + 1, C_SEPARATOR);
        curY += 1 + SECTION_GAP;

        // ── Section 2: price (centered, prominent) ────────────────────────────
        String priceStr = String.format("Buy for $%.2f", pendingBuy.price());
        int    priceX   = overlayX + (OVERLAY_W - this.font.width(priceStr)) / 2;
        g.drawString(this.font, priceStr, priceX, curY, C_PRICE, false);
        curY += this.font.lineHeight + SECTION_GAP;

        // ── Section 3: sale metadata ──────────────────────────────────────────
        renderSeller(g, this.font, pendingBuy.ownerName(), innerX, curY);
        renderTimeRight(g, this.font, pendingBuy.timeLeft(), rightEdge, curY);
    }

    /** Draws a 1-pixel border around the rectangle {@code (x, y, x+w, y+h)}. */
    private static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int col) {
        g.fill(x,         y,         x + w, y + 1,     col);
        g.fill(x,         y + h - 1, x + w, y + h,     col);
        g.fill(x,         y,         x + 1, y + h,     col);
        g.fill(x + w - 1, y,         x + w, y + h,     col);
    }

    // =========================================================================
    // Input
    // =========================================================================

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        if (pendingBuy != null && event.button() == 0) {
            double mx = event.x(), my = event.y();
            boolean inside = mx >= overlayX && mx < overlayX + OVERLAY_W
                          && my >= overlayY && my < overlayY + overlayH;
            if (!inside) {
                setPendingBuy(null);
                return true;
            }
            // Dispatch directly to buttons so they take priority over the result
            // list (which is earlier in the children list).  UIButton self-filters
            // via isMouseOver, so only the button under the cursor fires.
            confirmButton.mouseClicked(event, false);
            cancelButton.mouseClicked(event, false);
            return true; // consume — never let the list see overlay clicks
        }
        return super.mouseClicked(event, focused);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // ESC with overlay open → dismiss overlay only, not the whole screen.
        if (event.key() == 256 /* GLFW_KEY_ESCAPE */ && pendingBuy != null) {
            setPendingBuy(null);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // =========================================================================
    // Filtering
    // =========================================================================

    private void applyFilter() {
        if (searchBox == null) return;

        String  query        = searchBox.getValue().toLowerCase(Locale.ROOT).trim();
        boolean queryChanged = !query.equals(lastAppliedQuery);
        if (!queryChanged && !dataStale) return;

        lastAppliedQuery = query;
        dataStale        = false;

        filteredItems = query.isEmpty() ? new ArrayList<>(allItems)
                : allItems.stream()
                          .filter(item ->
                                  item.itemStack().getHoverName().getString()
                                          .toLowerCase(Locale.ROOT).contains(query)
                                  || item.ownerName().toLowerCase(Locale.ROOT).contains(query)
                                  || enchantmentNamesContain(item, query))
                          .collect(Collectors.toList());

        if (resultList != null) {
            resultList.setEntries(buildEntries(filteredItems));
            if (queryChanged) resultList.resetScroll();
        }
    }

    private static boolean enchantmentNamesContain(ClientAuctionItem item, String query) {
        for (ItemEnchantments ie : new ItemEnchantments[]{
                item.itemStack().get(DataComponents.STORED_ENCHANTMENTS),
                item.itemStack().get(DataComponents.ENCHANTMENTS) }) {
            if (ie == null) continue;
            for (var entry : ie.entrySet()) {
                String name = Enchantment.getFullname(entry.getKey(), entry.getIntValue())
                                         .getString().toLowerCase(Locale.ROOT);
                if (name.contains(query)) return true;
            }
        }
        return false;
    }

    private List<VariableHeightList.Entry> buildEntries(List<ClientAuctionItem> items) {
        List<VariableHeightList.Entry> entries = new ArrayList<>(items.size());
        for (ClientAuctionItem item : items) {
            ItemEnchantments enchants = resolveEnchantments(item);
            entries.add((enchants != null && !enchants.isEmpty())
                    ? new EnchantedItemEntry(item, enchants)
                    : new DefaultItemEntry(item));
        }
        return entries;
    }

    /**
     * Returns the relevant {@link ItemEnchantments} for an item:
     * {@code STORED_ENCHANTMENTS} for enchanted books,
     * {@code ENCHANTMENTS} for tools/armour.
     * Returns {@code null} if the item has no enchantments.
     */
    @Nullable
    private static ItemEnchantments resolveEnchantments(ClientAuctionItem item) {
        if (item.itemStack().is(Items.ENCHANTED_BOOK))
            return item.itemStack().get(DataComponents.STORED_ENCHANTMENTS);
        ItemEnchantments applied = item.itemStack().get(DataComponents.ENCHANTMENTS);
        return (applied != null && !applied.isEmpty()) ? applied : null;
    }

    // =========================================================================
    // Shared rendering helpers
    // Used by DefaultItemEntry, EnchantedItemEntry, and drawBuyOverlayChrome.
    // =========================================================================

    /**
     * Renders the item display name, truncated with an ellipsis if wider than
     * {@code maxWidth}, in {@link #C_NAME} white.
     */
    private static void renderName(GuiGraphics g, Font font,
            ItemStack stack, int x, int y, int maxWidth) {
        String name = stack.getHoverName().getString();
        if (font.width(name) > maxWidth)
            name = font.plainSubstrByWidth(name, maxWidth - 6) + "…";
        g.drawString(font, name, x, y, C_NAME, false);
    }

    /**
     * Renders each enchantment as a bullet line in {@link #C_ENCHANT} soft purple.
     *
     * @return Y coordinate immediately below the last drawn line
     *         (equals {@code startY} when the list is empty)
     */
    private static int renderEnchants(GuiGraphics g, Font font,
            List<Object2IntMap.Entry<Holder<Enchantment>>> enchants,
            int x, int startY) {
        int lineStep = font.lineHeight + 2;
        int y = startY;
        for (var e : enchants) {
            g.drawString(font,
                    "\u2022 " + Enchantment.getFullname(e.getKey(), e.getIntValue()).getString(),
                    x, y, C_ENCHANT, false);
            y += lineStep;
        }
        return y;
    }

    /**
     * Renders {@code "$X.XX"} at {@code (x, y)} in {@link #C_PRICE} gold.
     * Used inline in list entries where the price sits on the same row as the name.
     */
    private static void renderInlinePrice(GuiGraphics g, Font font, double price, int x, int y) {
        g.drawString(font, String.format("$%.2f", price), x, y, C_PRICE, false);
    }

    /**
     * Renders {@code "by <ownerName>"} at {@code (x, y)} in {@link #C_SELLER} gray.
     */
    private static void renderSeller(GuiGraphics g, Font font, String ownerName, int x, int y) {
        g.drawString(font, "by " + ownerName, x, y, C_SELLER, false);
    }

    /**
     * Renders the time string right-aligned to {@code rightEdge} in {@link #C_TIME} soft purple.
     */
    private static void renderTimeRight(GuiGraphics g, Font font, String time, int rightEdge, int y) {
        g.drawString(font, time, rightEdge - font.width(time), y, C_TIME, false);
    }

    // =========================================================================
    // Entry: compact two-line row for plain items
    // =========================================================================

    private class DefaultItemEntry extends VariableHeightList.Entry {

        private final ClientAuctionItem item;

        DefaultItemEntry(ClientAuctionItem item) { this.item = item; }

        @Override public int getHeight(Font font) { return BASE_ENTRY_H; }

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

            if (hovered && GUIAuctionHouseSearch.this.pendingBuy == null) {
                g.fill(x, y, x + width, y + h, C_HOVER_BG);
                g.setTooltipForNextFrame(font, item.itemStack(), mouseX, mouseY);
            }
            g.fill(x, y, x + width, y + 1, C_SEPARATOR);

            int iconX     = x + INNER_PAD;
            int iconY     = y + (h - 16) / 2;
            int textLeft  = iconX + 18;
            int rightEdge = x + width - INNER_PAD;
            int topRow    = y + 4;
            int botRow    = topRow + font.lineHeight + 1;

            g.renderItem(item.itemStack(), iconX, iconY);

            // Name + price on top row; seller + time on bottom row
            String priceStr = String.format("$%.2f", item.price());
            int    priceW   = font.width(priceStr);
            renderName(g, font, item.itemStack(), textLeft, topRow,
                    width - 18 - priceW - INNER_PAD * 3);
            renderInlinePrice(g, font, item.price(), rightEdge - priceW, topRow);
            renderSeller(g, font, item.ownerName(), textLeft, botRow);
            renderTimeRight(g, font, item.timeLeft(), rightEdge, botRow);
        }
    }

    // =========================================================================
    // Entry: expanded row for enchanted books and enchanted equipment
    // =========================================================================

    /**
     * Expanded list row that shows every enchantment by name below the item name.
     * Row height grows dynamically with the number of enchantments.
     *
     * <pre>
     * [icon] Enchanted Book          $12.50
     *        • Sharpness V
     *        • Unbreaking III
     *        • Mending
     *        by seller               2d:04h
     * </pre>
     */
    private class EnchantedItemEntry extends VariableHeightList.Entry {

        private static final int PAD_V          = 4;
        private static final int ENCHANT_INDENT = 8;

        private final ClientAuctionItem item;
        /** Cached at entry-creation time to avoid repeated component lookups. */
        private final List<Object2IntMap.Entry<Holder<Enchantment>>> enchantList;

        EnchantedItemEntry(ClientAuctionItem item, ItemEnchantments enchants) {
            this.item        = item;
            this.enchantList = new ArrayList<>(enchants.entrySet());
        }

        @Override
        public int getHeight(Font font) {
            // name + enchants + seller, with 2 px line gaps
            int rows = 1 + enchantList.size() + 1;
            return PAD_V + rows * font.lineHeight + (rows - 1) * 2 + PAD_V;
        }

        @Override
        public Component getNarration() {
            StringBuilder sb = new StringBuilder(item.itemStack().getHoverName().getString());
            for (var e : enchantList)
                sb.append(", ").append(
                        Enchantment.getFullname(e.getKey(), e.getIntValue()).getString());
            sb.append(" by ").append(item.ownerName())
              .append(String.format(" for $%.2f", item.price()));
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
            int lineStep  = font.lineHeight + 2;
            int textLeft  = x + INNER_PAD + 18;
            int rightEdge = x + width - INNER_PAD;

            if (hovered && GUIAuctionHouseSearch.this.pendingBuy == null) {
                g.fill(x, y, x + width, y + h, C_HOVER_BG);
                g.setTooltipForNextFrame(font, item.itemStack(), mouseX, mouseY);
            }
            g.fill(x, y, x + width, y + 1, C_SEPARATOR);

            // Icon — vertically centred across the full row height
            g.renderItem(item.itemStack(), x + INNER_PAD, y + (h - 16) / 2);

            // ── Name + inline price ───────────────────────────────────────────
            int rowY     = y + PAD_V;
            String priceStr = String.format("$%.2f", item.price());
            int    priceW   = font.width(priceStr);
            renderName(g, font, item.itemStack(), textLeft, rowY,
                    width - 18 - priceW - INNER_PAD * 3);
            renderInlinePrice(g, font, item.price(), rightEdge - priceW, rowY);
            rowY += lineStep;

            // ── Enchantment list ──────────────────────────────────────────────
            rowY = renderEnchants(g, font, enchantList, textLeft + ENCHANT_INDENT, rowY);

            // ── Seller + time ─────────────────────────────────────────────────
            renderSeller(g, font, item.ownerName(), textLeft, rowY);
            renderTimeRight(g, font, item.timeLeft(), rightEdge, rowY);
        }
    }
}
