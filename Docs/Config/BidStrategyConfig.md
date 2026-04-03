# BidStrategyConfig Reference

Source: `src/bid/config/BidStrategyConfig.java`

## File-level declarations

- `package bid.config;`  
  Declares the package namespace.
- `import domain.Category;`  
  Imports the category enum used by configuration constants.
- `public final class BidStrategyConfig`  
  Static utility-style config holder; `final` prevents inheritance.
- `private BidStrategyConfig() {}`  
  Prevents instantiation (constants-only class).
- Comment above `CHOSEN_CATEGORY`  
  Notes that this constant must match the startup category sent by `Runner`.

## Core targeting constants

- `CHOSEN_CATEGORY = Category.VIDEOGAMES`  
  The fixed ad category this bot commits to for the whole run.
- `MATCH_VIDEO_WEIGHT = 1.10`  
  Contribution weight when video category matches chosen category.
- `MATCH_INTEREST_1_WEIGHT = 1.00`  
  Weight for matching viewer's top interest.
- `MATCH_INTEREST_2_WEIGHT = 0.60`  
  Weight for matching viewer's second interest.
- `MATCH_INTEREST_3_WEIGHT = 0.30`  
  Weight for matching viewer's third interest.
- `SYNERGY_BONUS = 0.60`  
  Extra score when multiple positive match signals align.
- `SUBSCRIBED_SCORE = 0.55`  
  Base score contribution for subscribed viewers.

## Composite score multipliers

- `MATCH_WEIGHT = 2.6`  
  Global multiplier for category/interest match component.
- `SYNERGY_WEIGHT = 0.9`  
  Multiplier for synergy component.
- `SUB_WEIGHT = 1.1`  
  Multiplier for subscription-related component.
- `ENGAGEMENT_WEIGHT = 1.3`  
  Multiplier for engagement-derived component (e.g., comment/view signal).
- `VIEW_WEIGHT = 1.1`  
  Multiplier for view-count-derived component.

## Optional demographic modifiers

- `AGE_18_24_BONUS = 0.15`  
  Positive adjustment for age 18-24.
- `AGE_25_34_BONUS = 0.10`  
  Positive adjustment for age 25-34.
- `AGE_35_44_BONUS = 0.01`  
  Very small positive adjustment for age 35-44.
- `AGE_13_17_PENALTY = -0.03`  
  Slight negative adjustment for age 13-17.

## Bidding/spend policy constants

- `MAX_REASONABLE_BID = 250`  
  Upper cap to avoid extreme overbids.
- `FLOOR_SPEND_RATIO = 0.30`  
  Tracks the competition's 30% spend floor behavior.
- `INTERNAL_TARGET_BUFFER_RATIO = 1.03`  
  Internal target buffer (3% above floor) for safety against variance.
- `TARGET_ROUNDS_TO_HIT_FLOOR = 90_000`  
  Planned pacing horizon to reach spend target.

## Lag severity thresholds

These constants are interpreted by `src/bid/pacing/PacingController.java`.

At runtime, the controller computes:

- `targetSpentSoFar`: expected spend by current round (smoothed progress curve)
- `spendLag = targetSpentSoFar - trackedSpent`
- `lagRatio = spendLag / internalTargetBudget`

For a 10,000,000 budget:

- Floor budget = `3,000,000` (`FLOOR_SPEND_RATIO`)
- Internal target budget = `3,090,000` (`FLOOR * INTERNAL_TARGET_BUFFER_RATIO`)

- `GREEN_LAG_RATIO = 0.025`  
  Very small lag zone. Used in `PacingController.refreshPacingControls` as the first non-floor branch. Requires both low ratio lag and low absolute lag (`spendLag <= max(30_000, yellowExitLag / 2)`), so it is intentionally strict. At 10M budget, ratio-only lag is about `77,250`.
