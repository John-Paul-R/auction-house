package co.lemee.auctionhouse.neoforge;

import co.lemee.auctionhouse.AuctionHouseMod;
import co.lemee.auctionhouse.network.ClientModStatus;
import co.lemee.auctionhouse.neoforge.network.AuctionHouseNetworkHandlers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;


@Mod(AuctionHouseMod.MOD_ID)
public class AuctionHouseModNeoForge {

    public AuctionHouseModNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        AuctionHouseMod.realeconomy = ModList.get().isLoaded("realeconomy");
        AuctionHouseMod.impactor = ModList.get().isLoaded("impactor");
        AuctionHouseMod.initialize();

        modEventBus.register(new AuctionHouseNetworkHandlers());
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new AuctionHousePermissions());
    }

    @SubscribeEvent
    public void onServerStart(ServerStartedEvent event) {
        AuctionHouseMod.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ClientModStatus.remove(event.getEntity().getUUID());
    }
}