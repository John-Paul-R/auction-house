package co.lemee.auctionhouse.client;

import co.lemee.auctionhouse.AuctionHouseMod;
import co.lemee.auctionhouse.gui.GUIAuctionHouseSearch;
import co.lemee.auctionhouse.network.AuctionHouseOpenSearchPayload;
import co.lemee.auctionhouse.network.AuctionHouseQueryPayload;
import co.lemee.auctionhouse.network.AuctionHouseResponsePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Fabric client entrypoint. Registers client-side handlers for:
 * - The configuration-phase handshake query (sends back the mod version)
 * - The play-phase "open search screen" signal from the server
 */
public class AuctionHouseClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Config phase: respond to server handshake with our mod version
        ClientConfigurationNetworking.registerGlobalReceiver(
                AuctionHouseQueryPayload.TYPE,
                (payload, context) -> {
                    String version = FabricLoader.getInstance()
                            .getModContainer(AuctionHouseMod.MOD_ID)
                            .map(c -> c.getMetadata().getVersion().getFriendlyString())
                            .orElse("unknown");
                    context.responseSender().sendPacket(new AuctionHouseResponsePayload(version));
                });

        // Play phase: server asked us to open the search screen
        ClientPlayNetworking.registerGlobalReceiver(
                AuctionHouseOpenSearchPayload.TYPE,
                (payload, context) -> context.client().execute(() ->
                        context.client().setScreen(
                                new GUIAuctionHouseSearch(Component.literal("Search Auction House")))));
    }
}
