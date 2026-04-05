package bidder.diagnostics;

import static bidder.config.BidStrategyConfig.CHOSEN_CATEGORY;
import static bidder.config.BidStrategyConfig.HQ_OUTBID_MAX_WIN_RATE;
import static bidder.config.BidStrategyConfig.HQ_OUTBID_MIN_ENTERED;
import static bidder.config.BidStrategyConfig.HQ_OUTBID_MIN_GOOD_ENTRY_RATE;
import static bidder.config.BidStrategyConfig.MATCH_VIDEO_WEIGHT;
import static bidder.pacing.PacingController.floorBudget;
import static bidder.pacing.PacingController.targetSpentSoFar;

import bidder.state.BidRuntimeState;
import domain.Bid;
import domain.Category;
import domain.Video;
import domain.viewer.Viewer;
import domain.viewer.ViewerInterests;
import domain.viewer.ViewerSubscribed;

public final class BidDiagnostics {

    private BidDiagnostics() {
    }

    public static void updateDecisionDiagnostics(
            BidRuntimeState state,
            double tailScore,
            double matchScore,
            double engagementScore,
            Video video,
            Viewer viewer,
            Bid decidedBid
    ) {
        state.lastTailScore = tailScore;
        state.lastDecidedBid = decidedBid;

        RoundQualityBucket bucket = classifyBucket(tailScore, matchScore, engagementScore, video, viewer);
        int bucketIndex = bucket.index();
        state.diagnosticsBlockRoundsByBucket[bucketIndex]++;
        state.diagnosticsLastRoundBucket = bucket;

        state.blockTailScoreSum += tailScore;
        state.blockTailScoreCount++;

        if (decidedBid.maxBid() > 0) {
            state.blockBidMaxSum += decidedBid.maxBid();
            state.blockBidEnteredCount++;
            state.diagnosticsLastRoundEntered = true;
            state.diagnosticsBlockEnteredByBucket[bucketIndex]++;
            state.diagnosticsBlockEnteredMaxSumByBucket[bucketIndex] += decidedBid.maxBid();
        } else {
            state.diagnosticsLastRoundEntered = false;
        }
    }

