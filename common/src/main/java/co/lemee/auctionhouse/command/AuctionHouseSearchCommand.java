package co.lemee.auctionhouse.command;

import co.lemee.auctionhouse.gui.GUIAuctionHouse;
import co.lemee.auctionhouse.gui.GUIAuctionHouseSearch;
import co.lemee.auctionhouse.gui.GUIPersonalAuctionHouse;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public class AuctionHouseSearchCommand {
    public static int run(CommandContext<CommandSourceStack> context) {
//        GUIAuctionHouse guiAuctionHouse = new GUIAuctionHouse(context.getSource().getPlayer());
//        guiAuctionHouse.open();
        var screen = new GUIAuctionHouseSearch(Component.literal("My Screen"));
        Minecraft.getInstance().setScreen(screen);
        return 0;
    }
}
