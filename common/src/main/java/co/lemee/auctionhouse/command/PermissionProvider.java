package co.lemee.auctionhouse.command;

import net.minecraft.commands.CommandSourceStack;

import java.util.function.Predicate;

@FunctionalInterface
public interface PermissionProvider {
    Predicate<CommandSourceStack> require(PermissionNodes node, int defaultOpLevel);
}
