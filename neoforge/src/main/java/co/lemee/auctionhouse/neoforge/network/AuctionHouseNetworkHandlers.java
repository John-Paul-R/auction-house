package co.lemee.auctionhouse.neoforge.network;

import co.lemee.auctionhouse.AuctionHouseMod;
import co.lemee.auctionhouse.gui.GUIAuctionHouseSearch;
import co.lemee.auctionhouse.network.AuctionHouseBuyPayload;
import co.lemee.auctionhouse.network.AuctionHouseListingsPayload;
import co.lemee.auctionhouse.network.AuctionHouseOpenSearchPayload;
import co.lemee.auctionhouse.network.AuctionHouseQueryPayload;
import co.lemee.auctionhouse.network.AuctionHouseRequestListingsPayload;
import co.lemee.auctionhouse.network.AuctionHouseResponsePayload;
import co.lemee.auctionhouse.network.ClientModStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.extensions.IClientCommonPacketListenerExtension;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers handshake + search payload types on the NeoForge mod bus.
 * An instance of this class must be registered with the mod event bus:
 * {@code modEventBus.register(new AuctionHouseNetworkHandlers())}.
 *
 * <p>Handlers for S2C payloads (configurationToClient, playToClient) reference
 * {@code Minecraft} safely because NeoForge only invokes them on the receiving
 * (client) side, never on a dedicated server.
 */
public class AuctionHouseNetworkHandlers {

    @SubscribeEvent
    public void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(AuctionHouseMod.MOD_ID).optional();

        // ── Configuration phase ────────────────────────────────────────────────

        // C2S: client confirms it has the mod + sends version; server stores it and finishes task
        registrar.configurationToServer(
                AuctionHouseResponsePayload.TYPE,
                AuctionHouseResponsePayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    var profile = ((ServerCommonPacketListenerImpl) context.listener()).getOwner();
                    ClientModStatus.setClientVersion(profile.id(), payload.version());
                    AuctionHouseMod.LOGGER.info(
                            "AuctionHouse: client {} connected with mod v{}",
                            profile.name(), payload.version());
                    context.finishCurrentTask(AuctionHouseConfigTask.TYPE);
                }));

        // S2C: server pings the client (client replies with AuctionHouseResponsePayload)
        registrar.configurationToClient(
                AuctionHouseQueryPayload.TYPE,
                AuctionHouseQueryPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    String version = ModList.get()
                            .getModContainerById(AuctionHouseMod.MOD_ID)
                            .map(c -> c.getModInfo().getVersion().toString())
                            .orElse("unknown");
                    context.reply(new AuctionHouseResponsePayload(version));
                }));

        // ── Play phase ─────────────────────────────────────────────────────────

        // S2C: tells the client to open the search screen
        registrar.playToClient(
                AuctionHouseOpenSearchPayload.TYPE,
                AuctionHouseOpenSearchPayload.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        Minecraft.getInstance().setScreen(
                                new GUIAuctionHouseSearch(
                                        Component.literal("Search Auction House")))));

        // S2C: delivers listing snapshot → forwarded to the open screen
        registrar.playToClient(
                AuctionHouseListingsPayload.TYPE,
                AuctionHouseListingsPayload.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        GUIAuctionHouseSearch.updateListings(payload.items())));

        // C2S: client requests a fresh snapshot
        registrar.playToServer(
                AuctionHouseRequestListingsPayload.TYPE,
                AuctionHouseRequestListingsPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (AuctionHouseMod.ah != null) {
                        PacketDistributor.sendToPlayer(
                                (ServerPlayer) context.player(),
                                AuctionHouseMod.buildListingsPayload());
                    }
                }));

        // C2S: client confirms a purchase
        registrar.playToServer(
                AuctionHouseBuyPayload.TYPE,
                AuctionHouseBuyPayload.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        AuctionHouseMod.handleBuy((ServerPlayer) context.player(), payload.auctionId())));

        // ── Common-module hooks ────────────────────────────────────────────────

        // openSearchScreen: send open-trigger + initial snapshot together
        AuctionHouseMod.openSearchScreen = player -> {
            PacketDistributor.sendToPlayer(player, new AuctionHouseOpenSearchPayload());
            PacketDistributor.sendToPlayer(player, AuctionHouseMod.buildListingsPayload());
        };

        // requestListingsRefresh: called by the client screen every ~3 s
        AuctionHouseMod.requestListingsRefresh = () -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) {
                ((IClientCommonPacketListenerExtension) conn)
                        .send(new AuctionHouseRequestListingsPayload());
            }
        };

        // sendBuy: called by the confirmation screen to purchase a listing
        AuctionHouseMod.sendBuy = id -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) {
                ((IClientCommonPacketListenerExtension) conn)
                        .send(new AuctionHouseBuyPayload(id));
            }
        };
    }

    @SubscribeEvent
    public void onRegisterConfigTasks(RegisterConfigurationTasksEvent event) {
        event.register(new AuctionHouseConfigTask(event.getListener()));
    }
}
