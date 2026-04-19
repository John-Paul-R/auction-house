package co.lemee.auctionhouse.neoforge;

import co.lemee.auctionhouse.AuctionHouseMod;
import co.lemee.auctionhouse.command.AuctionHouseCommands;
import co.lemee.auctionhouse.command.PermissionNodes;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import static co.lemee.auctionhouse.AuctionHouseMod.LOGGER;

public class AuctionHousePermissions {

    private static final PermissionNode<Boolean> CANCEL_PERM = new PermissionNode<>(
            AuctionHouseMod.MOD_ID, "cancel", PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true);

    private static final PermissionNode<Boolean> EXPIRED_PERM = new PermissionNode<>(
            AuctionHouseMod.MOD_ID, "expired", PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true);

    private static final PermissionNode<Boolean> HELP_PERM = new PermissionNode<>(
            AuctionHouseMod.MOD_ID, "help", PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true);

    private static final PermissionNode<Boolean> MAIN_PERM = new PermissionNode<>(
            AuctionHouseMod.MOD_ID, "main", PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true);

    private static final PermissionNode<Boolean> RETURN_PERM = new PermissionNode<>(
            AuctionHouseMod.MOD_ID, "return", PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true);

    private static final PermissionNode<Boolean> RELOAD_PERM = new PermissionNode<>(
            AuctionHouseMod.MOD_ID, "reload", PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> {
                if (player == null) return false;
                return player.permissions().hasPermission(Permissions.COMMANDS_OWNER);
            });

    private static final PermissionNode<Boolean> SELL_PERM = new PermissionNode<>(
            AuctionHouseMod.MOD_ID, "sell", PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true);

    private static final PermissionNode<Boolean> SELLING_PERM = new PermissionNode<>(
            AuctionHouseMod.MOD_ID, "selling", PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true);

    public static boolean hasPermission(CommandSourceStack source, PermissionNode<Boolean> permission) {
        try {
            return PermissionAPI.getPermission(source.getPlayerOrException(), permission);
        } catch (CommandSyntaxException e) {
            return false;
        }
    }

    // Exhaustive switch — compiler will reject missing cases if PermissionNodes gains new constants.
    private static PermissionNode<Boolean> permNodeFor(PermissionNodes node) {
        return switch (node) {
            case CANCEL  -> CANCEL_PERM;
            case EXPIRED -> EXPIRED_PERM;
            case HELP    -> HELP_PERM;
            case MAIN    -> MAIN_PERM;
            case RETURN  -> RETURN_PERM;
            case RELOAD  -> RELOAD_PERM;
            case SELL    -> SELL_PERM;
            case SELLING -> SELLING_PERM;
        };
    }

    @SubscribeEvent
    public void permission(PermissionGatherEvent.Nodes event) {
        LOGGER.info("Registering permission nodes...");
        event.addNodes(CANCEL_PERM, EXPIRED_PERM, HELP_PERM, MAIN_PERM, RETURN_PERM, RELOAD_PERM, SELL_PERM, SELLING_PERM);
    }

    @SubscribeEvent
    public void onCommandsRegister(RegisterCommandsEvent event) {
        AuctionHouseCommands.register(event.getDispatcher(),
                (node, level) -> cs -> hasPermission(cs, permNodeFor(node)));
    }
}
