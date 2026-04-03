# PacingController Reference

Source: `src/bidder/pacing/PacingController.java`

## Class purpose

`PacingController` is the closed-loop spend governor for the bot.

Its job is to continuously answer: _"Are we spending too slowly, too fast, or on target relative to the floor objective?"_ and then translate that answer into concrete control outputs used by bidding logic:

- `state.paceBand` (`GREEN`, `YELLOW`, `ORANGE`, `RED`): urgency regime.
- `state.thresholdShift`: how strict/loose tier entry should be.
- `state.paceMultiplier`: scalar that globally scales bid sizes.

In short, this class controls pacing pressure over time so the bot can reach spend floor reliably without drifting into inefficient over-spending.

## Why this controller exists

Without pacing control, a score-only bidder usually fails in one of two ways:

1. **Under-spend risk**: too selective early/mid run, misses floor target.
2. **Catch-up panic**: over-corrects late and spends inefficiently.

`PacingController` avoids both by using:

- a progress-aware target curve (`targetSpentSoFar`),
- lag-based regime switching (`paceBand`),
- smoothing (`approach` + `CONTROL_STEP`),
- block-level memory (`consecutiveLagBlocks`, `lowSpendCatchUpBlocks`),
- near-floor and post-floor stabilization rules.

## File-level declarations and imports

- `package bidder.pacing;`  
  Places this class in pacing control layer.
- Static imports from `BidStrategyConfig`  
  Supply all tuning parameters: lag thresholds, floor ratios, guard ratios, and smoothing constants.
- `import bidder.state.BidRuntimeState;`  
  The state object this controller reads and updates.
- `import domain.Bid;`  
  Used to inspect whether a decision entered the auction (`maxBid > 0`).
- `public final class PacingController` + private constructor  
  Utility-style, stateless controller with static methods.

## High-level control loop

The runtime loop uses this class in two places:

1. `refreshPacingControls(state)` before constructing each bid.
2. `updatePacingState(state, decidedBid)` after each bid decision.

Every 100 rounds, `updatePacingState` triggers `evaluateBlockPacing`, which updates lag-memory counters and then calls `refreshPacingControls` again.

This creates a dual-timescale controller:

- **fast loop**: per-round controls,
- **slow loop**: per-100-round behavior adjustment.

## Method-by-method deep explanation

### `updatePacingState(BidRuntimeState state, Bid decidedBid)`

Purpose:

- Maintain per-round counters and trigger block evaluation cadence.

What it does:

- Increments `roundsSeen` and `blockRounds` every decision.
- If `decidedBid.maxBid() > 0`, increments `blockEnteredRounds` (participation tracking).
- When block reaches 100 rounds, calls `evaluateBlockPacing(state)`.

Why:

- Pacing needs both absolute timeline (`roundsSeen`) and block participation/spend behavior.
- 100-round granularity smooths noisy per-round outcomes while still reacting frequently.

### `refreshPacingControls(BidRuntimeState state)`

Purpose:

- Core decision function that maps spend progress into current pacing controls.

Step 1: derive control signals

- `floorBudget = ceil(initialBudget * FLOOR_SPEND_RATIO)`
- `targetBudget = ceil(floorBudget * INTERNAL_TARGET_BUFFER_RATIO)`
- `targetSoFar = targetSpentSoFar(state)`
- `spendLag = targetSoFar - trackedSpent`
- `lagRatio = spendLag / max(1, targetBudget)`
- `redExitLag` and `yellowExitLag` from floor-scaled ratios
- `nearFloorBudget = ceil(floorBudget * NEAR_FLOOR_STABILITY_RATIO)`

Why this signal design:

- `lagRatio` gives scale-invariant lag severity.
- `redExitLag`/`yellowExitLag` add absolute guardrails so regime exits are not ratio-only artifacts.
- `nearFloorBudget` softens aggression before hard floor completion.

Step 2: choose initial `paceBand`

- If already at/above floor: force `GREEN`.
- Else classify by lag thresholds and absolute lag limits:
  - GREEN if very low lag,
  - YELLOW if moderate,
  - ORANGE if high,
  - RED otherwise.

Why:

- Dual conditions (ratio + absolute) reduce false transitions and make bands more robust across budgets.

Step 3: apply regime safety overrides

- **Early red guard**: before `EARLY_RED_ROUND_GUARD`, RED is downgraded to ORANGE.
- **Near-floor dampening**: near floor, RED softens to ORANGE; ORANGE can soften to YELLOW when lag is acceptable.
- **Persistent lag escalation**: if `consecutiveLagBlocks >= 4`, increase band by one level unless already RED.

Why:

- Prevents early panic, reduces late overshoot, but still escalates if sustained lag persists.

Step 4: map band to target controls

- GREEN -> `targetShift=0.08`, `targetPace=0.98`
- YELLOW -> `-0.03`, `1.03`
- ORANGE -> `-0.19`, `1.14`
- RED -> `-0.30`, `1.22`

Interpretation:

- More negative `targetShift` means looser entry threshold (more rounds pass tier gate).
- Higher `targetPace` means larger bids.

Why:

- Band controls both participation rate and price pressure through independent levers.

Step 5: add conditional catch-up boosts

- RED + pre-floor + `lowSpendCatchUpBlocks >= 2`: additional loosening and pace increase.
- RED + pre-floor + `lowSpendCatchUpBlocks >= 4`: another increment.
- ORANGE + pre-floor + lag above yellow-exit absolute lag: small additional boost.

Why:

