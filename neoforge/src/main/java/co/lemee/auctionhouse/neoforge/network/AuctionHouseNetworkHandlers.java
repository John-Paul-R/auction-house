package co.lemee.auctionhouse.neoforge.network;

import co.lemee.auctionhouse.AuctionHouseMod;
import co.lemee.auctionhouse.gui.GUIAuctionHouseSearch;
import co.lemee.auctionhouse.network.AuctionHouseOpenSearchPayload;
import co.lemee.auctionhouse.network.AuctionHouseQueryPayload;
import co.lemee.auctionhouse.network.AuctionHouseResponsePayload;
import co.lemee.auctionhouse.network.ClientModStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers handshake + search payload types on the NeoForge mod bus.
 * Register an instance of this class with the mod event bus via
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

        // --- Configuration phase ---

        // C2S response: server stores UUID + version and finishes the config task
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

        // S2C query: only invoked on the client (receiving) side
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

        // --- Play phase ---

        // S2C: server asks client to open the search screen
        registrar.playToClient(
                AuctionHouseOpenSearchPayload.TYPE,
                AuctionHouseOpenSearchPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.setScreen(new GUIAuctionHouseSearch(
                            Component.literal("Search Auction House")));
                }));

        // Populate the common-module hook so AuctionHouseSearchCommand can send
        // this packet without needing to import NeoForge classes
        AuctionHouseMod.openSearchScreen =
                player -> PacketDistributor.sendToPlayer(player, new AuctionHouseOpenSearchPayload());
    }

    @SubscribeEvent
    public void onRegisterConfigTasks(RegisterConfigurationTasksEvent event) {
        event.register(new AuctionHouseConfigTask(event.getListener()));
    }
}
