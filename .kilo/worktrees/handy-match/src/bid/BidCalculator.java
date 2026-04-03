package bid;

import bid.diagnostics.BidDiagnostics;
import bid.pacing.PacingController;
import bid.policy.BidAdjustmentPolicy;
import bid.policy.BidSanitizer;
import bid.policy.TierBidPolicy;
import bid.scoring.ScoreCalculator;
import bid.state.BidRuntimeState;
import domain.Bid;
import domain.Video;
import domain.viewer.Viewer;
import helpers.DataParserResult;

import static bid.config.BidStrategyConfig.ENGAGEMENT_WEIGHT;
import static bid.config.BidStrategyConfig.MATCH_WEIGHT;
import static bid.config.BidStrategyConfig.SUB_WEIGHT;
import static bid.config.BidStrategyConfig.SYNERGY_WEIGHT;
import static bid.config.BidStrategyConfig.VIEW_WEIGHT;

public class BidCalculator {

    private static final BidRuntimeState STATE = new BidRuntimeState();

    public BidCalculator() {
    }

    public static Bid calculateBid(DataParserResult data) {
        if (data == null || data.video() == null || data.viewer() == null) {
            return new Bid(0, 0);
        }

        PacingController.refreshPacingControls(STATE);

        Video video = data.video();
        Viewer viewer = data.viewer();

        double matchScore = ScoreCalculator.calculateMatchScore(video, viewer);
        double synergyBonus = ScoreCalculator.calculateSynergyBonus(video, viewer);
        double subScore = ScoreCalculator.calculateSubscriptionScore(viewer);
        double engagementScore = ScoreCalculator.calculateEngagementScore(video);
        double viewScore = ScoreCalculator.calculateViewBucketScore(video.viewCount());
        double ageScore = ScoreCalculator.calculateAgeScore(viewer.age());
        double genderScore = ScoreCalculator.getGenderScore(viewer);

        double tailScore =
                MATCH_WEIGHT * matchScore
                        + SYNERGY_WEIGHT * synergyBonus
                        + SUB_WEIGHT * subScore
                        + ENGAGEMENT_WEIGHT * engagementScore
                        + VIEW_WEIGHT * viewScore
                        + ageScore
                        + genderScore;

        Bid tierBid = TierBidPolicy.toTierBid(tailScore, STATE.thresholdShift);
        int startBid = tierBid.startBid();
        int maxBid = tierBid.maxBid();

        Bid adjustedByBand = BidAdjustmentPolicy.applyBandBidAdjustments(
                STATE,
                startBid,
                maxBid,
                tailScore,
                matchScore
        );
        Bid qualityFilteredBid = BidAdjustmentPolicy.applyLagModeQualityFloor(
                STATE,
                adjustedByBand,
                tailScore,
                matchScore,
                engagementScore,
                viewer
        );
        Bid catchUpBid = BidAdjustmentPolicy.applyControlledCatchUpLane(
                STATE,
                qualityFilteredBid,
                tailScore,
                matchScore,
                engagementScore,
                viewer
        );
        Bid phaseBid = BidAdjustmentPolicy.applySpendPhasePolicy(
                STATE,
                catchUpBid,
                tailScore,
                matchScore,
                engagementScore,
                viewer
        );
        Bid lateCatchUpBid = BidAdjustmentPolicy.applyMinimalLateCatchUp(
                STATE,
                phaseBid,
                video,
                viewer,
                tailScore,
                matchScore
        );
        startBid = lateCatchUpBid.startBid();
        maxBid = lateCatchUpBid.maxBid();

        Bid premiumBid = BidAdjustmentPolicy.applyPremiumRoundBoost(
                STATE,
                startBid,
                maxBid,
                video,
                viewer,
                engagementScore
        );
        startBid = premiumBid.startBid();
        maxBid = premiumBid.maxBid();

        startBid = (int) Math.floor(startBid * STATE.paceMultiplier);
        maxBid = (int) Math.floor(maxBid * STATE.paceMultiplier);

        Bid safeBid = BidSanitizer.sanitizeBid(startBid, maxBid, STATE.remainingBudget);
        //BidDiagnostics.updateDecisionDiagnostics(STATE, tailScore, safeBid);
        PacingController.updatePacingState(STATE, safeBid);

        return safeBid;
    }

    public static void setInitialBudget(int newInitialBudget) {
        if (newInitialBudget <= 0) {
            return;
        }

        STATE.initialBudget = newInitialBudget;
        if (STATE.remainingBudget == Integer.MAX_VALUE) {
            STATE.remainingBudget = newInitialBudget;
        }
    }

    /**
     * Optional integration hook for budget-aware capping.
     * If caller does not provide this, bids are only bounded by internal safety caps.
     */
    public static void setRemainingBudget(int newRemainingBudget) {
        STATE.remainingBudget = Math.max(0, newRemainingBudget);
    }

    public static void onBidResult(boolean won, int ebucksSpent) {
        if (ebucksSpent <= 0) {
            return;
        }

        STATE.trackedSpent += ebucksSpent;
        STATE.blockSpentByResults += ebucksSpent;
        if (STATE.trackedSpent < 0) {
            STATE.trackedSpent = Integer.MAX_VALUE;
        }
    }

    public static void onSummary(int blockPoints, int blockSpent) {
        if (blockSpent > 0) {
            STATE.summarySpentTotal += blockSpent;
            if (STATE.summarySpentTotal > STATE.trackedSpent) {
                STATE.trackedSpent = STATE.summarySpentTotal;
            }
        }

        // Re-evaluate controls at summary boundaries where spend signal quality is highest.
        PacingController.refreshPacingControls(STATE);
        //BidDiagnostics.logPacingDiagnostics(STATE, blockPoints, blockSpent);
        //BidDiagnostics.resetBlockDiagnostics(STATE);
    }
}
