package bidder.policy;

import static bidder.config.BidStrategyConfig.CHOSEN_CATEGORY;
import static bidder.config.BidStrategyConfig.MATCH_VIDEO_WEIGHT;
import static bidder.config.BidStrategyConfig.MIN_ENGAGEMENT_FALLBACK_FOR_LAG_BID;
import static bidder.config.BidStrategyConfig.MIN_MATCH_SCORE_FOR_LAG_BID;
import static bidder.config.BidStrategyConfig.ORANGE_LAG_RATIO;
import static bidder.config.BidStrategyConfig.TARGET_ROUNDS_TO_HIT_FLOOR;
import static bidder.pacing.PacingController.floorBudget;

import bidder.pacing.CrowdingPressure;
import bidder.pacing.PaceBand;
import bidder.state.BidRuntimeState;
import domain.Bid;
import domain.Category;
import domain.Video;
import domain.viewer.Viewer;
import domain.viewer.ViewerInterests;
import domain.viewer.ViewerSubscribed;

public final class BidAdjustmentPolicy {

    private static final double MEDIUM_PRESSURE_START_RATIO = 0.60;
    private static final double HIGH_PRESSURE_START_RATIO = 0.72;

    private BidAdjustmentPolicy() {
    }

    public static Bid applyBandBidAdjustments(
            BidRuntimeState state,
            int startBid,
            int maxBid,
            double score,
            double matchScore
    ) {
        int adjustedStart = startBid;
        int adjustedMax = maxBid;

        switch (state.paceBand) {
            case GREEN:
                break;
            case YELLOW:
                if (adjustedMax > 0) {
                    adjustedStart += 1;
                    adjustedMax += 2;
                }
                break;
            case ORANGE:
                if (adjustedMax > 0) {
                    if (matchScore >= MATCH_VIDEO_WEIGHT) {
                        adjustedStart += 2;
                        adjustedMax += 4;
                    } else {
                        adjustedMax += 2;
                    }
                }
                break;
            case RED:
                if (adjustedMax > 0) {
                    if (matchScore >= MATCH_VIDEO_WEIGHT) {
                        adjustedStart += 3;
                        adjustedMax += 6;
                    } else {
                        adjustedMax += 3;
                    }
                } else if (score >= 4.55 && matchScore >= MIN_MATCH_SCORE_FOR_LAG_BID) {
                    adjustedStart = 2;
                    adjustedMax = 6;
                }
                break;
            default:
                break;
        }

        return new Bid(adjustedStart, adjustedMax);
    }

    public static Bid applyLagModeQualityFloor(
            BidRuntimeState state,
            Bid bandAdjustedBid,
            double score,
            double matchScore,
            double engagementScore,
            Viewer viewer
    ) {
        if (state.paceBand == PaceBand.GREEN || bandAdjustedBid.maxBid() <= 0) {
            return bandAdjustedBid;
        }

        if (state.trackedSpent < floorBudget(state)) {
            return bandAdjustedBid;
        }

        boolean hasMatchSignal = matchScore >= MIN_MATCH_SCORE_FOR_LAG_BID;
        boolean hasStrongMatchSignal = matchScore >= MATCH_VIDEO_WEIGHT;
        boolean hasFallbackQualitySignal = viewer.subscribed() == ViewerSubscribed.Y
                && engagementScore >= MIN_ENGAGEMENT_FALLBACK_FOR_LAG_BID;

        if ((state.paceBand == PaceBand.ORANGE || state.paceBand == PaceBand.RED)
                && !hasMatchSignal
                && !(hasFallbackQualitySignal && score >= 4.8)) {
            return new Bid(0, 0);
        }

        if (state.paceBand == PaceBand.RED && !hasStrongMatchSignal && score < 6.8) {
            int cappedMax = Math.min(bandAdjustedBid.maxBid(), 27);
            int cappedStart = Math.min(bandAdjustedBid.startBid(), cappedMax);
            return new Bid(cappedStart, cappedMax);
        }

        if (state.paceBand == PaceBand.ORANGE && !hasStrongMatchSignal && score < 6.3) {
            int cappedMax = Math.min(bandAdjustedBid.maxBid(), 19);
            int cappedStart = Math.min(bandAdjustedBid.startBid(), cappedMax);
            return new Bid(cappedStart, cappedMax);
        }

        return bandAdjustedBid;
    }

