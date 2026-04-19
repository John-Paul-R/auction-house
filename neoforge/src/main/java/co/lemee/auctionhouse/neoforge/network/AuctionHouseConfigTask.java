package co.lemee.auctionhouse.neoforge.network;

import co.lemee.auctionhouse.AuctionHouseMod;
import co.lemee.auctionhouse.network.AuctionHouseQueryPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.server.network.ConfigurationTask;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.common.extensions.IServerConfigurationPacketListenerExtension;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;

import java.util.function.Consumer;

/**
 * NeoForge configuration-phase task. Sends the handshake query to modded clients
 * and finishes immediately for vanilla clients.
 */
public record AuctionHouseConfigTask(ServerConfigurationPacketListener listener)
        implements ICustomConfigurationTask {

    public static final ConfigurationTask.Type TYPE =
            new ConfigurationTask.Type(AuctionHouseMod.MOD_ID + ":handshake_config");

    @Override
    public void run(Consumer<CustomPacketPayload> sender) {
        if (((ICommonPacketListener) listener).hasChannel(AuctionHouseQueryPayload.TYPE)) {
            // Modded client: send query and wait for response to call finishCurrentTask
            sender.accept(new AuctionHouseQueryPayload());
        } else {
            // Vanilla client: nothing to do, advance immediately
            ((IServerConfigurationPacketListenerExtension) listener).finishCurrentTask(TYPE);
        }
    }

    @Override
    public ConfigurationTask.Type type() {
        return TYPE;
    }
}
