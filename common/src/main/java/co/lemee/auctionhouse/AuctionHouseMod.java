package co.lemee.auctionhouse;

import co.lemee.auctionhouse.auction.AuctionItem;
import co.lemee.auctionhouse.auction.ExpiredItems;
import co.lemee.auctionhouse.config.ConfigManager;
import co.lemee.auctionhouse.economy.EconomyHandler;
import co.lemee.auctionhouse.network.AuctionHouseListingsPayload;
import co.lemee.auctionhouse.network.ClientAuctionItem;
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
import java.util.UUID;
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

    /**
     * Platform-specific hook for sending the "open search screen" packet to a player.
     * Each loader initialises this at startup. Also sends the initial listings snapshot.
     */
    public static Consumer<ServerPlayer> openSearchScreen = player -> {};

    /**
     * Platform-specific hook called by {@link co.lemee.auctionhouse.gui.GUIAuctionHouseSearch}
     * (client-side) every ~3 s to request a fresh listings snapshot from the server.
     * Each loader sets this in its client initialiser.
     */
    public static Runnable requestListingsRefresh = () -> {};

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

    /**
     * Platform-specific hook called by the buy-confirmation screen (client-side) to send a
     * purchase request to the server. Each loader sets this in its client initialiser.
     */
    public static Consumer<Integer> sendBuy = id -> {};

    /** Convenience: build and wrap in a payload ready for sending. */
    public static AuctionHouseListingsPayload buildListingsPayload() {
        return new AuctionHouseListingsPayload(buildListings());
    }

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