- Repeated low-spend evidence justifies stronger corrections than band alone.

Step 6: smooth and clamp controls

- Move current controls toward targets with `approach(..., CONTROL_STEP)`.
- Clamp:
  - `thresholdShift` to `[-0.66, 0.65]`
  - `paceMultiplier` to `[0.92, 1.34]`

Why:

- Smoothing prevents oscillation and abrupt bid behavior.
- Clamps prevent pathological extremes.

Step 7: apply spend guard corridor

- Compute `lowerGuardSpend` and `upperGuardSpend` from initial budget ratios.
- If spend is within corridor `[lower, upper)`, enforce mild cool-down (`thresholdShift >= 0.06`, `paceMultiplier <= 0.99`).
- If spend exceeds upper guard, force GREEN and stronger cool-down; reset lag counters.

Why:

- Corridor acts as anti-overshoot damping around floor attainment zone.

Step 8: hard post-floor stabilization

- If spend >= floor:
  - force GREEN,
  - clear lag counters,
  - move controls toward conservative anchors (`0.10`, `0.97`) with slightly faster step (`CONTROL_STEP + 0.01`),
  - keep `thresholdShift >= 0` and `paceMultiplier <= 1.00`.

Why:

- After floor objective is met, strategy should prioritize efficiency and avoid unnecessary aggression.

### `evaluateBlockPacing(BidRuntimeState state)`

Purpose:

- Slow-loop analysis over the last 100 rounds to update lag persistence signals.

What it computes:

- `participationRate = blockEnteredRounds / 100`
- fresh `lagRatio` using current target/spend
- `desiredBlockSpend` from `desiredRedBlockSpend(lagRatio)`
- local near-floor threshold at `95%` floor

Counter update logic:

- Increase `consecutiveLagBlocks` when pre-floor lag is meaningful (`> 0.14`) and participation is low (`< 0.14`).
- Decrease it when lag improves (`<= YELLOW_LAG_RATIO`) or participation is healthy (`> 0.24`).
- Increase `lowSpendCatchUpBlocks` when pre-floor lag is high (`> ORANGE_LAG_RATIO`) and actual block spend is below desired.
- Decrease it when lag improves or block spend meets desired.
- Near floor (`>= 95% floor`) decay both counters by `2`.
- Clamp `lowSpendCatchUpBlocks` to `[0, 12]`.

Then it:

- resets block counters,
- stores `lastEvaluatedBlockSpend`,
- clears `blockSpentByResults`,
- calls `refreshPacingControls(state)` to apply updated memory.

Why:

- Per-round lag can be noisy; block-level counters capture persistent underperformance and feed more stable escalation decisions.

### `desiredRedBlockSpend(double lagRatio)`

Purpose:

- Convert lag severity into expected minimum spend per 100 rounds in high-pressure conditions.

Mapping:

- `lagRatio >= 0.75` -> `320`
- `>= 0.55` -> `285`
- `>= 0.40` -> `245`
- `>= ORANGE_LAG_RATIO` -> `210`
- otherwise `175`

Why:

- Provides a simple piecewise target used by `lowSpendCatchUpBlocks` logic to detect whether catch-up is actually happening.

### `increaseBand(PaceBand band)`

Purpose:

- Escalate one urgency level when persistent lag blocks accumulate.

Behavior:

- GREEN -> YELLOW
- YELLOW -> ORANGE
- otherwise -> RED

Why:

- Encapsulates deterministic escalation policy and keeps transition logic centralized.

### `floorBudget(BidRuntimeState state)`

Purpose:

- Compute effective floor-spend objective in absolute currency units.

Formula:

- `ceil(initialBudget * FLOOR_SPEND_RATIO)`

Why:

- Shared helper used across pacing and policy to ensure one consistent floor definition.

### `internalTargetBudget(BidRuntimeState state)`

Purpose:

- Compute buffered internal objective above floor.

Formula:

- `ceil(floorBudget * INTERNAL_TARGET_BUFFER_RATIO)`

Why:

- The small buffer provides safety margin against randomness and summary reconciliation drift.

### `targetSpentSoFar(BidRuntimeState state)`

Purpose:

- Produce expected cumulative spend target at current progress.

Computation:

- `progress = min(1.0, roundsSeen / TARGET_ROUNDS_TO_HIT_FLOOR)`
- `smoothProgress = progress^2 * (3 - 2*progress)` (smoothstep)
- `blendedProgress = 0.65*progress + 0.35*smoothProgress`
- return `round(targetBudget * blendedProgress)`

Why this shape:

- Pure linear can be too rigid; pure smoothstep can delay pressure too much then spike late.
- Blended curve starts pressure early enough while avoiding violent late catch-up demands.

### `approach(double current, double target, double step)`

Purpose:

- Monotonic bounded move toward a target.

Behavior:

- If below target, increase by at most `step`.
- If above target, decrease by at most `step`.
- If equal, keep unchanged.

Why:

- Standard control-smoothing primitive that reduces instability and oscillatory bidding.

## Practical design summary

`PacingController` combines:

- **model-based expectation** (`targetSpentSoFar`),
- **feedback** (`spendLag`, `lagRatio`, participation, block spend),
- **stateful memory** (lag counters),
- **actuators** (`paceBand`, `thresholdShift`, `paceMultiplier`),
- **stability guards** (early red guard, near-floor dampening, smoothing, clamps, guard corridor).

The result is a pacing system that is aggressive when it must be, conservative when it can be, and progressively more stable near and after floor attainment.