    public static Bid applyControlledCatchUpLane(
            BidRuntimeState state,
            Bid filteredBid,
            double score,
            double matchScore,
            double engagementScore,
            Viewer viewer
    ) {
        if (filteredBid.maxBid() > 0) {
            int adjustedStart = filteredBid.startBid();
            int adjustedMax = filteredBid.maxBid();

            if (state.paceBand == PaceBand.RED && state.lastLagRatio >= 0.80 && score < 8.1) {
                adjustedMax = Math.min(adjustedMax, 34);
                adjustedStart = Math.min(adjustedStart, adjustedMax);
            }

            if (state.paceBand == PaceBand.RED
                    && state.lastLagRatio >= ORANGE_LAG_RATIO
                    && matchScore >= MATCH_VIDEO_WEIGHT
                    && score >= 7.4) {
                adjustedStart += 3;
                adjustedMax += 7;
            }

            if (state.paceBand == PaceBand.ORANGE
                    && state.lastLagRatio >= 0.20
                    && matchScore >= MATCH_VIDEO_WEIGHT
                    && score >= 7.2) {
                adjustedStart += 2;
                adjustedMax += 4;
            }

            if (state.paceBand == PaceBand.RED
                    && state.lowSpendCatchUpBlocks >= 2
                    && matchScore >= MATCH_VIDEO_WEIGHT
                    && score >= 6.2) {
                adjustedStart += 2;
                adjustedMax += 3;
            }

            return new Bid(adjustedStart, adjustedMax);
        }

        boolean lagMode = state.paceBand == PaceBand.RED
                || (state.paceBand == PaceBand.ORANGE
                && state.lowSpendCatchUpBlocks >= 3
                && state.lastLagRatio > ORANGE_LAG_RATIO);

        if (!lagMode || state.trackedSpent >= floorBudget(state)) {
            return filteredBid;
        }

        if (state.lastLagRatio < 0.35 || state.lowSpendCatchUpBlocks == 0) {
            return filteredBid;
        }

        boolean highPressure = state.crowdingPressure == CrowdingPressure.HIGH;
        boolean mediumMatchSignal = matchScore >= (highPressure ? 0.24 : 0.32)
                && score >= (highPressure ? 4.65 : 4.95);
        boolean qualityFallbackSignal = viewer.subscribed() == ViewerSubscribed.Y
                && engagementScore >= (highPressure ? 0.92 : 1.0)
                && score >= (highPressure ? 4.6 : 4.8);

        if (!mediumMatchSignal && !qualityFallbackSignal) {
            return filteredBid;
        }

        if (highPressure) {
            if (state.lastLagRatio >= 0.55 || state.lowSpendCatchUpBlocks >= 3) {
                return new Bid(4, 14);
            }
            return new Bid(3, 11);
        }

        if (state.lastLagRatio >= 0.55 || state.lowSpendCatchUpBlocks >= 3) {
            return new Bid(3, 10);
        }

        return new Bid(2, 7);
    }

    public static Bid applySpendPhasePolicy(
            BidRuntimeState state,
            Bid bid,
            double score,
            double matchScore,
            double engagementScore,
            Viewer viewer
    ) {
        boolean preFloor = state.trackedSpent < floorBudget(state);

        if (preFloor) {
            if (bid.maxBid() > 0) {
                int adjustedStart = bid.startBid();
                int adjustedMax = bid.maxBid();

                if (matchScore >= MATCH_VIDEO_WEIGHT || score >= 6.4) {
                    adjustedStart += 3;
                    adjustedMax += 5;
                } else if (score >= 5.2 || engagementScore >= 1.0) {
                    adjustedStart += 2;
                    adjustedMax += 3;
                }

                return new Bid(adjustedStart, adjustedMax);
            }

            Category[] interests = extractInterests(viewer.interests());
            boolean topInterestMatch = interests.length > 0 && interests[0] == CHOSEN_CATEGORY;
            boolean highPressure = state.crowdingPressure == CrowdingPressure.HIGH;
            boolean relaxedCandidate = score >= 4.9
                    && (matchScore >= 0.30 || topInterestMatch || viewer.subscribed() == ViewerSubscribed.Y);
            boolean highPressureMidQualityCandidate = highPressure
                    && state.lastLagRatio >= ORANGE_LAG_RATIO
                    && score >= 4.55
                    && (matchScore >= 0.24 || engagementScore >= 0.95 || viewer.subscribed() == ViewerSubscribed.Y);

            if (relaxedCandidate || highPressureMidQualityCandidate) {
                if (highPressure) {
                    if (state.lastLagRatio >= 0.50) {
                        return new Bid(4, 13);
                    }
                    return new Bid(3, 10);
                }
                if (state.lastLagRatio >= ORANGE_LAG_RATIO) {
                    return new Bid(3, 10);
                }
                return new Bid(2, 7);
            }

            return bid;
        }

        if (bid.maxBid() <= 0) {
            return bid;
        }

        boolean strongRound = matchScore >= MATCH_VIDEO_WEIGHT && score >= 6.7;
        if (!strongRound && score < 5.5) {
            return new Bid(0, 0);
        }

        if (!strongRound && score < 6.3) {
            int cappedMax = Math.min(bid.maxBid(), 20);
            int cappedStart = Math.min(bid.startBid(), cappedMax);
            return new Bid(cappedStart, cappedMax);
        }

        return bid;
    }

