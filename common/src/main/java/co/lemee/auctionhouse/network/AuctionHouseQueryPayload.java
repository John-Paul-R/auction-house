package co.lemee.auctionhouse.network;

import co.lemee.auctionhouse.AuctionHouseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → Client configuration-phase payload. The server sends this to ask
 * "do you have the AuctionHouse mod?". Modded clients reply with
 * {@link AuctionHouseResponsePayload}; vanilla clients silently drop it.
 */
public record AuctionHouseQueryPayload() implements CustomPacketPayload {

    public static final Type<AuctionHouseQueryPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AuctionHouseMod.MOD_ID, "handshake_query"));

    public static final StreamCodec<FriendlyByteBuf, AuctionHouseQueryPayload> CODEC =
            StreamCodec.unit(new AuctionHouseQueryPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
