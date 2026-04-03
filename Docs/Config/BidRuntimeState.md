# BidRuntimeState Reference

Source: `src/bidder/state/BidRuntimeState.java`

## File-level declarations

- `package bidder.state;`  
  Declares the runtime-state package used by bidder components.
- `import bidder.pacing.PaceBand;`  
  Imports pacing mode enum (`GREEN`, `YELLOW`, `ORANGE`, `RED`).
- `import domain.Bid;`  
  Imports bid value object used to store the last decision.
- `public class BidRuntimeState`  
  Mutable state container shared by calculator, pacing, and policy layers.

## Budget and progress fields

- `initialBudget = 10_000_000`  
  Startup budget baseline. Used to derive floor target, guard ranges, and lag ratios.
- `remainingBudget = Integer.MAX_VALUE`  
  Live budget cap for bid sanitization. Defaults to "unknown/unbounded" until externally set.
- `trackedSpent = 0`  
  Authoritative running spend estimate from round results and summary reconciliation.
- `summarySpentTotal = 0`  
  Spend total accumulated from periodic `S ...` summary lines; used to correct drift.
- `roundsSeen = 0`  
  Number of bidding rounds processed so far; drives pacing progress curve.

## 100-round block pacing telemetry

- `blockRounds = 0`  
  Round counter inside the current 100-round block.
- `blockEnteredRounds = 0`  
  Count of rounds where bot entered auction (`maxBid > 0`) within current block.
- `blockSpentByResults = 0`  
  Spend observed from `W cost` results in current block.
- `lastEvaluatedBlockSpend = 0`  
  Snapshot of previous block spend when block evaluation runs.
- `consecutiveLagBlocks = 0`  
  Number of consecutive blocks with persistent under-participation / lag symptoms.
- `lowSpendCatchUpBlocks = 0`  
  Counter for blocks where lag is high and realized spend is below desired catch-up pace.

## Decision quality aggregates (diagnostics support)

- `blockTailScoreSum = 0.0`  
  Sum of tail scores in current block, intended for average-score diagnostics.
- `blockTailScoreCount = 0`  
  Number of tail-score samples in current block.
- `blockBidMaxSum = 0`  
  Sum of max bids in current block for bid-intensity diagnostics.
- `blockBidEnteredCount = 0`  
  Count of rounds with active bids for block-level averaging.

## Last-observed control signals

- `lastBlockParticipationRate = 0.0`  
  Last computed participation ratio (`entered rounds / 100`).
- `lastLagRatio = 0.0`  
  Last computed pacing lag ratio (`spendLag / internalTargetBudget`).
- `lastTailScore = 0.0`  
  Last computed opportunity quality score.
- `lastDecidedBid = new Bid(0, 0)`  
  Last emitted bid after all adjustments; default is no-bid.

## Adaptive pacing controls

- `thresholdShift = 0.0`  
  Tier-selection offset applied before mapping score to bid tier. Positive value is stricter, negative value is looser.
- `paceMultiplier = 1.0`  
  Final scalar applied to start/max bids. Above 1.0 increases aggressiveness, below 1.0 cools spending.
- `paceBand = PaceBand.GREEN`  
  Current pacing regime selected by controller from lag severity.

## Runtime lifecycle summary

This state object is updated throughout the bid loop:

1. `BidCalculator` reads the current controls (`thresholdShift`, `paceMultiplier`, `paceBand`) to build a bid.
2. `PacingController.updatePacingState` increments round and block counters after each decision.
3. Bid results update spend trackers (`trackedSpent`, `blockSpentByResults`).
4. Every 100 rounds, block counters are evaluated and reset, then pacing controls are refreshed.
5. Periodic summary messages reconcile spend (`summarySpentTotal`) and can override optimistic local tracking.

In practice, `BidRuntimeState` is the single source of truth for pacing memory, spend accounting, and adaptive bid aggressiveness across rounds.