    public static Bid applyCompetitionPressureBoost(
            BidRuntimeState state,
            Bid bid,
            double score,
            double matchScore,
            double engagementScore,
            Video video,
            Viewer viewer
    ) {
        if (bid.maxBid() <= 0 || state.trackedSpent >= floorBudget(state) || state.crowdingPressure == CrowdingPressure.LOW) {
            return bid;
        }

        int adjustedStart = bid.startBid();
        int adjustedMax = bid.maxBid();
        boolean premiumRound = isPremiumRound(video, viewer, engagementScore);

        if (state.crowdingPressure == CrowdingPressure.MEDIUM) {
            if (score >= 7.4 || matchScore >= MATCH_VIDEO_WEIGHT) {
                adjustedMax += 4;
            } else if (score >= 6.2) {
                adjustedMax += 3;
            } else {
                adjustedMax += 2;
            }
            adjustedStart = enforceStartRatio(adjustedStart, adjustedMax, MEDIUM_PRESSURE_START_RATIO);
        } else {
            if (premiumRound) {
                adjustedMax += score >= 7.4 ? 10 : 8;
                adjustedStart = Math.max(adjustedStart, adjustedMax - 1);
            } else {
                if (score >= 7.6 || matchScore >= MATCH_VIDEO_WEIGHT) {
                    adjustedMax += 8;
                } else if (score >= 6.5) {
                    adjustedMax += 6;
                } else {
                    adjustedMax += 4;
                }
                adjustedStart = enforceStartRatio(adjustedStart, adjustedMax, HIGH_PRESSURE_START_RATIO);
            }
        }

        if (adjustedStart > adjustedMax) {
            adjustedStart = adjustedMax;
        }
        return new Bid(adjustedStart, adjustedMax);
    }

    public static Bid applyMinimalLateCatchUp(
            BidRuntimeState state,
            Bid bid,
            Video video,
            Viewer viewer,
            double score,
            double matchScore
    ) {
        if (bid.maxBid() <= 0 || state.trackedSpent >= floorBudget(state)) {
            return bid;
        }

        double progress = (double) state.roundsSeen / (double) Math.max(1, TARGET_ROUNDS_TO_HIT_FLOOR);
        if (progress < 0.92) {
            return bid;
        }

        double spendRatio = (double) state.trackedSpent / (double) Math.max(1, state.initialBudget);
        if (spendRatio >= 0.295) {
            return bid;
        }

        if (video.category() != CHOSEN_CATEGORY || matchScore < MATCH_VIDEO_WEIGHT || score < 6.2) {
            return bid;
        }

        Category[] interests = extractInterests(viewer.interests());
        boolean topOrSecondInterestMatch = (interests.length > 0 && interests[0] == CHOSEN_CATEGORY)
                || (interests.length > 1 && interests[1] == CHOSEN_CATEGORY);
        if (!topOrSecondInterestMatch) {
            return bid;
        }

        int adjustedMax = Math.max(bid.maxBid(), score >= 7.2 ? 28 : 20);
        if (state.paceBand == PaceBand.RED && state.lastLagRatio >= ORANGE_LAG_RATIO) {
            adjustedMax += 2;
        }

        int adjustedStart = Math.max(bid.startBid(), (int) Math.floor((double) adjustedMax * 0.50));
        return new Bid(adjustedStart, adjustedMax);
    }

    public static Bid applyPremiumRoundBoost(
            BidRuntimeState state,
            int startBid,
            int maxBid,
            Video video,
            Viewer viewer,
            double engagementScore
    ) {
        int adjustedStart = startBid;
        int adjustedMax = maxBid;

        if (isPremiumRound(video, viewer, engagementScore)) {
            if (state.paceBand == PaceBand.RED) {
                adjustedStart += 4;
                adjustedMax += 10;
            } else if (state.paceBand == PaceBand.ORANGE) {
                adjustedStart += 3;
                adjustedMax += 7;
            } else {
                adjustedStart += 2;
                adjustedMax += 4;
            }
        }

        return new Bid(adjustedStart, adjustedMax);
    }

    private static boolean isPremiumRound(Video video, Viewer viewer, double engagementScore) {
        Category[] interests = extractInterests(viewer.interests());
        boolean topInterestMatch = interests.length > 0 && interests[0] == CHOSEN_CATEGORY;

        return video.category() == CHOSEN_CATEGORY
                && topInterestMatch
                && viewer.subscribed() == ViewerSubscribed.Y
                && engagementScore >= 1.2;
    }

    private static Category[] extractInterests(ViewerInterests viewerInterests) {
        if (viewerInterests == null || viewerInterests.interests() == null) {
            return new Category[0];
        }
        return viewerInterests.interests();
    }

    private static int enforceStartRatio(int startBid, int maxBid, double minRatio) {
        int minStartBid = (int) Math.ceil((double) maxBid * minRatio);
        return Math.max(startBid, minStartBid);
    }
}
