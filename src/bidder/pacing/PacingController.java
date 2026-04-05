package bidder.pacing;

import static bidder.config.BidStrategyConfig.CONTROL_STEP;
import static bidder.config.BidStrategyConfig.EARLY_RED_HIGH_PRESSURE_LAG_RATIO;
import static bidder.config.BidStrategyConfig.EARLY_RED_ROUND_GUARD;
import static bidder.config.BidStrategyConfig.FLOOR_SPEND_RATIO;
import static bidder.config.BidStrategyConfig.GREEN_LAG_RATIO;
import static bidder.config.BidStrategyConfig.HQ_OUTBID_EXTRA_TARGET_PACE;
import static bidder.config.BidStrategyConfig.HQ_OUTBID_EXTRA_TARGET_SHIFT;
import static bidder.config.BidStrategyConfig.HQ_OUTBID_MAX_WIN_RATE;
import static bidder.config.BidStrategyConfig.HQ_OUTBID_MIN_ENTERED;
import static bidder.config.BidStrategyConfig.HQ_OUTBID_MIN_GOOD_ENTRY_RATE;
import static bidder.config.BidStrategyConfig.HQ_OUTBID_MIN_STAGE_FOR_EXTRA_PACE;
import static bidder.config.BidStrategyConfig.HQ_OUTBID_MIN_STAGE_FOR_ORANGE_BAND;
import static bidder.config.BidStrategyConfig.HQ_OUTBID_RECOVERY_STREAK_BLOCKS_TO_RESET;
import static bidder.config.BidStrategyConfig.HQ_OUTBID_RECOVERY_WIN_RATE;
import static bidder.config.BidStrategyConfig.HQ_OUTBID_SEVERE_ZERO_WIN_MIN_ENTERED;
import static bidder.config.BidStrategyConfig.HQ_OUTBID_STAGE_CAP;
import static bidder.config.BidStrategyConfig.HQ_OUTBID_STRONG_RECOVERY_WIN_RATE;
import static bidder.config.BidStrategyConfig.INTERNAL_TARGET_BUFFER_RATIO;
import static bidder.config.BidStrategyConfig.NEAR_FLOOR_STABILITY_RATIO;
import static bidder.config.BidStrategyConfig.ORANGE_LAG_RATIO;
import static bidder.config.BidStrategyConfig.RED_EXIT_SPEND_LAG_RATIO_OF_FLOOR;
import static bidder.config.BidStrategyConfig.TARGET_ROUNDS_TO_HIT_FLOOR;
import static bidder.config.BidStrategyConfig.TARGET_SPEND_LOWER_GUARD_RATIO;
import static bidder.config.BidStrategyConfig.TARGET_SPEND_UPPER_GUARD_RATIO;
import static bidder.config.BidStrategyConfig.YELLOW_EXIT_SPEND_LAG_RATIO_OF_FLOOR;
import static bidder.config.BidStrategyConfig.YELLOW_LAG_RATIO;

import bidder.diagnostics.RoundQualityBucket;
import bidder.state.BidRuntimeState;
import domain.Bid;

public final class PacingController {

    private PacingController() {
    }

    public static void updatePacingState(BidRuntimeState state, Bid decidedBid) {
        state.roundsSeen++;
        state.blockRounds++;
        if (decidedBid.maxBid() > 0) {
            state.blockEnteredRounds++;
        }

        if (state.blockRounds >= 100) {
            evaluateBlockPacing(state);
        }
    }

