package co.lemee.auctionhouse.network;

import co.lemee.auctionhouse.AuctionHouseMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → Client (play phase). Carries a full snapshot of current auction listings
 * so the client can filter them locally in {@link co.lemee.auctionhouse.gui.GUIAuctionHouseSearch}.
 * Sent immediately after {@link AuctionHouseOpenSearchPayload} on screen open, and again
 * whenever the client requests a refresh via {@link AuctionHouseRequestListingsPayload}.
 */
public record AuctionHouseListingsPayload(List<ClientAuctionItem> items) implements CustomPacketPayload {

    public static final Type<AuctionHouseListingsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AuctionHouseMod.MOD_ID, "listings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionHouseListingsPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public AuctionHouseListingsPayload decode(RegistryFriendlyByteBuf buf) {
                    int size = buf.readVarInt();
                    List<ClientAuctionItem> items = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        items.add(ClientAuctionItem.STREAM_CODEC.decode(buf));
                    }
                    return new AuctionHouseListingsPayload(items);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, AuctionHouseListingsPayload payload) {
                    buf.writeVarInt(payload.items().size());
                    for (ClientAuctionItem item : payload.items()) {
                        ClientAuctionItem.STREAM_CODEC.encode(buf, item);
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
