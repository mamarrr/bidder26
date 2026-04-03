package bidder.scoring;

import static bidder.config.BidStrategyConfig.AGE_13_17_PENALTY;
import static bidder.config.BidStrategyConfig.AGE_18_24_BONUS;
import static bidder.config.BidStrategyConfig.AGE_25_34_BONUS;
import static bidder.config.BidStrategyConfig.AGE_35_44_BONUS;
import static bidder.config.BidStrategyConfig.CHOSEN_CATEGORY;
import static bidder.config.BidStrategyConfig.MATCH_INTEREST_1_WEIGHT;
import static bidder.config.BidStrategyConfig.MATCH_INTEREST_2_WEIGHT;
import static bidder.config.BidStrategyConfig.MATCH_INTEREST_3_WEIGHT;
import static bidder.config.BidStrategyConfig.MATCH_VIDEO_WEIGHT;
import static bidder.config.BidStrategyConfig.SUBSCRIBED_SCORE;
import static bidder.config.BidStrategyConfig.SYNERGY_BONUS;

import domain.Category;
import domain.Video;
import domain.viewer.Viewer;
import domain.viewer.ViewerAge;
import domain.viewer.ViewerGender;
import domain.viewer.ViewerInterests;
import domain.viewer.ViewerSubscribed;

public final class ScoreCalculator {

    private ScoreCalculator() {
    }

    public static double calculateMatchScore(Video video, Viewer viewer) {
        double score = 0.0;

        if (video.category() == CHOSEN_CATEGORY) {
            score += MATCH_VIDEO_WEIGHT;
        }

        Category[] interests = extractInterests(viewer.interests());
        if (interests.length > 0 && interests[0] == CHOSEN_CATEGORY) {
            score += MATCH_INTEREST_1_WEIGHT;
        }
        if (interests.length > 1 && interests[1] == CHOSEN_CATEGORY) {
            score += MATCH_INTEREST_2_WEIGHT;
        }
        if (interests.length > 2 && interests[2] == CHOSEN_CATEGORY) {
            score += MATCH_INTEREST_3_WEIGHT;
        }

        return score;
    }

    public static double calculateSynergyBonus(Video video, Viewer viewer) {
        Category[] interests = extractInterests(viewer.interests());
        boolean topInterestMatch = interests.length > 0 && interests[0] == CHOSEN_CATEGORY;

        if (video.category() == CHOSEN_CATEGORY && topInterestMatch) {
            return SYNERGY_BONUS;
        }
        return 0.0;
    }

    public static double calculateSubscriptionScore(Viewer viewer) {
        return viewer.subscribed() == ViewerSubscribed.Y ? SUBSCRIBED_SCORE : 0.0;
    }

    public static double calculateEngagementScore(Video video) {
        int viewCount = Math.max(video.viewCount(), 1);
        int commentCount = Math.max(video.commentCount(), 0);

        double engagementRatio = (double) commentCount / (double) viewCount;
        double score = Math.log(1.0 + engagementRatio * 1200.0);

        return Math.min(2.2, Math.max(0.0, score));
    }

    public static double calculateViewBucketScore(int viewCount) {
        int safeViewCount = Math.max(viewCount, 0);

        if (safeViewCount < 1_000) {
            return 0.75;
        }
        if (safeViewCount < 10_000) {
            return 1.00;
        }
        if (safeViewCount < 100_000) {
            return 1.30;
        }
        if (safeViewCount < 1_000_000) {
            return 0.95;
        }
        if (safeViewCount < 10_000_000) {
            return 1.15;
        }

        return 0.80;
    }

    public static double calculateAgeScore(ViewerAge age) {
        if (age == null) {
            return 0.0;
        }

        switch (age) {
            case AGE_13_17:
                return AGE_13_17_PENALTY;
            case AGE_18_24:
                return AGE_18_24_BONUS;
            case AGE_25_34:
                return AGE_25_34_BONUS;
            case AGE_35_44:
                return AGE_35_44_BONUS;
            default:
                return 0.0;
        }
    }

    public static double getGenderScore(Viewer viewer) {
        if (viewer.gender() == ViewerGender.M) {
            return 0.05;
        }
        return 0.00;
    }

    private static Category[] extractInterests(ViewerInterests viewerInterests) {
        if (viewerInterests == null || viewerInterests.interests() == null) {
            return new Category[0];
        }
        return viewerInterests.interests();
    }
}
