package bidder.policy;

import domain.Bid;

public final class TierBidPolicy {

    private TierBidPolicy() {
    }

    public static Bid toTierBid(double tailScore, double thresholdShiftValue) {
        // Positive shift => stricter selection, negative => looser selection.
        double adjustedScore = tailScore - thresholdShiftValue;

        if (adjustedScore < 3.9) {
            return new Bid(0, 0);
        }
        if (adjustedScore < 5.0) {
            return new Bid(1, 4);
        }
        if (adjustedScore < 6.1) {
            return new Bid(3, 9);
        }
        if (adjustedScore < 7.2) {
            return new Bid(6, 16);
        }
        if (adjustedScore < 8.4) {
            return new Bid(11, 26);
        }

        return new Bid(18, 42);
    }
}