    public static void refreshPacingControls(BidRuntimeState state) {
        int floorBudget = floorBudget(state);
        if (floorBudget <= 0) {
            return;
        }

        int targetBudget = internalTargetBudget(state);
        int targetSoFar = targetSpentSoFar(state);
        int spendLag = targetSoFar - state.trackedSpent;
        int redExitLag = (int) Math.ceil((double) floorBudget * RED_EXIT_SPEND_LAG_RATIO_OF_FLOOR);
        int yellowExitLag = (int) Math.ceil((double) floorBudget * YELLOW_EXIT_SPEND_LAG_RATIO_OF_FLOOR);
        double lagRatio = (double) spendLag / (double) Math.max(1, targetBudget);
        state.lastLagRatio = lagRatio;
        int nearFloorBudget = (int) Math.ceil((double) floorBudget * NEAR_FLOOR_STABILITY_RATIO);
        boolean preFloor = state.trackedSpent < floorBudget;

        if (state.trackedSpent >= floorBudget) {
            state.paceBand = PaceBand.GREEN;
        } else if (lagRatio <= GREEN_LAG_RATIO && spendLag <= Math.max(30_000, yellowExitLag / 2)) {
            state.paceBand = PaceBand.GREEN;
        } else if (lagRatio <= YELLOW_LAG_RATIO && spendLag <= yellowExitLag) {
            state.paceBand = PaceBand.YELLOW;
        } else if (lagRatio <= ORANGE_LAG_RATIO && spendLag <= redExitLag) {
            state.paceBand = PaceBand.ORANGE;
        } else {
            state.paceBand = PaceBand.RED;
        }

        if (state.roundsSeen < EARLY_RED_ROUND_GUARD && state.paceBand == PaceBand.RED) {
            boolean allowEarlyRed = state.crowdingPressure == CrowdingPressure.HIGH
                    && lagRatio >= EARLY_RED_HIGH_PRESSURE_LAG_RATIO;
            if (!allowEarlyRed) {
                state.paceBand = PaceBand.ORANGE;
            }
        }

        if (state.trackedSpent >= nearFloorBudget && state.paceBand == PaceBand.RED) {
            state.paceBand = PaceBand.ORANGE;
        }
        if (state.trackedSpent >= nearFloorBudget
                && state.paceBand == PaceBand.ORANGE
                && lagRatio <= ORANGE_LAG_RATIO) {
            state.paceBand = PaceBand.YELLOW;
        }

        if (state.consecutiveLagBlocks >= 4 && state.paceBand != PaceBand.RED) {
            state.paceBand = increaseBand(state.paceBand);
        }

        if (preFloor
                && state.highQualityOutbidStage >= HQ_OUTBID_MIN_STAGE_FOR_ORANGE_BAND
                && state.paceBand.ordinal() < PaceBand.ORANGE.ordinal()) {
            state.paceBand = PaceBand.ORANGE;
        }

        double targetShift;
        double targetPace;

        switch (state.paceBand) {
            case GREEN:
                targetShift = 0.08;
                targetPace = 0.98;
                break;
            case YELLOW:
                targetShift = -0.05;
                targetPace = 1.05;
                break;
            case ORANGE:
                targetShift = -0.22;
                targetPace = 1.17;
                break;
            case RED:
                targetShift = -0.33;
                targetPace = 1.25;
                break;
            default:
                targetShift = 0.0;
                targetPace = 1.0;
                break;
        }

        if (state.paceBand == PaceBand.RED && preFloor && state.lowSpendCatchUpBlocks >= 2) {
            targetShift -= 0.05;
            targetPace += 0.04;
        }
        if (state.paceBand == PaceBand.RED && preFloor && state.lowSpendCatchUpBlocks >= 4) {
            targetShift -= 0.04;
            targetPace += 0.03;
        }
        if (state.paceBand == PaceBand.ORANGE && preFloor && spendLag > yellowExitLag) {
            targetShift -= 0.03;
            targetPace += 0.02;
        }

        if (preFloor && state.highQualityOutbidStage >= HQ_OUTBID_MIN_STAGE_FOR_EXTRA_PACE) {
            targetShift -= HQ_OUTBID_EXTRA_TARGET_SHIFT;
            targetPace += HQ_OUTBID_EXTRA_TARGET_PACE;
        }

        state.thresholdShift = approach(state.thresholdShift, targetShift, CONTROL_STEP);
        state.paceMultiplier = approach(state.paceMultiplier, targetPace, CONTROL_STEP);

        state.thresholdShift = Math.max(-0.66, Math.min(0.65, state.thresholdShift));
        state.paceMultiplier = Math.max(0.92, Math.min(1.34, state.paceMultiplier));

        int lowerGuardSpend = (int) Math.ceil((double) state.initialBudget * TARGET_SPEND_LOWER_GUARD_RATIO);
        int upperGuardSpend = (int) Math.ceil((double) state.initialBudget * TARGET_SPEND_UPPER_GUARD_RATIO);

        if (state.trackedSpent >= lowerGuardSpend && state.trackedSpent < upperGuardSpend) {
            state.thresholdShift = Math.max(0.06, state.thresholdShift);
            state.paceMultiplier = Math.min(state.paceMultiplier, 0.99);
        }

        if (state.trackedSpent >= upperGuardSpend) {
            state.paceBand = PaceBand.GREEN;
            state.consecutiveLagBlocks = 0;
            state.lowSpendCatchUpBlocks = 0;
            state.thresholdShift = Math.max(0.12, state.thresholdShift);
            state.paceMultiplier = Math.min(state.paceMultiplier, 0.95);
        }

        if (state.trackedSpent >= floorBudget) {
            state.paceBand = PaceBand.GREEN;
            state.consecutiveLagBlocks = 0;
            state.lowSpendCatchUpBlocks = 0;

            state.thresholdShift = approach(state.thresholdShift, 0.10, CONTROL_STEP + 0.01);
            state.paceMultiplier = approach(state.paceMultiplier, 0.97, CONTROL_STEP + 0.01);

            state.thresholdShift = Math.max(0.0, state.thresholdShift);
            state.paceMultiplier = Math.max(0.92, Math.min(1.00, state.paceMultiplier));
        }
    }

