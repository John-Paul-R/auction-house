package co.lemee.auctionhouse;

import co.lemee.auctionhouse.auction.AuctionItem;
import co.lemee.auctionhouse.auction.ExpiredItems;
import co.lemee.auctionhouse.config.ConfigManager;
import co.lemee.auctionhouse.economy.EconomyHandler;
import co.lemee.auctionhouse.network.AuctionHouseListingsPayload;
import co.lemee.auctionhouse.network.ClientAuctionItem;
import co.lemee.auctionhouse.network.ClientModStatus;
import co.lemee.auctionhouse.sql.DatabaseManager;
import co.lemee.auctionhouse.sql.SQLiteDatabaseManager;
import co.lemee.auctionhouse.util.CommonMethods;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class AuctionHouseMod {
    public static final String MOD_ID = "auctionhouse";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Connection connection;
    public static co.lemee.auctionhouse.auction.AuctionHouse ah;
    public static ExpiredItems ei;
    public static ArrayList<String> tableRegistry = new ArrayList<>();
    public static boolean impactor = false;
    public static boolean realeconomy = false;
    public static MinecraftServer server;

    // =========================================================================
    // Platform-specific hooks (set by each loader at startup)
    // =========================================================================

    /**
     * Platform hook: sends the "open search screen" packet + initial listings
     * snapshot to a player.  Do not call directly — use
     * {@link #openSearchForPlayer(ServerPlayer)} so the player is registered as
     * a watcher.
     */
    public static Consumer<ServerPlayer> openSearchScreen = player -> {};

    /**
     * Platform hook: push a {@link AuctionHouseListingsPayload} to a specific
     * online player.  Set by each loader in its network registration.
     */
    public static BiConsumer<ServerPlayer, AuctionHouseListingsPayload> pushListingsToPlayer
            = (player, payload) -> {};

    /**
     * Kept for API compatibility; no longer called by the client screen.
     * The client used to send a C2S refresh request every ~3 s; that polling
     * has been replaced by server-side push via {@link #notifyListingsChanged()}.
     *
     * @deprecated Listing refreshes are now server-initiated; this hook is a no-op.
     */
    @Deprecated
    public static Runnable requestListingsRefresh = () -> {};

    /** Platform hook: called by the buy-confirmation screen (client-side). */
    public static Consumer<Integer> sendBuy = id -> {};

    // =========================================================================
    // Search-screen watcher registry
    // =========================================================================

    /**
     * UUIDs of players currently (or recently) viewing the search screen.
     * Populated by {@link #openSearchForPlayer}; pruned opportunistically by
     * {@link #notifyListingsChanged} when iterating online players.
     */
    private static final Set<UUID> searchScreenWatchers = ConcurrentHashMap.newKeySet();

    /**
     * Hash of the listings snapshot last sent to each watcher.  Used to skip
     * sending a new payload when the listing set hasn't actually changed.
     * Key: player UUID; Value: hash from {@link #computeListingsHash()}.
     */
    private static final ConcurrentHashMap<UUID, Long> watcherLastHash = new ConcurrentHashMap<>();

    /**
     * Opens the search screen for {@code player}, registers them as a watcher
     * so they receive future server-push updates, and records the current hash
     * so the first push after opening doesn't redundantly resend the same data.
     * <p>
     * Call this instead of {@code openSearchScreen.accept(player)} directly.
     */
    public static void openSearchForPlayer(ServerPlayer player) {
        long hash = computeListingsHash();
        searchScreenWatchers.add(player.getUUID());
        watcherLastHash.put(player.getUUID(), hash);
        openSearchScreen.accept(player);
    }

    /**
     * Remove a player from the watcher registry (call on disconnect).
     * Safe to call even if the player was never a watcher.
     */
    public static void removeWatcher(UUID uuid) {
        searchScreenWatchers.remove(uuid);
        watcherLastHash.remove(uuid);
    }

    /**
     * Called whenever the auction house listing set changes (item added,
     * purchased, or expired).  Computes a hash of the current listings and
     * pushes a fresh snapshot to every watching player whose locally-stored
     * hash no longer matches.
     *
     * <p>Must be called on the server thread.
     */
    public static void notifyListingsChanged() {
        if (server == null || ah == null) return;

        long newHash = computeListingsHash();

        // Prune offline watchers opportunistically so the set doesn't grow
        // without bound on servers where players never explicitly disconnect.
        Set<UUID> onlineUuids = server.getPlayerList().getPlayers()
                .stream().map(ServerPlayer::getUUID).collect(Collectors.toSet());
        searchScreenWatchers.retainAll(onlineUuids);
        watcherLastHash.keySet().retainAll(onlineUuids);

        // Build the payload lazily — only if at least one watcher needs it.
        AuctionHouseListingsPayload payload = null;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!searchScreenWatchers.contains(player.getUUID())) continue;
            Long lastHash = watcherLastHash.get(player.getUUID());
            if (lastHash != null && lastHash == newHash) continue; // nothing changed for this player

            if (payload == null) payload = buildListingsPayload();
            watcherLastHash.put(player.getUUID(), newHash);
            pushListingsToPlayer.accept(player, payload);
        }
    }

    /**
     * Computes a stable hash over the current set of auction item IDs.
     * Sorted so that insertion order doesn't affect the result.
     * Price/time changes are intentionally excluded — only structural
     * changes (add/remove) trigger a push.
     */
    private static long computeListingsHash() {
        if (ah == null) return 0L;
        return ah.items.stream()
                .mapToLong(AuctionItem::getId)
                .sorted()
                .reduce(1L, (h, id) -> h * 31L + id);
    }

    // =========================================================================
    // Listing helpers
    // =========================================================================

    /** Build the current listing snapshot for transmission to a modded client. */
    public static List<ClientAuctionItem> buildListings() {
        if (ah == null) return List.of();
        return ah.items.stream()
                .map(i -> new ClientAuctionItem(
                        i.getId(),
                        i.getItemStack().copy(),
                        i.getOwner(),
                        i.getPrice(),
                        i.getTimeLeft()))
                .collect(Collectors.toList());
    }

    /** Convenience: build and wrap in a payload ready for sending. */
    public static AuctionHouseListingsPayload buildListingsPayload() {
        return new AuctionHouseListingsPayload(buildListings());
    }

    // =========================================================================
    // Server lifecycle
    // =========================================================================

    static {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(SQLiteDatabaseManager.url);
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static DatabaseManager getDatabaseManager() {
        return new SQLiteDatabaseManager();
    }

    public static void onServerStarted(MinecraftServer server) {
        AuctionHouseMod.server = server;
        SQLiteDatabaseManager.createTables(tableRegistry);
        CommonMethods.reloadHouse();
        CommonMethods.reloadExpired();
    }

    public static void onServerStopping(MinecraftServer server) {
        try {
            connection.close();
        } catch (SQLException e) {
            LOGGER.error("Closing database connection", e);
        }
    }

    // =========================================================================
    // Buy handler
    // =========================================================================

    /**
     * Server-side: look up the listing by {@code auctionId}, validate, transfer funds, and deliver the item.
     * Safe to call from the server thread. Mirrors the logic in {@link co.lemee.auctionhouse.gui.GUIAuctionItem}.
     */
    public static void handleBuy(ServerPlayer player, int auctionId) {
        if (ah == null) {
            player.sendSystemMessage(Component.literal("Auction house is not available.").withStyle(ChatFormatting.RED));
            return;
        }
        AuctionItem item = ah.items.stream()
                .filter(i -> i.getId() == auctionId)
                .findFirst().orElse(null);
        if (item == null || !getDatabaseManager().isItemForAuction(auctionId)) {
            player.sendSystemMessage(Component.literal("That item is no longer available.").withStyle(ChatFormatting.RED));
            return;
        }
        if (player.getInventory().getFreeSlot() == -1) {
            player.sendSystemMessage(Component.literal("You don't have any empty slot in your inventory.").withStyle(ChatFormatting.RED));
            return;
        }
        EconomyHandler economy = EconomyHandler.getInstance();
        if (!economy.isAvailable()) {
            player.sendSystemMessage(Component.literal("No economy plugin is configured on this server — purchases are disabled.").withStyle(ChatFormatting.RED));
            return;
        }
        if (economy.transfer(player.getUUID(), UUID.fromString(item.getUuid()), item.getPrice())) {
            getDatabaseManager().removeItemFromAuction(item);
            player.sendSystemMessage(Component.literal("You have purchased ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(String.valueOf(item.getItemStack().getCount())).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" "))
                    .append(Component.literal(item.getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE))
                    .append(Component.literal(" from ").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(item.getOwner()).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" for " + item.getPrice() + " $").withStyle(ChatFormatting.GREEN)));
            player.getInventory().add(item.getItemStack());
        } else {
            player.sendSystemMessage(Component.literal("You don't have enough money to buy this item.").withStyle(ChatFormatting.RED));
        }
    }

    public static void initialize() {
        if (!ConfigManager.loadConfig())
            throw new RuntimeException("Could not load config");

        LOGGER.info("AuctionHouse loaded!");

        tableRegistry.add("CREATE TABLE IF NOT EXISTS auctionhouse (id integer PRIMARY KEY AUTOINCREMENT, playeruuid text NOT NULL, owner text NOT NULL, nbt text NOT NULL, item text NOT NULL, count integer NOT NULL, price double NOT NULL, secondsLeft long NOT NULL);");
        tableRegistry.add("CREATE TABLE IF NOT EXISTS expireditems (id integer PRIMARY KEY, playeruuid text NOT NULL, owner text NOT NULL, nbt text NOT NULL, item text NOT NULL, count integer NOT NULL, price double NOT NULL);");
    }
}
