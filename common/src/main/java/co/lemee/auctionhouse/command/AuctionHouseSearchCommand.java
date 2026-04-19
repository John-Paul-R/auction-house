package co.lemee.auctionhouse.command;

import co.lemee.auctionhouse.AuctionHouseMod;
import co.lemee.auctionhouse.network.ClientModStatus;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class AuctionHouseSearchCommand {

    public static int run(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        if (ClientModStatus.hasClientMod(player.getUUID())) {
            // Client has the mod — ask it to open the search screen
            AuctionHouseMod.openSearchScreen.accept(player);
        } else {
            // Vanilla client or client without the mod — inform the player
            player.sendSystemMessage(Component.literal(
                    "§eThe search UI requires the AuctionHouse client mod."));
        }
        return 1;
    }
}
