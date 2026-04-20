package co.lemee.auctionhouse.network;

import co.lemee.auctionhouse.AuctionHouseMod;
import co.lemee.auctionhouse.network.AuctionHouseBuyPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;

/**
 * Server-side registration of the configuration-phase handshake and play-phase
 * search payloads. Call {@link #register()} once from the main Fabric mod initialiser.
 */
public final class AuctionHouseHandshake {

    private AuctionHouseHandshake() {}

    public static void register() {
        // ── Configuration-phase payload types ─────────────────────────────────
        PayloadTypeRegistry.configurationS2C().register(
                AuctionHouseQueryPayload.TYPE, AuctionHouseQueryPayload.CODEC);
        PayloadTypeRegistry.configurationC2S().register(
                AuctionHouseResponsePayload.TYPE, AuctionHouseResponsePayload.CODEC);

        // ── Play-phase payload types ───────────────────────────────────────────
        // S2C: tells the client to open the search screen
        PayloadTypeRegistry.playS2C().register(
                AuctionHouseOpenSearchPayload.TYPE, AuctionHouseOpenSearchPayload.CODEC);
        // S2C: delivers a listing snapshot to the client
        PayloadTypeRegistry.playS2C().register(
                AuctionHouseListingsPayload.TYPE, AuctionHouseListingsPayload.CODEC);
        // C2S: client asks for a fresh snapshot
        PayloadTypeRegistry.playC2S().register(
                AuctionHouseRequestListingsPayload.TYPE, AuctionHouseRequestListingsPayload.CODEC);
        // C2S: client confirms a purchase
        PayloadTypeRegistry.playC2S().register(
                AuctionHouseBuyPayload.TYPE, AuctionHouseBuyPayload.CODEC);

        // ── Common-module hooks ────────────────────────────────────────────────

        // openSearchScreen: send the open-trigger + initial snapshot together
        AuctionHouseMod.openSearchScreen = player -> {
            ServerPlayNetworking.send(player, new AuctionHouseOpenSearchPayload());
            ServerPlayNetworking.send(player, AuctionHouseMod.buildListingsPayload());
        };

        // pushListingsToPlayer: used by notifyListingsChanged() for server-push updates
        AuctionHouseMod.pushListingsToPlayer = ServerPlayNetworking::send;

        // ── Config-phase handshake: server → client ────────────────────────────
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (ServerConfigurationNetworking.canSend(handler, AuctionHouseQueryPayload.TYPE)) {
                ServerConfigurationNetworking.send(handler, new AuctionHouseQueryPayload());
            }
        });

        // Config-phase handshake: client → server (version stored)
        ServerConfigurationNetworking.registerGlobalReceiver(
                AuctionHouseResponsePayload.TYPE,
                (payload, context) -> {
                    var profile = ((ServerCommonPacketListenerImpl) context.networkHandler()).getOwner();
                    ClientModStatus.setClientVersion(profile.id(), payload.version());
                    AuctionHouseMod.LOGGER.info(
                            "AuctionHouse: client {} connected with mod v{}",
                            profile.name(), payload.version());
                });

        // ── Play-phase: refresh request (C2S) ─────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(
                AuctionHouseRequestListingsPayload.TYPE,
                (payload, context) -> {
                    // Respond on the server thread — ah.items is not thread-safe
                    context.server().execute(() ->
                            ServerPlayNetworking.send(
                                    context.player(),
                                    AuctionHouseMod.buildListingsPayload()));
                });

        // ── Play-phase: buy request (C2S) ─────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(
                AuctionHouseBuyPayload.TYPE,
                (payload, context) ->
                        context.server().execute(() ->
                                AuctionHouseMod.handleBuy(context.player(), payload.auctionId())));

        // ── Disconnect cleanup ─────────────────────────────────────────────────
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ClientModStatus.remove(handler.player.getUUID());
            AuctionHouseMod.removeWatcher(handler.player.getUUID());
        });
    }
}
