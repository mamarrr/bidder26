package bidder;

import static bidder.config.BidStrategyConfig.ENGAGEMENT_WEIGHT;
import static bidder.config.BidStrategyConfig.MATCH_WEIGHT;
import static bidder.config.BidStrategyConfig.SUB_WEIGHT;
import static bidder.config.BidStrategyConfig.SYNERGY_WEIGHT;
import static bidder.config.BidStrategyConfig.VIEW_WEIGHT;

import bidder.diagnostics.BidDiagnostics;
import bidder.pacing.PacingController;
import bidder.policy.BidAdjustmentPolicy;
import bidder.policy.BidSanitizer;
import bidder.policy.TierBidPolicy;
import bidder.scoring.ScoreCalculator;
import bidder.state.BidRuntimeState;
import domain.Bid;
import domain.Video;
import domain.viewer.Viewer;
import helpers.DataParserResult;

public class BidCalculator {

    private static BidRuntimeState STATE;

    public BidCalculator(int initialBudget) {
        STATE = new BidRuntimeState(initialBudget);
    }

    public Bid calculateBid(DataParserResult data) {
        if (data == null || data.video() == null || data.viewer() == null) {
            Bid noBid = new Bid(0, 0);
            STATE.lastDecidedBid = noBid;
            return noBid;
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

        double totalScore =
                MATCH_WEIGHT * matchScore
                        + SYNERGY_WEIGHT * synergyBonus
                        + SUB_WEIGHT * subScore
                        + ENGAGEMENT_WEIGHT * engagementScore
                        + VIEW_WEIGHT * viewScore
                        + ageScore
                        + genderScore;

        Bid tierBid = TierBidPolicy.toTierBid(totalScore, STATE.thresholdShift);
        int startBid = tierBid.startBid();
        int maxBid = tierBid.maxBid();

        Bid adjustedByBand = BidAdjustmentPolicy.applyBandBidAdjustments(
                STATE,
                startBid,
                maxBid,
                totalScore,
                matchScore
        );
        Bid qualityFilteredBid = BidAdjustmentPolicy.applyLagModeQualityFloor(
                STATE,
                adjustedByBand,
                totalScore,
                matchScore,
                engagementScore,
                viewer
        );
        Bid catchUpBid = BidAdjustmentPolicy.applyControlledCatchUpLane(
                STATE,
                qualityFilteredBid,
                totalScore,
                matchScore,
                engagementScore,
                viewer
        );
        Bid phaseBid = BidAdjustmentPolicy.applySpendPhasePolicy(
                STATE,
                catchUpBid,
                totalScore,
                matchScore,
                engagementScore,
                viewer
        );
        Bid lateCatchUpBid = BidAdjustmentPolicy.applyMinimalLateCatchUp(
                STATE,
                phaseBid,
                video,
                viewer,
                totalScore,
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

        Bid competitionPressureBid = BidAdjustmentPolicy.applyCompetitionPressureBoost(
                STATE,
                new Bid(startBid, maxBid),
                totalScore,
                matchScore,
                engagementScore,
                video,
                viewer
        );
        startBid = competitionPressureBid.startBid();
        maxBid = competitionPressureBid.maxBid();

        Bid highQualityRecoveryBid = BidAdjustmentPolicy.applyHighQualityOutbidRecovery(
                STATE,
                new Bid(startBid, maxBid),
                totalScore,
                matchScore,
                engagementScore,
                video,
                viewer
        );
        startBid = highQualityRecoveryBid.startBid();
        maxBid = highQualityRecoveryBid.maxBid();

        startBid = (int) Math.floor(startBid * STATE.paceMultiplier);
        maxBid = (int) Math.floor(maxBid * STATE.paceMultiplier);

        Bid safeBid = BidSanitizer.sanitizeBid(startBid, maxBid, STATE.remainingBudget);
        STATE.lastDecidedBid = safeBid;
        BidDiagnostics.updateDecisionDiagnostics(
                STATE,
                totalScore,
                matchScore,
                engagementScore,
                video,
                viewer,
                safeBid
        );
        PacingController.updatePacingState(STATE, safeBid);

        return safeBid;
    }

    /**
     * Optional integration hook for budget-aware capping.
     * If caller does not provide this, bids are only bounded by internal safety caps.
     */
    public void setRemainingBudget(int newRemainingBudget) {
        STATE.remainingBudget = Math.max(0, newRemainingBudget);
    }

    public void onBidResult(boolean won, int ebucksSpent) {
        BidDiagnostics.updateResultDiagnostics(STATE, won);

        if (STATE.lastDecidedBid.maxBid() > 0) {
            STATE.blockEnteredWithBid++;
            if (won) {
                STATE.blockWins++;
                if (ebucksSpent > 0) {
                    STATE.blockWinCostSum += ebucksSpent;
                    STATE.blockWinCostCount++;
                }
            } else {
                STATE.blockLossesWithBid++;
            }
        }

        if (ebucksSpent > 0) {
            STATE.trackedSpent += ebucksSpent;
            STATE.blockSpentByResults += ebucksSpent;
            if (STATE.trackedSpent < 0) {
                STATE.trackedSpent = Integer.MAX_VALUE;
            }
        }
    }

    public void onSummary(int blockPoints, int blockSpent) {
        if (blockSpent > 0) {
            STATE.summarySpentTotal += blockSpent;
            if (STATE.summarySpentTotal > STATE.trackedSpent) {
                STATE.trackedSpent = STATE.summarySpentTotal;
            }
        }

        // Re-evaluate controls at summary boundaries where spend signal quality is highest.
        PacingController.refreshPacingControls(STATE);
        BidDiagnostics.logPacingDiagnostics(STATE, blockPoints, blockSpent);
        BidDiagnostics.resetBlockDiagnostics(STATE);
    }
}
