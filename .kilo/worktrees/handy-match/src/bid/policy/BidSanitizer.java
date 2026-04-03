package bid.policy;

import static bid.config.BidStrategyConfig.MAX_REASONABLE_BID;

import domain.Bid;

public final class BidSanitizer {

    private BidSanitizer() {
    }

    public static Bid sanitizeBid(int startBid, int maxBid, int remainingBudget) {
        int safeStart = Math.max(0, startBid);
        int safeMax = Math.max(0, maxBid);

        if (safeStart > MAX_REASONABLE_BID) {
            safeStart = MAX_REASONABLE_BID;
        }
        if (safeMax > MAX_REASONABLE_BID) {
            safeMax = MAX_REASONABLE_BID;
        }

        if (safeStart > safeMax) {
            safeStart = safeMax;
        }

        if (remainingBudget <= 0) {
            return new Bid(0, 0);
        }

        if (safeMax > remainingBudget) {
            safeMax = remainingBudget;
        }
        if (safeStart > safeMax) {
            safeStart = safeMax;
        }

        return new Bid(safeStart, safeMax);
    }
}
