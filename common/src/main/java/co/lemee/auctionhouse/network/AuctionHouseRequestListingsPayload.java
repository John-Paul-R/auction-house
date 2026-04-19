package co.lemee.auctionhouse.network;

import co.lemee.auctionhouse.AuctionHouseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → Server (play phase). Sent by {@link co.lemee.auctionhouse.gui.GUIAuctionHouseSearch}
 * every ~3 seconds to request a fresh {@link AuctionHouseListingsPayload} snapshot.
 */
public record AuctionHouseRequestListingsPayload() implements CustomPacketPayload {

    public static final Type<AuctionHouseRequestListingsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AuctionHouseMod.MOD_ID, "request_listings"));

    public static final StreamCodec<FriendlyByteBuf, AuctionHouseRequestListingsPayload> CODEC =
            StreamCodec.unit(new AuctionHouseRequestListingsPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
