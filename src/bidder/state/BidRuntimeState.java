package bidder.state;

import bidder.diagnostics.RoundQualityBucket;
import bidder.pacing.PaceBand;
import bidder.pacing.CrowdingPressure;
import domain.Bid;

public class BidRuntimeState {

    public final int initialBudget;
    public int remainingBudget = Integer.MAX_VALUE;
    public int trackedSpent = 0;
    public int summarySpentTotal = 0;
    public int roundsSeen = 0;

    public int blockRounds = 0;
    public int blockEnteredRounds = 0;
    public int blockEnteredWithBid = 0;
    public int blockWins = 0;
    public int blockLossesWithBid = 0;
    public int blockWinCostSum = 0;
    public int blockWinCostCount = 0;
    public int blockSpentByResults = 0;
    public int lastEvaluatedBlockSpend = 0;
    public int consecutiveLagBlocks = 0;
    public int lowSpendCatchUpBlocks = 0;

    public double blockTailScoreSum = 0.0;
    public int blockTailScoreCount = 0;
    public int blockBidMaxSum = 0;
    public int blockBidEnteredCount = 0;
    public int[] diagnosticsBlockRoundsByBucket = new int[RoundQualityBucket.values().length];
    public int[] diagnosticsBlockEnteredByBucket = new int[RoundQualityBucket.values().length];
    public int[] diagnosticsBlockWinsByBucket = new int[RoundQualityBucket.values().length];
    public int[] diagnosticsBlockLossesByBucket = new int[RoundQualityBucket.values().length];
    public int[] diagnosticsBlockEnteredMaxSumByBucket = new int[RoundQualityBucket.values().length];
    public RoundQualityBucket diagnosticsLastRoundBucket = RoundQualityBucket.LOW;
    public boolean diagnosticsLastRoundEntered = false;

    public double lastBlockParticipationRate = 0.0;
    public int lastBlockEnteredWithBid = 0;
    public int lastBlockWins = 0;
    public int lastBlockLossesWithBid = 0;
    public double lastBlockWinRate = 0.0;
    public double lastBlockAvgWinCost = 0.0;
    public int lastBlockHighQualityEntered = 0;
    public int lastBlockHighQualityWins = 0;
    public double lastBlockHighQualityWinRate = 0.0;
    public int lastBlockGoodEntered = 0;
    public double lastBlockGoodWinRate = 0.0;
    public double lastBlockMediocreEntryRate = 0.0;
    public int consecutiveHighQualityOutbidBlocks = 0;
    public int highQualityOutbidStage = 0;
    public int highQualityRecoveryStreakBlocks = 0;
    public boolean highQualityRecoveryStreakHasStrong = false;
    public int highQualityRecoveryProbeCounter = 0;
    public boolean highQualityRecoveryBoostActive = false;
    public boolean highQualityStagnationOverrideActive = false;
    public int highQualityStagnationRounds = 0;
    public CrowdingPressure crowdingPressure = CrowdingPressure.LOW;
    public double lastLagRatio = 0.0;
    public double lastTailScore = 0.0;
    public Bid lastDecidedBid = new Bid(0, 0);

    public double thresholdShift = 0.0;
    public double paceMultiplier = 1.0;

    public PaceBand paceBand = PaceBand.GREEN;

    public BidRuntimeState(int initialBudget) {
        this.initialBudget = initialBudget;
        remainingBudget = initialBudget;
    }
    
}
