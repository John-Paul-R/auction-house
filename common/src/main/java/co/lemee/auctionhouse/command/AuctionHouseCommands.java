package co.lemee.auctionhouse.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;

public final class AuctionHouseCommands {

    private AuctionHouseCommands() {}

    // NOTE: the root "ah" node must be registered first so Brigadier's merge
    // preserves its .requires predicate when subsequent subcommand registrations
    // are merged into the same node.
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, PermissionProvider perms) {
        dispatcher.register(Commands.literal("ah")
                .requires(perms.require(PermissionNodes.MAIN, 0))
                .executes(AuctionHouseMainCommand::run));

        dispatcher.register(Commands.literal("ah")
                .then(Commands.literal("cancel")
                        .requires(perms.require(PermissionNodes.CANCEL, 0))
                        .executes(AuctionHouseCancelCommand::run)));

        dispatcher.register(Commands.literal("ah")
                .then(Commands.literal("expired")
                        .requires(perms.require(PermissionNodes.EXPIRED, 0))
                        .executes(AuctionHouseExpiredCommand::run)));

        dispatcher.register(Commands.literal("ah")
                .then(Commands.literal("help")
                        .requires(perms.require(PermissionNodes.HELP, 0))
                        .executes(AuctionHouseHelpCommand::run)));

        dispatcher.register(Commands.literal("ah")
                .then(Commands.literal("return")
                        .requires(perms.require(PermissionNodes.RETURN, 0))
                        .executes(AuctionHouseReturnCommand::run)));

        dispatcher.register(Commands.literal("ah")
                .then(Commands.literal("reload")
                        .requires(perms.require(PermissionNodes.RELOAD, 4))
                        .executes(AuctionHouseReloadCommand::run)));

        dispatcher.register(Commands.literal("ah")
                .then(Commands.literal("sell")
                        .then(Commands.argument("price", DoubleArgumentType.doubleArg(0))
                                .requires(perms.require(PermissionNodes.SELL, 0))
                                .executes(AuctionHouseSellCommand::run))));

        dispatcher.register(Commands.literal("ah")
                .then(Commands.literal("selling")
                        .requires(perms.require(PermissionNodes.SELLING, 0))
                        .executes(AuctionHouseSellingCommand::run)));

        dispatcher.register(Commands.literal("ah")
                .then(Commands.literal("search")
                        .requires(perms.require(PermissionNodes.MAIN, 0))
                        .executes(AuctionHouseSearchCommand::run)));
    }
}
