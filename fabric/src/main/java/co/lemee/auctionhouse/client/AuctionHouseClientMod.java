package co.lemee.auctionhouse.client;

import co.lemee.auctionhouse.AuctionHouseMod;
import co.lemee.auctionhouse.gui.GUIAuctionHouseSearch;
import co.lemee.auctionhouse.network.AuctionHouseBuyPayload;
import co.lemee.auctionhouse.network.AuctionHouseListingsPayload;
import co.lemee.auctionhouse.network.AuctionHouseOpenSearchPayload;
import co.lemee.auctionhouse.network.AuctionHouseQueryPayload;
import co.lemee.auctionhouse.network.AuctionHouseRequestListingsPayload;
import co.lemee.auctionhouse.network.AuctionHouseResponsePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Fabric client entrypoint. Registers client-side handlers for:
 * <ul>
 *   <li>Config-phase handshake query → sends back the mod version</li>
 *   <li>Play-phase "open search screen" signal from the server</li>
 *   <li>Play-phase listing snapshots → forwards to the open screen</li>
 * </ul>
 * Also sets {@link AuctionHouseMod#requestListingsRefresh} so the screen can
 * trigger a C2S refresh without importing Fabric API classes.
 */
public class AuctionHouseClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // ── Config phase: respond to handshake with our mod version ───────────
        ClientConfigurationNetworking.registerGlobalReceiver(
                AuctionHouseQueryPayload.TYPE,
                (payload, context) -> {
                    String version = FabricLoader.getInstance()
                            .getModContainer(AuctionHouseMod.MOD_ID)
                            .map(c -> c.getMetadata().getVersion().getFriendlyString())
                            .orElse("unknown");
                    context.responseSender().sendPacket(new AuctionHouseResponsePayload(version));
                });

        // ── Play phase: open the search screen ────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(
                AuctionHouseOpenSearchPayload.TYPE,
                (payload, context) -> context.client().execute(() ->
                        context.client().setScreen(
                                new GUIAuctionHouseSearch(
                                        Component.literal("Search Auction House")))));

        // ── Play phase: receive listing snapshot → update the open screen ──────
        ClientPlayNetworking.registerGlobalReceiver(
                AuctionHouseListingsPayload.TYPE,
                (payload, context) -> context.client().execute(() ->
                        GUIAuctionHouseSearch.updateListings(payload.items())));

        // ── Refresh hook: screen calls this every ~3 s ─────────────────────────
        AuctionHouseMod.requestListingsRefresh = () ->
                ClientPlayNetworking.send(new AuctionHouseRequestListingsPayload());

        // ── Buy hook: confirmation screen sends purchase request to server ──────
        AuctionHouseMod.sendBuy = id ->
                ClientPlayNetworking.send(new AuctionHouseBuyPayload(id));
    }
}
