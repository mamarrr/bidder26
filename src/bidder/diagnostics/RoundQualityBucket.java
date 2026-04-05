package bidder.diagnostics;

public enum RoundQualityBucket {
    PREMIUM,
    GOOD,
    MEDIOCRE,
    LOW;

    public int index() {
        return ordinal();
    }
}
