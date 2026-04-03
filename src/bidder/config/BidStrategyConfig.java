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
    public static final int TARGET_ROUNDS_TO_HIT_FLOOR = 90_000;

    public static final double GREEN_LAG_RATIO = 0.025;
    public static final double YELLOW_LAG_RATIO = 0.11;
    public static final double ORANGE_LAG_RATIO = 0.27;

    public static final int EARLY_RED_ROUND_GUARD = 18_000;
    public static final double CONTROL_STEP = 0.04;

    public static final double TARGET_SPEND_LOWER_GUARD_RATIO = 0.305;
    public static final double TARGET_SPEND_UPPER_GUARD_RATIO = 0.315;
    public static final double RED_EXIT_SPEND_LAG_RATIO_OF_FLOOR = 0.20;
    public static final double YELLOW_EXIT_SPEND_LAG_RATIO_OF_FLOOR = 0.12;

    public static final double MIN_MATCH_SCORE_FOR_LAG_BID = 0.60;
    public static final double MIN_ENGAGEMENT_FALLBACK_FOR_LAG_BID = 1.15;
    public static final double NEAR_FLOOR_STABILITY_RATIO = 0.92;
}