    private static void evaluateBlockPacing(BidRuntimeState state) {
        int enteredWithBid = state.blockEnteredWithBid;
        int wins = state.blockWins;
        int lossesWithBid = state.blockLossesWithBid;
        double winRate = enteredWithBid <= 0 ? 0.0 : (double) wins / (double) enteredWithBid;
        double avgWinCost = state.blockWinCostCount <= 0
                ? 0.0
                : (double) state.blockWinCostSum / (double) state.blockWinCostCount;

        state.lastBlockEnteredWithBid = enteredWithBid;
        state.lastBlockWins = wins;
        state.lastBlockLossesWithBid = lossesWithBid;
        state.lastBlockWinRate = winRate;
        state.lastBlockAvgWinCost = avgWinCost;
        state.crowdingPressure = classifyCrowdingPressure(enteredWithBid, winRate);

        int premiumIndex = RoundQualityBucket.PREMIUM.index();
        int goodIndex = RoundQualityBucket.GOOD.index();
        int mediocreIndex = RoundQualityBucket.MEDIOCRE.index();

        int premiumEntered = state.diagnosticsBlockEnteredByBucket[premiumIndex];
        int premiumWins = state.diagnosticsBlockWinsByBucket[premiumIndex];
        int goodRounds = state.diagnosticsBlockRoundsByBucket[goodIndex];
        int goodEntered = state.diagnosticsBlockEnteredByBucket[goodIndex];
        int goodWins = state.diagnosticsBlockWinsByBucket[goodIndex];
        int mediocreRounds = state.diagnosticsBlockRoundsByBucket[mediocreIndex];
        int mediocreEntered = state.diagnosticsBlockEnteredByBucket[mediocreIndex];

        int highQualityEntered = premiumEntered + goodEntered;
        int highQualityWins = premiumWins + goodWins;
        double highQualityWinRate = safeRatio(highQualityWins, highQualityEntered);
        double goodEntryRate = safeRatio(goodEntered, goodRounds);
        double goodWinRate = safeRatio(goodWins, goodEntered);
        double mediocreEntryRate = safeRatio(mediocreEntered, mediocreRounds);

        state.lastBlockHighQualityEntered = highQualityEntered;
        state.lastBlockHighQualityWins = highQualityWins;
        state.lastBlockHighQualityWinRate = highQualityWinRate;
        state.lastBlockGoodEntered = goodEntered;
        state.lastBlockGoodWinRate = goodWinRate;
        state.lastBlockMediocreEntryRate = mediocreEntryRate;

        updateHighQualityOutbidStage(state, highQualityEntered, highQualityWins, highQualityWinRate, goodEntryRate);

        double participationRate = (double) state.blockEnteredRounds / 100.0;
        state.lastBlockParticipationRate = participationRate;

        int targetSoFar = targetSpentSoFar(state);
        int lag = targetSoFar - state.trackedSpent;
        double lagRatio = (double) lag / (double) Math.max(1, internalTargetBudget(state));
        int nearFloorBudget = (int) Math.ceil((double) floorBudget(state) * 0.95);
        int desiredBlockSpend = desiredRedBlockSpend(lagRatio);

        if (state.trackedSpent < floorBudget(state) && lagRatio > 0.14 && participationRate < 0.14) {
            state.consecutiveLagBlocks++;
        } else if (lagRatio <= YELLOW_LAG_RATIO || participationRate > 0.24) {
            state.consecutiveLagBlocks = Math.max(0, state.consecutiveLagBlocks - 1);
        }

        if (state.trackedSpent < floorBudget(state) && lagRatio > ORANGE_LAG_RATIO
                && state.blockSpentByResults < desiredBlockSpend) {
            state.lowSpendCatchUpBlocks++;
        } else if (lagRatio <= YELLOW_LAG_RATIO || state.blockSpentByResults >= desiredBlockSpend) {
            state.lowSpendCatchUpBlocks = Math.max(0, state.lowSpendCatchUpBlocks - 1);
        }

        if (state.trackedSpent >= nearFloorBudget) {
            state.consecutiveLagBlocks = Math.max(0, state.consecutiveLagBlocks - 2);
            state.lowSpendCatchUpBlocks = Math.max(0, state.lowSpendCatchUpBlocks - 2);
        }

        state.lowSpendCatchUpBlocks = Math.max(0, Math.min(12, state.lowSpendCatchUpBlocks));

        state.blockRounds = 0;
        state.blockEnteredRounds = 0;
        state.blockEnteredWithBid = 0;
        state.blockWins = 0;
        state.blockLossesWithBid = 0;
        state.blockWinCostSum = 0;
        state.blockWinCostCount = 0;
        state.lastEvaluatedBlockSpend = state.blockSpentByResults;
        state.blockSpentByResults = 0;

        refreshPacingControls(state);
    }

