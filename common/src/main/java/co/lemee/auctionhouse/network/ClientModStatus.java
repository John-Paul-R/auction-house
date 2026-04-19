package co.lemee.auctionhouse.network;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side registry of which connected clients have the AuctionHouse mod installed.
 * Populated during the configuration-phase handshake; entries are removed on disconnect.
 */
public final class ClientModStatus {

    private static final ConcurrentHashMap<UUID, String> CLIENT_VERSIONS = new ConcurrentHashMap<>();

    private ClientModStatus() {}

    /** Record that a client has the mod, storing the version they reported. */
    public static void setClientVersion(UUID uuid, String version) {
        CLIENT_VERSIONS.put(uuid, version);
    }

    /** Remove a player's entry (call on disconnect). */
    public static void remove(UUID uuid) {
        CLIENT_VERSIONS.remove(uuid);
    }

    /** Returns {@code true} if the player's client has the mod. */
    public static boolean hasClientMod(UUID uuid) {
        return CLIENT_VERSIONS.containsKey(uuid);
    }

    /** Returns the mod version the client reported, if present. */
    public static Optional<String> getClientVersion(UUID uuid) {
        return Optional.ofNullable(CLIENT_VERSIONS.get(uuid));
    }
}
