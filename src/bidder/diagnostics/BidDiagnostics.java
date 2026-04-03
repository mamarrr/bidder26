package bidder.diagnostics;

import static bidder.pacing.PacingController.floorBudget;
import static bidder.pacing.PacingController.targetSpentSoFar;

import bidder.state.BidRuntimeState;
import domain.Bid;

public final class BidDiagnostics {

    private BidDiagnostics() {
    }

    public static void updateDecisionDiagnostics(BidRuntimeState state, double tailScore, Bid decidedBid) {
        state.lastTailScore = tailScore;
        state.lastDecidedBid = decidedBid;

        state.blockTailScoreSum += tailScore;
        state.blockTailScoreCount++;

        if (decidedBid.maxBid() > 0) {
            state.blockBidMaxSum += decidedBid.maxBid();
            state.blockBidEnteredCount++;
        }
    }

    public static void logPacingDiagnostics(BidRuntimeState state, int blockPoints, int blockSpent) {
        double avgTail = state.blockTailScoreCount == 0
                ? 0.0
                : state.blockTailScoreSum / (double) state.blockTailScoreCount;
        double avgEnteredMax = state.blockBidEnteredCount == 0
                ? 0.0
                : (double) state.blockBidMaxSum / (double) state.blockBidEnteredCount;

        int targetSoFar = targetSpentSoFar(state);
        int spendLag = targetSoFar - state.trackedSpent;

        System.err.println(
                "Pacing diagnostics: "
                        + "phase=" + currentSpendPhase(state)
                        + ", "
                        + "band=" + state.paceBand
                        + ", lagRatio=" + format3(state.lastLagRatio)
                        + ", targetSpent=" + targetSoFar
                        + ", trackedSpent=" + state.trackedSpent
                        + ", spendLag=" + spendLag
                        + ", thresholdShift=" + format3(state.thresholdShift)
                        + ", paceMultiplier=" + format3(state.paceMultiplier)
                        + ", participation=" + format3(state.lastBlockParticipationRate)
                        + ", avgTail=" + format3(avgTail)
                        + ", avgEnteredMax=" + format3(avgEnteredMax)
                        + ", lowSpendBlocks=" + state.lowSpendCatchUpBlocks
                        + ", blockSpendSignal=" + state.lastEvaluatedBlockSpend
                        + ", lastTail=" + format3(state.lastTailScore)
                        + ", lastBid=" + state.lastDecidedBid.startBid() + " " + state.lastDecidedBid.maxBid()
                        + ", blockPoints=" + blockPoints
                        + ", blockSpent=" + blockSpent
        );
    }

    public static void resetBlockDiagnostics(BidRuntimeState state) {
        state.blockTailScoreSum = 0.0;
        state.blockTailScoreCount = 0;
        state.blockBidMaxSum = 0;
        state.blockBidEnteredCount = 0;
    }

    private static String format3(double value) {
        return String.valueOf(Math.round(value * 1000.0) / 1000.0);
    }

    private static String currentSpendPhase(BidRuntimeState state) {
        return state.trackedSpent < floorBudget(state) ? "PRE_FLOOR" : "POST_FLOOR";
    }
}
