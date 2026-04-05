package bidder.config;

import domain.Category;

public final class BidStrategyConfig {

    private BidStrategyConfig() {
    }

    /**
     * Keep this in sync with the startup category currently sent by Runner.
     */
    public static final Category CHOSEN_CATEGORY = Category.VIDEOGAMES;

    public static final double MATCH_VIDEO_WEIGHT = 1.10;
    public static final double MATCH_INTEREST_1_WEIGHT = 1.00;
    public static final double MATCH_INTEREST_2_WEIGHT = 0.60;
    public static final double MATCH_INTEREST_3_WEIGHT = 0.30;
    public static final double SYNERGY_BONUS = 0.60;
    public static final double SUBSCRIBED_SCORE = 0.55;

    public static final double MATCH_WEIGHT = 2.6;
    public static final double SYNERGY_WEIGHT = 0.9;
    public static final double SUB_WEIGHT = 1.1;
    public static final double ENGAGEMENT_WEIGHT = 1.3;
    public static final double VIEW_WEIGHT = 1.1;

    // Optional, weak demographic modifiers.
    public static final double AGE_18_24_BONUS = 0.15;
    public static final double AGE_25_34_BONUS = 0.10;
    public static final double AGE_35_44_BONUS = 0.01;
    public static final double AGE_13_17_PENALTY = -0.03;

    public static final int MAX_REASONABLE_BID = 250;
    public static final double FLOOR_SPEND_RATIO = 0.30;
    public static final double INTERNAL_TARGET_BUFFER_RATIO = 1.03;
    public static final int TARGET_ROUNDS_TO_HIT_FLOOR = 70_000;

    public static final double GREEN_LAG_RATIO = 0.025;
    public static final double YELLOW_LAG_RATIO = 0.11;
    public static final double ORANGE_LAG_RATIO = 0.27;

    public static final int EARLY_RED_ROUND_GUARD = 18_000;
    public static final double EARLY_RED_HIGH_PRESSURE_LAG_RATIO = 0.34;
    public static final double CONTROL_STEP = 0.04;

    public static final double TARGET_SPEND_LOWER_GUARD_RATIO = 0.305;
    public static final double TARGET_SPEND_UPPER_GUARD_RATIO = 0.315;
    public static final double RED_EXIT_SPEND_LAG_RATIO_OF_FLOOR = 0.20;
    public static final double YELLOW_EXIT_SPEND_LAG_RATIO_OF_FLOOR = 0.12;

    public static final double MIN_MATCH_SCORE_FOR_LAG_BID = 0.60;
    public static final double MIN_ENGAGEMENT_FALLBACK_FOR_LAG_BID = 1.15;
    public static final double NEAR_FLOOR_STABILITY_RATIO = 0.92;

    public static final int HQ_OUTBID_STAGE_CAP = 4;
    public static final int HQ_OUTBID_MIN_ENTERED = 8;
    public static final double HQ_OUTBID_MAX_WIN_RATE = 0.08;
    public static final double HQ_OUTBID_MIN_GOOD_ENTRY_RATE = 0.95;
    public static final int HQ_OUTBID_SEVERE_ZERO_WIN_MIN_ENTERED = 14;
    public static final double HQ_OUTBID_RECOVERY_WIN_RATE = 0.14;
    public static final double HQ_OUTBID_STRONG_RECOVERY_WIN_RATE = 0.22;
    public static final int HQ_OUTBID_RECOVERY_STREAK_BLOCKS_TO_RESET = 2;
    public static final int HQ_OUTBID_MIN_STAGE_FOR_ORANGE_BAND = 2;
    public static final int HQ_OUTBID_MIN_STAGE_FOR_EXTRA_PACE = 3;
    public static final double HQ_OUTBID_EXTRA_TARGET_SHIFT = 0.03;
    public static final double HQ_OUTBID_EXTRA_TARGET_PACE = 0.03;

    public static final int[] HQ_RECOVERY_GOOD_MIN_MAX_BY_STAGE = {34, 44, 58, 76};
    public static final int[] HQ_RECOVERY_PREMIUM_MIN_MAX_BY_STAGE = {52, 64, 78, 92};
    public static final double[] HQ_RECOVERY_GOOD_MIN_START_RATIO_BY_STAGE = {0.82, 0.88, 0.92, 0.96};
    public static final double[] HQ_RECOVERY_PREMIUM_MIN_START_RATIO_BY_STAGE = {0.94, 0.97, 0.99, 1.00};

    public static final int HQ_RECOVERY_PROBE_MIN_STAGE = 2;
    public static final int HQ_RECOVERY_PROBE_CADENCE = 4;
    public static final int HQ_RECOVERY_GOOD_PROBE_MAX_INCREMENT = 8;
    public static final int HQ_RECOVERY_PREMIUM_PROBE_MAX_INCREMENT = 12;
    public static final double HQ_RECOVERY_GOOD_PROBE_MIN_START_RATIO = 0.95;
    public static final double HQ_RECOVERY_PREMIUM_PROBE_MIN_START_RATIO = 0.99;

    public static final int HQ_RECOVERY_STAGNATION_MIN_CONSECUTIVE_BLOCKS = 6;
    public static final double HQ_RECOVERY_STAGNATION_MIN_LAG_RATIO = ORANGE_LAG_RATIO;
    public static final int HQ_RECOVERY_STAGNATION_GOOD_MIN_MAX = 96;
    public static final int HQ_RECOVERY_STAGNATION_PREMIUM_MIN_MAX = 120;
    public static final double HQ_RECOVERY_STAGNATION_GOOD_MIN_START_RATIO = 0.985;
    public static final double HQ_RECOVERY_STAGNATION_PREMIUM_MIN_START_RATIO = 1.00;
    public static final int HQ_RECOVERY_STAGNATION_PROBE_CADENCE = 1;
    public static final int HQ_RECOVERY_STAGNATION_GOOD_PROBE_MAX_INCREMENT = 10;
    public static final int HQ_RECOVERY_STAGNATION_PREMIUM_PROBE_MAX_INCREMENT = 14;
}
