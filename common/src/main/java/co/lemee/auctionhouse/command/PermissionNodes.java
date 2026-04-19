package co.lemee.auctionhouse.command;

public enum PermissionNodes {
    CANCEL,
    EXPIRED,
    HELP,
    MAIN,
    RETURN,
    RELOAD,
    SELL,
    SELLING;

    public String node() {
        return "auctionhouse." + name().toLowerCase();
    }
}
