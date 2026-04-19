package co.lemee.auctionhouse.network;

import co.lemee.auctionhouse.AuctionHouseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → Server configuration-phase payload. Sent in response to
 * {@link AuctionHouseQueryPayload} to confirm the client has the mod,
 * and to report the installed mod version.
 */
public record AuctionHouseResponsePayload(String version) implements CustomPacketPayload {

    public static final Type<AuctionHouseResponsePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AuctionHouseMod.MOD_ID, "handshake_response"));

    public static final StreamCodec<FriendlyByteBuf, AuctionHouseResponsePayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeUtf(payload.version()),
                    buf -> new AuctionHouseResponsePayload(buf.readUtf())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
