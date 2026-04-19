package co.lemee.auctionhouse.network;

import co.lemee.auctionhouse.AuctionHouseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → Server play-phase payload.
 * Sent when the player confirms a purchase in {@link co.lemee.auctionhouse.gui.GUIAuctionHouseSearch}.
 * The server looks up the listing by {@link #auctionId}, validates, and executes the transfer.
 */
public record AuctionHouseBuyPayload(int auctionId) implements CustomPacketPayload {

    public static final Type<AuctionHouseBuyPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AuctionHouseMod.MOD_ID, "buy"));

    public static final StreamCodec<FriendlyByteBuf, AuctionHouseBuyPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeInt(p.auctionId()),
                    buf -> new AuctionHouseBuyPayload(buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
