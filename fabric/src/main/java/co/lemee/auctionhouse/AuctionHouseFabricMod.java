package co.lemee.auctionhouse;

import co.lemee.auctionhouse.command.AuctionHouseCommands;
import co.lemee.auctionhouse.network.AuctionHouseHandshake;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

import static co.lemee.auctionhouse.AuctionHouseMod.LOGGER;

public class AuctionHouseFabricMod implements ModInitializer {

    @Override
    public void onInitialize() {

        AuctionHouseMod.realeconomy = FabricLoader.getInstance().isModLoaded("realeconomy");
        AuctionHouseMod.impactor = FabricLoader.getInstance().isModLoaded("impactor");

        AuctionHouseMod.initialize();
        AuctionHouseHandshake.register();

        ServerLifecycleEvents.SERVER_STARTED.register(AuctionHouseMod::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(AuctionHouseMod::onServerStopping);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                AuctionHouseCommands.register(dispatcher, (node, level) -> Permissions.require(node.node(), level)));

        LOGGER.info("AuctionHouse loaded!");
    }
}