package bidder.pacing;

import static bidder.config.BidStrategyConfig.CONTROL_STEP;
import static bidder.config.BidStrategyConfig.EARLY_RED_ROUND_GUARD;
import static bidder.config.BidStrategyConfig.FLOOR_SPEND_RATIO;
import static bidder.config.BidStrategyConfig.GREEN_LAG_RATIO;
import static bidder.config.BidStrategyConfig.INTERNAL_TARGET_BUFFER_RATIO;
import static bidder.config.BidStrategyConfig.NEAR_FLOOR_STABILITY_RATIO;
import static bidder.config.BidStrategyConfig.ORANGE_LAG_RATIO;
import static bidder.config.BidStrategyConfig.RED_EXIT_SPEND_LAG_RATIO_OF_FLOOR;
import static bidder.config.BidStrategyConfig.TARGET_ROUNDS_TO_HIT_FLOOR;
import static bidder.config.BidStrategyConfig.TARGET_SPEND_LOWER_GUARD_RATIO;
import static bidder.config.BidStrategyConfig.TARGET_SPEND_UPPER_GUARD_RATIO;
import static bidder.config.BidStrategyConfig.YELLOW_EXIT_SPEND_LAG_RATIO_OF_FLOOR;
import static bidder.config.BidStrategyConfig.YELLOW_LAG_RATIO;

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
            state.paceBand = PaceBand.ORANGE;
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

        double targetShift;
        double targetPace;

        switch (state.paceBand) {
            case GREEN:
                targetShift = 0.08;
                targetPace = 0.98;
                break;
            case YELLOW:
                targetShift = -0.03;
                targetPace = 1.03;
                break;
            case ORANGE:
                targetShift = -0.19;
                targetPace = 1.14;
                break;
            case RED:
                targetShift = -0.30;
                targetPace = 1.22;
                break;
            default:
                targetShift = 0.0;
                targetPace = 1.0;
                break;
        }

        if (state.paceBand == PaceBand.RED && state.trackedSpent < floorBudget && state.lowSpendCatchUpBlocks >= 2) {
            targetShift -= 0.05;
            targetPace += 0.04;
        }
        if (state.paceBand == PaceBand.RED && state.trackedSpent < floorBudget && state.lowSpendCatchUpBlocks >= 4) {
            targetShift -= 0.04;
            targetPace += 0.03;
        }
        if (state.paceBand == PaceBand.ORANGE && state.trackedSpent < floorBudget && spendLag > yellowExitLag) {
            targetShift -= 0.03;
            targetPace += 0.02;
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
}
