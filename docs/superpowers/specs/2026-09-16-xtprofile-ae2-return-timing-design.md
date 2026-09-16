# XTProfile AE2 Return Timing Design

## Goal

Measure processing-route `Time` from AE2's own observable lifecycle instead of inferring completion from GregTech machine activity.

## Problem

XTProfile currently starts timing when AE2 successfully pushes a processing pattern, then tries to infer elapsed runtime from the target GT machine/controller. This works for many machines but remains unreliable for the Assembly Line and any route whose controller active/progress state is not a trustworthy completion signal.

AE2 already tracks the exact outputs it is waiting to receive back from processing patterns in `CraftingCPUCluster.waitingFor`. When a returned stack is accepted by `CraftingCPUCluster.injectItems(..., MODULATE, ...)`, AE2 decrements that expected-output accounting. This is the authoritative event XTProfile should use for route completion.

## Design

For each successful processing `pushPattern()` XTProfile creates a pending operation scoped to the current Crafting CPU and the resolved Interface/CRIB medium. The operation records:

- CPU identity
- medium/interface identity
- dispatch timestamp
- every expected pattern output and expected quantity
- remaining quantity for each expected output

The existing medium aggregation remains unchanged: one Interface/CRIB is still one profiler row even if many patterns are routed through it.

When `CraftingCPUCluster.injectItems` accepts a stack in `Actionable.MODULATE` against AE2's `waitingFor`, XTProfile observes the accepted quantity and applies it to pending operations for that CPU/output key. Matching is FIFO for otherwise identical in-flight operations. An operation completes only when all expected output quantities for that dispatch have returned.

On completion, XTProfile adds `returnTimestamp - dispatchTimestamp` to the route's accumulated busy/time metric for both the current CPU scope and the session-wide `All` scope.

## Multiple Outputs and Parallel Operations

Processing patterns may produce multiple outputs. A pending operation is not complete until all expected outputs have returned in the required quantities.

If multiple identical operations are in flight, returned quantities are consumed from the oldest matching pending operation first. This preserves deterministic attribution without depending on GT machine internals. If AE2's action source exposes useful interface provenance, it may be used only as an additional discriminator; correctness must not depend on it.

## TPS Metric

`Time` becomes AE2-observed processing latency.

The existing GT controller/tick sampling remains responsible for `TPS` cost where a GT machine/controller can be resolved. A route can therefore have valid elapsed `Time` even when machine-side TPS information is unavailable.

## Reset and Session Semantics

Current CPU behavior remains unchanged: starting a new craft on the same physical CPU resets CPU-scoped route metrics and pending operations for that craft. `All` retains completed timing totals for the profiling session.

Pending operations must be discarded when their CPU craft is reset/cancelled or when the XTProfile session is cleared, so outputs from an old craft cannot complete a new craft's timers.

## Implementation Boundaries

Expected changes are limited to XTProfile timing and the existing AE2 CraftingCPU mixin path:

- `XTProfileRouteTracker` / a focused pending-operation helper for pending state and returned-output attribution
- `MixinCraftingCPUClusterXTProfile` to observe accepted returned outputs from `injectItems`
- existing tests plus focused tests for single-output, multi-output, partial quantities, FIFO identical outputs, CPU reset, and session aggregation

The UI and one-row-per-Interface/CRIB aggregation do not change.

## Verification

The implementation is complete only after:

1. regression tests demonstrate dispatch-to-return timing, including multi-output/FIFO/reset cases;
2. the full repository CI passes, including compilation and the dedicated-server smoke test;
3. an artifact is produced for in-game verification of Assembly Line timing.