    public static void updateResultDiagnostics(BidRuntimeState state, boolean won) {
        if (!state.diagnosticsLastRoundEntered) {
            return;
        }

        int bucketIndex = state.diagnosticsLastRoundBucket.index();
        if (won) {
            state.diagnosticsBlockWinsByBucket[bucketIndex]++;
        } else {
            state.diagnosticsBlockLossesByBucket[bucketIndex]++;
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

        String premiumBreakdown = bucketBreakdown(state, RoundQualityBucket.PREMIUM);
        String goodBreakdown = bucketBreakdown(state, RoundQualityBucket.GOOD);
        String mediocreBreakdown = bucketBreakdown(state, RoundQualityBucket.MEDIOCRE);
        String lowBreakdown = bucketBreakdown(state, RoundQualityBucket.LOW);

        int goodRounds = state.diagnosticsBlockRoundsByBucket[RoundQualityBucket.GOOD.index()];
        int goodEntered = state.diagnosticsBlockEnteredByBucket[RoundQualityBucket.GOOD.index()];
        int mediocreRounds = state.diagnosticsBlockRoundsByBucket[RoundQualityBucket.MEDIOCRE.index()];
        int mediocreEntered = state.diagnosticsBlockEnteredByBucket[RoundQualityBucket.MEDIOCRE.index()];

        double goodEntryRate = safeRatio(goodEntered, goodRounds);
        double mediocreEntryRate = safeRatio(mediocreEntered, mediocreRounds);
        String blockSignal = classifyBlockSignal(state, goodEntryRate, mediocreEntryRate, mediocreRounds);

        System.err.println(
                "Pacing diagnostics: "
                        + "phase=" + currentSpendPhase(state)
                        + ", "
                        + "pressure=" + state.crowdingPressure
                        + ", hqEntered=" + state.lastBlockHighQualityEntered
                        + ", hqWins=" + state.lastBlockHighQualityWins
                        + ", hqWinRate=" + format3(state.lastBlockHighQualityWinRate)
                        + ", goodEntryRate=" + format3(goodEntryRate)
                        + ", goodWinRate=" + format3(state.lastBlockGoodWinRate)
                        + ", mediocreEntryRate=" + format3(mediocreEntryRate)
                        + ", hqOutbidStage=" + state.highQualityOutbidStage
                        + ", consecutiveHqOutbidBlocks=" + state.consecutiveHighQualityOutbidBlocks
                        + ", hqRecoveryStreakBlocks=" + state.highQualityRecoveryStreakBlocks
                        + ", hqRecoveryStreakHasStrong=" + state.highQualityRecoveryStreakHasStrong
                        + ", hqRecoveryActive=" + state.highQualityRecoveryBoostActive
                        + ", hqProbeCounter=" + state.highQualityRecoveryProbeCounter
                        + ", hqStagnationOverride=" + state.highQualityStagnationOverrideActive
                        + ", hqStagnationRounds=" + state.highQualityStagnationRounds
                        + ", blockSignal=" + blockSignal
                        + ", entered=" + state.lastBlockEnteredWithBid
                        + ", wins=" + state.lastBlockWins
                        + ", losses=" + state.lastBlockLossesWithBid
                        + ", winRate=" + format3(state.lastBlockWinRate)
                        + ", avgWinCost=" + format3(state.lastBlockAvgWinCost)
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
                        + ", qualityBuckets=["
                        + premiumBreakdown + "; "
                        + goodBreakdown + "; "
                        + mediocreBreakdown + "; "
                        + lowBreakdown + "]"
                        + ", blockPoints=" + blockPoints
                        + ", blockSpent=" + blockSpent
        );
    }

    public static void resetBlockDiagnostics(BidRuntimeState state) {
        state.blockTailScoreSum = 0.0;
        state.blockTailScoreCount = 0;
        state.blockBidMaxSum = 0;
        state.blockBidEnteredCount = 0;
        state.diagnosticsLastRoundEntered = false;
        state.highQualityRecoveryBoostActive = false;
        state.highQualityStagnationOverrideActive = false;
        state.highQualityStagnationRounds = 0;

        for (int i = 0; i < state.diagnosticsBlockRoundsByBucket.length; i++) {
            state.diagnosticsBlockRoundsByBucket[i] = 0;
            state.diagnosticsBlockEnteredByBucket[i] = 0;
            state.diagnosticsBlockWinsByBucket[i] = 0;
            state.diagnosticsBlockLossesByBucket[i] = 0;
            state.diagnosticsBlockEnteredMaxSumByBucket[i] = 0;
        }
    }

    private static String format3(double value) {
        return String.valueOf(Math.round(value * 1000.0) / 1000.0);
    }

    private static String currentSpendPhase(BidRuntimeState state) {
        return state.trackedSpent < floorBudget(state) ? "PRE_FLOOR" : "POST_FLOOR";
    }

    private static String bucketBreakdown(BidRuntimeState state, RoundQualityBucket bucket) {
        int index = bucket.index();
        int rounds = state.diagnosticsBlockRoundsByBucket[index];
        int entered = state.diagnosticsBlockEnteredByBucket[index];
        int wins = state.diagnosticsBlockWinsByBucket[index];
        int losses = state.diagnosticsBlockLossesByBucket[index];
        int noBid = Math.max(0, rounds - entered);
        int enteredMaxSum = state.diagnosticsBlockEnteredMaxSumByBucket[index];

        double entryRate = safeRatio(entered, rounds);
        double winRate = safeRatio(wins, entered);
        double avgEnteredMax = entered == 0 ? 0.0 : (double) enteredMaxSum / (double) entered;

        return bucket.name()
                + "{r=" + rounds
                + ",e=" + entered
                + ",noBid=" + noBid
                + ",entryRate=" + format3(entryRate)
                + ",winRate=" + format3(winRate)
                + ",losses=" + losses
                + ",avgMax=" + format3(avgEnteredMax)
                + "}";
    }

    private static RoundQualityBucket classifyBucket(
            double tailScore,
            double matchScore,
            double engagementScore,
            Video video,
            Viewer viewer
    ) {
        if (isPremiumRound(video, viewer, engagementScore)) {
            return RoundQualityBucket.PREMIUM;
        }

        boolean goodRound = tailScore >= 6.4
                || (matchScore >= MATCH_VIDEO_WEIGHT && engagementScore >= 1.0);
        if (goodRound) {
            return RoundQualityBucket.GOOD;
        }

        boolean mediocreRound = tailScore >= 4.7
                || engagementScore >= 0.9
                || matchScore >= 0.28;
        if (mediocreRound) {
            return RoundQualityBucket.MEDIOCRE;
        }

        return RoundQualityBucket.LOW;
    }

    private static boolean isPremiumRound(Video video, Viewer viewer, double engagementScore) {
        if (video == null || viewer == null || viewer.subscribed() != ViewerSubscribed.Y) {
            return false;
        }

        Category[] interests = extractInterests(viewer.interests());
        boolean topInterestMatch = interests.length > 0 && interests[0] == CHOSEN_CATEGORY;

        return video.category() == CHOSEN_CATEGORY
                && topInterestMatch
                && engagementScore >= 1.2;
    }

    private static Category[] extractInterests(ViewerInterests interests) {
        if (interests == null || interests.interests() == null) {
            return new Category[0];
        }
        return interests.interests();
    }

    private static String classifyBlockSignal(
            BidRuntimeState state,
            double goodEntryRate,
            double mediocreEntryRate,
            int mediocreRounds
    ) {
        if (state.highQualityStagnationOverrideActive) {
            return "HQ_STAGNATION_OVERRIDE";
        }

        boolean highQualityOutbidSignal = state.lastBlockHighQualityEntered >= HQ_OUTBID_MIN_ENTERED
                && state.lastBlockHighQualityWinRate < HQ_OUTBID_MAX_WIN_RATE
                && goodEntryRate >= HQ_OUTBID_MIN_GOOD_ENTRY_RATE;
        if (state.highQualityOutbidStage > 0 || highQualityOutbidSignal) {
            return "HQ_OUTBID_ACTIVE";
        }
        if (mediocreRounds >= 12 && mediocreEntryRate <= 0.32) {
            return "SELECTION_LIMITER_MEDIOCRE";
        }
        return "MIXED";
    }

    private static double safeRatio(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return (double) numerator / (double) denominator;
    }
}
