package co.lemee.auctionhouse.network;

import co.lemee.auctionhouse.AuctionHouseMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;

/**
 * Server-side registration of the configuration-phase handshake.
 * Call {@link #register()} once from the main Fabric mod initialiser.
 */
public final class AuctionHouseHandshake {

    private AuctionHouseHandshake() {}

    public static void register() {
        // Declare the payload types for each direction
        PayloadTypeRegistry.configurationS2C().register(
                AuctionHouseQueryPayload.TYPE, AuctionHouseQueryPayload.CODEC);
        PayloadTypeRegistry.configurationC2S().register(
                AuctionHouseResponsePayload.TYPE, AuctionHouseResponsePayload.CODEC);

        // Play-phase S2C: server asks client to open the search screen
        PayloadTypeRegistry.playS2C().register(
                AuctionHouseOpenSearchPayload.TYPE, AuctionHouseOpenSearchPayload.CODEC);

        // Set the common-module hook so AuctionHouseSearchCommand can send without platform imports
        AuctionHouseMod.openSearchScreen =
                player -> ServerPlayNetworking.send(player, new AuctionHouseOpenSearchPayload());

        // During config phase: send the query only if the client advertises the channel
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (ServerConfigurationNetworking.canSend(handler, AuctionHouseQueryPayload.TYPE)) {
                ServerConfigurationNetworking.send(handler, new AuctionHouseQueryPayload());
            }
        });

        // Receive the client's version response and store it
        ServerConfigurationNetworking.registerGlobalReceiver(
                AuctionHouseResponsePayload.TYPE,
                (payload, context) -> {
                    var profile = ((ServerCommonPacketListenerImpl) context.networkHandler()).getOwner();
                    ClientModStatus.setClientVersion(profile.id(), payload.version());
                    AuctionHouseMod.LOGGER.info(
                            "AuctionHouse: client {} connected with mod v{}",
                            profile.name(), payload.version());
                });

        // Clean up when a player disconnects during play phase
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                ClientModStatus.remove(handler.player.getUUID()));
    }
}