    private static int desiredRedBlockSpend(double lagRatio) {
        if (lagRatio >= 0.75) {
            return 320;
        }
        if (lagRatio >= 0.55) {
            return 285;
        }
        if (lagRatio >= 0.40) {
            return 245;
        }
        if (lagRatio >= ORANGE_LAG_RATIO) {
            return 210;
        }
        return 175;
    }

    private static CrowdingPressure classifyCrowdingPressure(int entered, double winRate) {
        if (entered < 15 || winRate >= 0.18) {
            return CrowdingPressure.LOW;
        }
        if (winRate >= 0.08) {
            return CrowdingPressure.MEDIUM;
        }
        return CrowdingPressure.HIGH;
    }

    private static void updateHighQualityOutbidStage(
            BidRuntimeState state,
            int highQualityEntered,
            int highQualityWins,
            double highQualityWinRate,
            double goodEntryRate
    ) {
        boolean outbidTrigger = highQualityEntered >= HQ_OUTBID_MIN_ENTERED
                && highQualityWinRate < HQ_OUTBID_MAX_WIN_RATE
                && goodEntryRate >= HQ_OUTBID_MIN_GOOD_ENTRY_RATE;

        if (outbidTrigger) {
            state.consecutiveHighQualityOutbidBlocks++;
            state.highQualityRecoveryStreakBlocks = 0;
            state.highQualityRecoveryStreakHasStrong = false;

            int stageIncrease = 1;
            boolean severeOutbid = highQualityWins == 0
                    && highQualityEntered >= HQ_OUTBID_SEVERE_ZERO_WIN_MIN_ENTERED;
            if (severeOutbid) {
                stageIncrease++;
            }

            state.highQualityOutbidStage = Math.min(
                    HQ_OUTBID_STAGE_CAP,
                    state.highQualityOutbidStage + stageIncrease
            );
            return;
        }

        state.consecutiveHighQualityOutbidBlocks = 0;

        boolean strongRecoveryBlock = highQualityWinRate >= HQ_OUTBID_STRONG_RECOVERY_WIN_RATE;
        boolean recoveryBlock = highQualityWinRate >= HQ_OUTBID_RECOVERY_WIN_RATE;

        if (!recoveryBlock) {
            state.highQualityRecoveryStreakBlocks = 0;
            state.highQualityRecoveryStreakHasStrong = false;
            return;
        }

        state.highQualityRecoveryStreakBlocks++;
        if (strongRecoveryBlock) {
            state.highQualityRecoveryStreakHasStrong = true;
        }

        boolean resetStage = state.highQualityRecoveryStreakHasStrong
                && state.highQualityRecoveryStreakBlocks >= HQ_OUTBID_RECOVERY_STREAK_BLOCKS_TO_RESET;
        if (resetStage) {
            state.highQualityOutbidStage = 0;
            state.highQualityRecoveryProbeCounter = 0;
            state.highQualityRecoveryStreakBlocks = 0;
            state.highQualityRecoveryStreakHasStrong = false;
            return;
        }

        state.highQualityOutbidStage = Math.max(0, state.highQualityOutbidStage - 1);
        if (state.highQualityOutbidStage == 0) {
            state.highQualityRecoveryProbeCounter = 0;
            state.highQualityRecoveryStreakBlocks = 0;
            state.highQualityRecoveryStreakHasStrong = false;
        }
    }

