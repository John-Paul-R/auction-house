package co.lemee.auctionhouse.economy;

import java.util.UUID;

import static co.lemee.auctionhouse.AuctionHouseMod.LOGGER;

/**
 * Fallback used when neither Impactor nor RealEconomy is available.
 * All operations fail gracefully — the server admin is warned at startup.
 */
public class NoEconomyHandler extends EconomyHandler {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    protected boolean add(UUID accountUUID, double amount) {
        LOGGER.error("AuctionHouse: no economy plugin available — 'add' operation skipped");
        return false;
    }

    @Override
    protected boolean remove(UUID accountUUID, double amount) {
        LOGGER.error("AuctionHouse: no economy plugin available — 'remove' operation skipped");
        return false;
    }

    @Override
    public double getBalance(UUID accountUUID) {
        return 0.0;
    }
}
