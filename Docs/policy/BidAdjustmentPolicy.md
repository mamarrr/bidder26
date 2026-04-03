# BidAdjustmentPolicy Reference

Source: `src/bidder/policy/BidAdjustmentPolicy.java`

## Class purpose

`BidAdjustmentPolicy` is the tactical decision layer between base score-to-tier mapping and final bid sanitization.

It does not compute core opportunity value (that happens in `ScoreCalculator`) and it does not enforce hard safety caps (that happens in `BidSanitizer`). Instead, it reshapes candidate bids based on pacing state, spend phase, lag pressure, and round quality.

Design intent:

- Keep bidding behavior adaptive to spend lag (`paceBand`, `lastLagRatio`, catch-up counters).
- Increase spend pressure when below floor target.
- Protect efficiency after floor is reached by dropping/capping weaker rounds.
- Exploit premium rounds more aggressively.

## File-level declarations and imports

- `package bidder.policy;`  
  Places this class in the policy layer of the bidder module.
- Static imports from `BidStrategyConfig` (`CHOSEN_CATEGORY`, `MATCH_VIDEO_WEIGHT`, `MIN_*`, `ORANGE_LAG_RATIO`, `TARGET_ROUNDS_TO_HIT_FLOOR`)  
  Provide key thresholds and constants used for gating, boosts, and late catch-up logic.
- Static import `PacingController.floorBudget`  
  Reuses pacing floor calculation consistently instead of duplicating spend-floor math.
- Imports `PaceBand`, `BidRuntimeState`, domain models (`Bid`, `Video`, `Viewer`, `Category`, `ViewerInterests`, `ViewerSubscribed`)  
  Connects policy logic to current runtime state and per-round audience/video attributes.
- `public final class BidAdjustmentPolicy` + private constructor  
  Utility class with static methods only; no instances and no internal mutable state.

## Where it runs in the pipeline

In `src/bidder/BidCalculator.java`, methods are applied in this order:

1. `applyBandBidAdjustments`
2. `applyLagModeQualityFloor`
3. `applyControlledCatchUpLane`
4. `applySpendPhasePolicy`
5. `applyMinimalLateCatchUp`
6. `applyPremiumRoundBoost`

This order matters: broad pacing adjustments happen first, quality floors constrain them, phase and late-floor logic refine them, then premium-round boosts are applied at the end.

## Method-by-method deep explanation

### `applyBandBidAdjustments(...)`

Purpose:

- Apply coarse aggression tuning based on pacing band.
- Add small deterministic offsets before finer filters run.

Inputs and role:

- `state.paceBand`: current urgency regime.
- `startBid`, `maxBid`: base tier bid from `TierBidPolicy`.
- `score`, `matchScore`: quality and relevance signals.

Behavior:

- `GREEN`: no change.
- `YELLOW`: if bidding, increase `maxBid` by `+1`.
- `ORANGE`: if bidding and strong match (`matchScore >= MATCH_VIDEO_WEIGHT`), increase `startBid +1`, `maxBid +3`; else `maxBid +1`.
- `RED`: if bidding and strong match, `startBid +2`, `maxBid +5`; otherwise `maxBid +2`.
- `RED` fallback activation: if current candidate is no-bid (`maxBid == 0`) but `score >= 4.55` and `matchScore >= MIN_MATCH_SCORE_FOR_LAG_BID`, force a small entry bid `1 5`.

Why:

- Pace band should influence aggressiveness globally, but with lightweight adjustments.
- Strong-match rounds get larger increments because they are better spend vehicles under lag pressure.
- RED fallback prevents long inactivity streaks when the tier gate was just slightly too strict.

### `applyLagModeQualityFloor(...)`

Purpose:

- Prevent lag-driven overbidding on weak-quality rounds, mainly after floor is already achieved.

Key gating:

- If `paceBand == GREEN` or no active bid, return unchanged.
- If still pre-floor (`trackedSpent < floorBudget`), return unchanged (do not block spend catch-up too early).

Quality signals:

- `hasMatchSignal`: `matchScore >= MIN_MATCH_SCORE_FOR_LAG_BID`.
- `hasStrongMatchSignal`: `matchScore >= MATCH_VIDEO_WEIGHT`.
- `hasFallbackQualitySignal`: subscribed viewer and `engagementScore >= MIN_ENGAGEMENT_FALLBACK_FOR_LAG_BID`.

Behavior:

- In ORANGE/RED, if no match signal and no fallback signal plus `score >= 4.8`, force no-bid.
- In RED with weak match and `score < 6.8`, cap max bid to `27`.
- In ORANGE with weak match and `score < 6.3`, cap max bid to `19`.

Why:

- After floor is secured, efficiency dominates over raw spend acceleration.
- Capping instead of dropping preserves participation on borderline rounds without paying top prices.

### `applyControlledCatchUpLane(...)`

Purpose:

- Add targeted catch-up pressure when lag is persistent, while containing runaway bids.

Path A: when there is already an active bid (`filteredBid.maxBid() > 0`)

