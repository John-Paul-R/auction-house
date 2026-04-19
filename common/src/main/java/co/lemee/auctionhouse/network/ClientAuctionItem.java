package co.lemee.auctionhouse.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Lightweight, wire-serialisable representation of a listing sent to modded clients.
 * Created server-side from {@link co.lemee.auctionhouse.auction.AuctionItem}; used
 * client-side in {@link co.lemee.auctionhouse.gui.GUIAuctionHouseSearch}.
 */
public record ClientAuctionItem(int id, ItemStack itemStack, String ownerName, double price, String timeLeft) {

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientAuctionItem> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ClientAuctionItem decode(RegistryFriendlyByteBuf buf) {
                    int id          = buf.readInt();
                    ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
                    String owner    = buf.readUtf();
                    double price    = buf.readDouble();
                    String timeLeft = buf.readUtf();
                    return new ClientAuctionItem(id, stack, owner, price, timeLeft);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, ClientAuctionItem item) {
                    buf.writeInt(item.id());
                    ItemStack.STREAM_CODEC.encode(buf, item.itemStack());
                    buf.writeUtf(item.ownerName());
                    buf.writeDouble(item.price());
                    buf.writeUtf(item.timeLeft());
                }
            };
}
