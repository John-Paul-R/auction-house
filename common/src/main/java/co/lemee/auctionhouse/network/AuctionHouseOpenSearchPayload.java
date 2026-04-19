package co.lemee.auctionhouse.network;

import co.lemee.auctionhouse.AuctionHouseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → Client play-phase payload. The server sends this to ask the client
 * to open the {@link co.lemee.auctionhouse.gui.GUIAuctionHouseSearch} screen.
 * Only sent to clients that confirmed they have the mod (see {@link ClientModStatus}).
 */
public record AuctionHouseOpenSearchPayload() implements CustomPacketPayload {

    public static final Type<AuctionHouseOpenSearchPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AuctionHouseMod.MOD_ID, "open_search"));

    public static final StreamCodec<FriendlyByteBuf, AuctionHouseOpenSearchPayload> CODEC =
            StreamCodec.unit(new AuctionHouseOpenSearchPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