- `YELLOW_LAG_RATIO = 0.11`  
  Moderate lag zone. If lag is above green but within yellow and absolute lag is below `yellowExitLag`, pacing stays controlled rather than aggressive. Also used to decay catch-up counters in block evaluation (`evaluateBlockPacing`). At 10M budget, ratio-only lag is about `339,900`.
- `ORANGE_LAG_RATIO = 0.27`  
  High lag zone. Above this, controller tends to RED behavior unless absolute lag gates still permit ORANGE. This value also gates several catch-up bid boosts in `src/bid/policy/BidAdjustmentPolicy.java`. At 10M budget, ratio-only lag is about `834,300`.
- `EARLY_RED_ROUND_GUARD = 18_000`  
  Early-run anti-panic guard. Before round `18,000`, any computed RED band is downgraded to ORANGE. This avoids overreacting when outcome noise is still high and observed spend is not yet stable.
- `CONTROL_STEP = 0.04`  
  Smoothing factor for control state transitions (`thresholdShift`, `paceMultiplier`). Instead of jumping directly to target aggression, controller approaches targets by increments of `0.04`. This reduces oscillation and keeps bid dynamics stable.

## Guard rails and state exits

- `TARGET_SPEND_LOWER_GUARD_RATIO = 0.305`  
  Lower edge of a stabilization corridor around the 30% floor. When tracked spend enters `[30.5%, 31.5%)`, controller applies a mild cool-down (`thresholdShift >= 0.06`, `paceMultiplier <= 0.99`) to avoid overshooting while still securing floor compliance.
- `TARGET_SPEND_UPPER_GUARD_RATIO = 0.315`  
  Upper edge of stabilization corridor. Once spend reaches `31.5%+`, controller forces GREEN-like behavior and stronger cool-down (`thresholdShift >= 0.12`, `paceMultiplier <= 0.95`) because floor objective is already safe.
- `RED_EXIT_SPEND_LAG_RATIO_OF_FLOOR = 0.20`  
  Converts floor budget to an absolute lag allowance for RED-to-lower transitions: `redExitLag = ceil(floorBudget * 0.20)`. At 10M budget, this is `600,000`. Using floor-based lag here prevents ratio-only artifacts late in the run.
- `YELLOW_EXIT_SPEND_LAG_RATIO_OF_FLOOR = 0.12`  
  Tighter absolute lag allowance for YELLOW/GREEN transitions: `yellowExitLag = ceil(floorBudget * 0.12)`. At 10M budget, this is `360,000`. This tighter bound prevents declaring "on track" too early.

## Fallback bidding gates

- `MIN_MATCH_SCORE_FOR_LAG_BID = 0.60`  
  Quality floor for catch-up mode. Used in multiple lag paths in `BidAdjustmentPolicy` to avoid spending catch-up budget on weak relevance rounds. Practical effect: lag mode expands spend mostly on medium-plus match opportunities.
- `MIN_ENGAGEMENT_FALLBACK_FOR_LAG_BID = 1.15`  
  Secondary quality path when direct match is weaker. In lag mode, subscribed viewers with engagement score >= `1.15` can still pass filters, preserving access to high-value impressions outside strict category-match cases.
- `NEAR_FLOOR_STABILITY_RATIO = 0.92`  
  Near-floor dampener. `nearFloorBudget = ceil(floorBudget * 0.92)`. At 10M budget, this is `2,760,000`. After crossing it, RED pressure is softened (RED -> ORANGE, ORANGE -> YELLOW when lag is acceptable), reducing late-stage overbidding risk.

## Practical summary

This config controls three things:

1. **Opportunity scoring** (match, synergy, subscriber, engagement, views, demographics)
2. **Budget pacing** toward the effective 30% floor
3. **Adaptive aggressiveness** based on how far spending lags target

From `Lag severity thresholds` onward, the constants implement a closed-loop pacing controller:

- Ratio thresholds classify lag severity (GREEN/YELLOW/ORANGE/RED)
- Floor-scaled absolute lags define safer band exits
- Guard ratios prevent overshoot near floor attainment
- Quality gates keep lag spending efficient instead of indiscriminate