- RED + extreme lag (`lastLagRatio >= 0.80`) + non-elite score (`score < 8.1`): cap max at `34`.
- RED + lag (`lastLagRatio >= ORANGE_LAG_RATIO`) + strong match + high score (`>= 7.4`): boost `+2/+6`.
- ORANGE + moderate lag (`>= 0.20`) + strong match + `score >= 7.2`: boost `+1/+3`.
- RED + repeated under-spend blocks (`lowSpendCatchUpBlocks >= 2`) + strong match + `score >= 6.2`: extra `+1/+2`.

Path B: when current candidate is no-bid

- Enable only in lag mode:
  - `RED`, or
  - `ORANGE` with `lowSpendCatchUpBlocks >= 3` and `lastLagRatio > ORANGE_LAG_RATIO`.
- Disable if floor already reached.
- Require meaningful lag (`lastLagRatio >= 0.35`) and at least one catch-up block.
- Require either:
  - medium match signal (`matchScore >= 0.32` and `score >= 4.95`), or
  - quality fallback (subscribed + `engagementScore >= 1.0` + `score >= 4.8`).
- If lag is severe (`lastLagRatio >= 0.55` or many catch-up blocks), inject `2 8`; else inject `1 6`.

Why:

- This method is the main controlled spend-recovery lane: it can both boost existing bids and reopen selected no-bid rounds.
- It uses layered thresholds to avoid indiscriminate bidding while still breaking low-spend deadlocks.

### `applySpendPhasePolicy(...)`

Purpose:

- Split behavior into pre-floor and post-floor modes, because objective priorities differ.

Pre-floor branch (`trackedSpent < floorBudget`):

- If already bidding:
  - strong round (`matchScore >= MATCH_VIDEO_WEIGHT` or `score >= 6.4`): `+2/+4`.
  - medium round (`score >= 5.2` or `engagementScore >= 1.0`): `+1/+2`.
- If currently no-bid:
  - evaluate `relaxedCandidate`: `score >= 4.9` and at least one of
    - `matchScore >= 0.30`,
    - top viewer interest matches chosen category,
    - viewer subscribed.
  - if relaxed candidate, inject `2 8` in high lag (`lastLagRatio >= ORANGE_LAG_RATIO`), else `1 6`.

Post-floor branch:

- If no active bid, keep no-bid.
- Define `strongRound = matchScore >= MATCH_VIDEO_WEIGHT && score >= 6.7`.
- If not strong and `score < 5.5`, drop round (`0 0`).
- If not strong and `score < 6.3`, cap max to `20`.
- Else keep bid.

Why:

- Before floor: bias toward participation and controlled expansion.
- After floor: tighten selection to preserve points-per-spend efficiency.

### `applyMinimalLateCatchUp(...)`

Purpose:

- Last-resort floor attainment guard near the end of target pacing horizon.

Activation conditions:

- Active bid exists and floor not reached.
- Progress is late: `roundsSeen / TARGET_ROUNDS_TO_HIT_FLOOR >= 0.92`.
- Spend ratio still low: `trackedSpent / initialBudget < 0.295`.
- Round is high-quality and aligned:
  - video category == chosen category,
  - `matchScore >= MATCH_VIDEO_WEIGHT`,
  - `score >= 6.2`,
  - viewer top or second interest matches chosen category.

Actions:

- Raise `maxBid` floor to at least `20` (or `28` for `score >= 7.2`).
- If RED with lag above ORANGE threshold, add extra `+2` max.
- Ensure `startBid` is at least 50% of `maxBid`.

Why:

- Late in the run, failure to reach effective spend floor is costly.
- This method adds a narrow, high-quality emergency lane instead of broad panic bidding.

### `applyPremiumRoundBoost(...)`

Purpose:

- Amplify bids on rare high-conversion rounds where multiple strong signals align.

How it works:

- Calls `isPremiumRound(video, viewer, engagementScore)`.
- If premium:
  - RED: `start +4`, `max +10`
  - ORANGE: `start +3`, `max +7`
  - GREEN/YELLOW: `start +2`, `max +4`
- Otherwise returns unchanged bid.

Why:

- Premium rounds are likely to be high-value impressions; extra aggression is concentrated where expected return is strongest.

### `isPremiumRound(...)` (private helper)

Purpose:

- Encapsulate premium-round definition in one place.

Criteria:

- Video category matches chosen category.
- Viewer top interest matches chosen category.
- Viewer is subscribed.
- Engagement score is at least `1.2`.

Why:

- Requires category alignment + intent + engagement, reducing false positives.

### `extractInterests(...)` (private helper)

Purpose:

- Safely read viewer interests without null risks.

Behavior:

- Returns empty array when `viewerInterests` or internal array is null.
- Otherwise returns the underlying interests array.

Why:

- Keeps call sites concise and null-safe when checking top/second interest logic.

## Design rationale summary

`BidAdjustmentPolicy` intentionally layers adjustments from broad to narrow:

1. Pace-band coarse shifts
2. Lag-mode quality safety
3. Controlled catch-up interventions
4. Spend-phase (pre/post floor) policy
5. Late-floor emergency lane
6. Premium-opportunity amplification

This structure balances two competing goals across long runs:

- reach required spend floor reliably,
- preserve score efficiency by concentrating spend on higher-quality opportunities.