    private static PaceBand increaseBand(PaceBand band) {
        if (band == PaceBand.GREEN) {
            return PaceBand.YELLOW;
        }
        if (band == PaceBand.YELLOW) {
            return PaceBand.ORANGE;
        }
        return PaceBand.RED;
    }

    public static int floorBudget(BidRuntimeState state) {
        return (int) Math.ceil((double) state.initialBudget * FLOOR_SPEND_RATIO);
    }

    public static int internalTargetBudget(BidRuntimeState state) {
        return (int) Math.ceil((double) floorBudget(state) * INTERNAL_TARGET_BUFFER_RATIO);
    }

    public static int targetSpentSoFar(BidRuntimeState state) {
        int targetBudget = internalTargetBudget(state);
        if (targetBudget <= 0) {
            return 0;
        }

        double progress = Math.min(1.0, (double) state.roundsSeen / (double) TARGET_ROUNDS_TO_HIT_FLOOR);
        double smoothProgress = progress * progress * (3.0 - (2.0 * progress));
        // Blended curve starts pressure earlier than smoothstep-only, but avoids violent late catch-up spikes.
        double blendedProgress = (0.65 * progress) + (0.35 * smoothProgress);

        return (int) Math.round(targetBudget * blendedProgress);
    }

    private static double approach(double current, double target, double step) {
        if (current < target) {
            return Math.min(target, current + step);
        }
        if (current > target) {
            return Math.max(target, current - step);
        }
        return current;
    }

    private static double safeRatio(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return (double) numerator / (double) denominator;
    }
}
