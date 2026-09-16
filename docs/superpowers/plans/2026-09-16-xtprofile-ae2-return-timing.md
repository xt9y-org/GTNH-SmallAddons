# XTProfile AE2 Return Timing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace XTProfile route `Time` inference from GT machine active state with elapsed time from AE2 processing-pattern dispatch until AE2 accepts the expected output(s) back into the crafting CPU.

**Architecture:** Keep one Interface/CRIB as one `MediumRecord`. Add a focused pending-operation tracker keyed by CPU and AE stack identity. `recordDispatch` opens a pending processing operation from `ICraftingPatternDetails.getCondensedAEOutputs()`. A CraftingCPU mixin observes `recordReturnedOutputs(IAEStack<?>)`, which AE2 calls only after it has accepted that exact quantity against `waitingFor`; returned quantities consume pending operations FIFO and completed operations add dispatch-to-return latency to `MediumRecord.busyNs`. GT machine sampling remains only for tick/TPS cost.

**Tech Stack:** Java 17 source tooling targeting GTNH 1.7.10 runtime, AE2-Unofficial crafting API, Sponge Mixin/MixinExtras, JUnit 5, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-16-xtprofile-ae2-return-timing-design.md`

## Global Constraints

- Work only on branch `xtprofile`; do not merge to `master`.
- One Interface/CRIB remains one profiler row even when many patterns use it.
- `Time` is AE2-observed processing latency, not GT controller active time.
- `TPS` remains GT controller/tick-cost based when a controller can be resolved.
- Current CPU metrics reset when a new craft starts on the same CPU; `All` retains completed session totals.
- Pending operations from an old craft/session must not complete timers in a new craft/session.
- Processing patterns with multiple outputs complete only after all expected quantities have returned.
- Identical parallel operations are attributed FIFO per CPU/output.
- Full CI, including the dedicated-server smoke test, must pass before producing the test JAR.

---

### Task 1: Pending processing-operation model

**Files:**
- Create: `src/main/java/com/xt9y/features/xtprofile/XTProfilePendingOperations.java`
- Create: `src/test/java/com/xt9y/features/xtprofile/XTProfilePendingOperationsTest.java`
- Modify: `src/main/java/com/xt9y/features/xtprofile/XTProfileLabels.java`

**Interfaces:**
- Consumes: normalized output keys from `XTProfileLabels.stackKey(IAEStack<?>)` plus output quantities.
- Produces: `add(long cpuId, String mediumId, long startedNs, Map<String, Long> outputs)`, `List<Completion> accept(long cpuId, String outputKey, long amount, long returnedNs)`, `clearCpu(long cpuId)`, and `clear()`.
- `Completion` exposes `mediumId` and `elapsedNs` for each operation completed by an accepted returned quantity.

- [ ] **Step 1: Write failing tests for one-output completion, partial quantity, multi-output completion, FIFO identical outputs, and CPU clear.**

```java
@Test
void oneOutputCompletesAtReturnTime() {
    XTProfilePendingOperations pending = new XTProfilePendingOperations();
    pending.add(7L, "medium:a", 1_000L, outputs("item:x", 2L));

    assertTrue(pending.accept(7L, "item:x", 1L, 4_000L).isEmpty());
    List<XTProfilePendingOperations.Completion> done = pending.accept(7L, "item:x", 1L, 6_000L);

    assertEquals(1, done.size());
    assertEquals("medium:a", done.get(0).mediumId);
    assertEquals(5_000L, done.get(0).elapsedNs);
}

@Test
void multiOutputWaitsForEveryOutput() {
    XTProfilePendingOperations pending = new XTProfilePendingOperations();
    Map<String, Long> expected = new LinkedHashMap<>();
    expected.put("item:a", 1L);
    expected.put("item:b", 3L);
    pending.add(2L, "medium:assline", 10L, expected);

    assertTrue(pending.accept(2L, "item:a", 1L, 20L).isEmpty());
    assertTrue(pending.accept(2L, "item:b", 2L, 30L).isEmpty());
    assertEquals(30L, pending.accept(2L, "item:b", 1L, 40L).get(0).elapsedNs);
}

@Test
void identicalOperationsCompleteFifo() {
    XTProfilePendingOperations pending = new XTProfilePendingOperations();
    pending.add(3L, "medium:first", 100L, outputs("item:x", 1L));
    pending.add(3L, "medium:second", 200L, outputs("item:x", 1L));

    assertEquals("medium:first", pending.accept(3L, "item:x", 1L, 500L).get(0).mediumId);
    assertEquals("medium:second", pending.accept(3L, "item:x", 1L, 700L).get(0).mediumId);
}

@Test
void clearCpuDropsOldCraftOperations() {
    XTProfilePendingOperations pending = new XTProfilePendingOperations();
    pending.add(4L, "medium:old", 100L, outputs("item:x", 1L));
    pending.clearCpu(4L);
    assertTrue(pending.accept(4L, "item:x", 1L, 200L).isEmpty());
}
```

- [ ] **Step 2: Run the focused test and verify RED.**

Run: `./gradlew test --tests com.xt9y.features.xtprofile.XTProfilePendingOperationsTest`

Expected: compilation/test failure because `XTProfilePendingOperations` does not exist yet.

- [ ] **Step 3: Implement minimal FIFO pending-state logic.**

Use one insertion-ordered list of `Operation` objects. Each operation stores `cpuId`, `mediumId`, `startedNs`, and an insertion-ordered `Map<String, Long> remaining`. `accept` walks operations in insertion order, only touches matching CPU/output entries, decrements no more than the supplied amount, removes a completed operation, and emits exactly one `Completion` with `max(0, returnedNs - startedNs)`.

- [ ] **Step 4: Normalize generic AE stack keys so quantity does not affect identity.**

In `XTProfileLabels.describe`, keep the existing item key. For non-item stacks, copy the stack, set its stack size to `1`, and hash the normalized copy string rather than the original quantity-bearing stack.

- [ ] **Step 5: Run the focused tests and verify GREEN.**

Run: `./gradlew test --tests com.xt9y.features.xtprofile.XTProfilePendingOperationsTest`

Expected: all pending-operation tests pass.

- [ ] **Step 6: Commit.**

Commit message: `feat: track pending AE2 processing outputs`

---

### Task 2: Start route timers on successful processing dispatch

**Files:**
- Modify: `src/main/java/com/xt9y/features/xtprofile/XTProfileRouteTracker.java`
- Modify: `src/test/java/com/xt9y/features/xtprofile/XTProfileRouteTimingAndSearchTest.java`

**Interfaces:**
- Consumes: `XTProfilePendingOperations.add`, `clearCpu`, `clear` from Task 1.
- Produces: pending operations for successful non-craftable processing-pattern dispatches and CPU/session lifecycle cleanup.

- [ ] **Step 1: Add a failing test for condensed expected-output collection.**

Add a package-private helper on the intended API surface:

```java
static void addExpectedOutput(Map<String, Long> expected, String key, long amount)
```

Test that null/empty keys and non-positive amounts are ignored and duplicate keys are summed. This isolates the quantity semantics used when translating `getCondensedAEOutputs()` into one pending operation.

- [ ] **Step 2: Run the focused test and verify RED.**

Run: `./gradlew test --tests com.xt9y.features.xtprofile.XTProfileRouteTimingAndSearchTest`

Expected: failure because `addExpectedOutput` is not implemented.

- [ ] **Step 3: Wire successful processing dispatches into pending timing.**

In `recordDispatch(...)`, after obtaining `cpuId` and updating the medium record, only for `pattern != null && !pattern.isCraftable()`:

```java
Map<String, Long> expected = new LinkedHashMap<>();
for (IAEStack<?> output : pattern.getCondensedAEOutputs()) {
    if (output == null) continue;
    addExpectedOutput(expected, XTProfileLabels.stackKey(output), output.getStackSize());
}
if (!expected.isEmpty()) pendingOperations.add(cpuId, mediumId, now, expected);
```

Do not change medium aggregation or dispatch counts.

- [ ] **Step 4: Clear pending state at lifecycle boundaries.**

When `cpuId(...)` detects a new craft, call `pendingOperations.clearCpu(existing)` alongside `resetCpuMetrics`. In `clear()`, call `pendingOperations.clear()`.

- [ ] **Step 5: Run focused tests and verify GREEN.**

Run: `./gradlew test --tests com.xt9y.features.xtprofile.XTProfileRouteTimingAndSearchTest --tests com.xt9y.features.xtprofile.XTProfilePendingOperationsTest`

Expected: all focused tests pass.

- [ ] **Step 6: Commit.**

Commit message: `feat: start AE2 route timers on dispatch`

---

### Task 3: Finish timers from AE2 accepted returned outputs

**Files:**
- Modify: `src/main/java/com/xt9y/features/xtprofile/XTProfileRouteTracker.java`
- Modify: `src/mixin/java/com/xt9y/features/mixin/mixins/late/MixinCraftingCPUClusterXTProfile.java`
- Modify: `src/test/java/com/xt9y/features/xtprofile/XTProfileRouteTimingAndSearchTest.java`

**Interfaces:**
- Consumes: `XTProfilePendingOperations.accept`.
- Produces: `XTProfileRouteTracker.recordReturnedOutput(CraftingCPUCluster cpu, IAEStack<?> returnedStack)` and completed route latency accumulated into `MediumRecord.busyNs`/`busyNsByCpu`.

- [ ] **Step 1: Write failing tests for completion accounting.**

Add a package-private helper:

```java
static void accountCompletedLatency(XTProfileData.MediumRecord medium, long cpuId, long elapsedNs)
```

Test that it adds non-negative elapsed time to session `busyNs` and the exact CPU entry without changing tick-cost fields.

- [ ] **Step 2: Run focused test and verify RED.**

Run: `./gradlew test --tests com.xt9y.features.xtprofile.XTProfileRouteTimingAndSearchTest`

Expected: failure because `accountCompletedLatency` does not exist.

- [ ] **Step 3: Implement returned-output handling.**

`recordReturnedOutput` must ignore null stacks/non-positive quantities/inactive sessions, resolve the current CPU id, normalize the returned stack with `XTProfileLabels.stackKey`, pass the accepted quantity to `pendingOperations.accept`, and for every completion locate its `MediumRecord` and call `accountCompletedLatency`.

- [ ] **Step 4: Hook AE2's authoritative accepted-output point.**

In `MixinCraftingCPUClusterXTProfile`, inject at `HEAD` of:

```java
protected void recordReturnedOutputs(IAEStack<?> returnedStack)
```

and call:

```java
XTProfileRouteTracker.INSTANCE.recordReturnedOutput(
    (CraftingCPUCluster) (Object) this,
    returnedStack);
```

AE2 calls `recordReturnedOutputs` only after it has decremented `waitingFor` by the exact accepted quantity (`what` or `insert`), so no inference from method return leftovers is needed.

- [ ] **Step 5: Run focused tests and verify GREEN.**

Run: `./gradlew test --tests com.xt9y.features.xtprofile.XTProfileRouteTimingAndSearchTest --tests com.xt9y.features.xtprofile.XTProfilePendingOperationsTest`

Expected: all focused tests pass.

- [ ] **Step 6: Commit.**

Commit message: `feat: finish route timers from AE2 returns`

---

### Task 4: Separate `Time` from GT active-state accounting

**Files:**
- Modify: `src/main/java/com/xt9y/features/xtprofile/XTProfileRouteTracker.java`
- Modify: `src/test/java/com/xt9y/features/xtprofile/XTProfileRouteTimingAndSearchTest.java`
- Verify: `src/main/java/com/xt9y/features/xtprofile/XTProfilePanelAggregator.java`

**Interfaces:**
- Consumes: completed latency already stored in `MediumRecord.busyNs`.
- Produces: GT sampling updates only `tickCostNs` and `activeTicks`, so panel `Time` is exclusively dispatch-to-return latency while `TPS` still derives from average GT tick cost.

- [ ] **Step 1: Change the existing machine-tick test to require no `busyNs` mutation.**

For `accountRunningTick(...)`, assert `medium.busyNs == 0` and no `busyNsByCpu` entry after a machine tick, while `tickCostNs`, `activeTicks`, and their CPU maps still increment.

- [ ] **Step 2: Run the focused test and verify RED.**

Run: `./gradlew test --tests com.xt9y.features.xtprofile.XTProfileRouteTimingAndSearchTest`

Expected: failure because current GT sampling still adds busy time.

- [ ] **Step 3: Remove GT-active-time accumulation from `accountRunningTick`.**

Keep the sampling boundary update and tick-cost/active-tick accounting. Remove writes to `MediumRecord.busyNs` and `busyNsByCpu` from this method only.

- [ ] **Step 4: Verify panel aggregation needs no structural change.**

`XTProfilePanelAggregator` must continue sorting by `busyNs` and displaying `craftTimeMillis = busyNs / 1_000_000.0`; after this task that field now exclusively contains completed AE2 return latency.

- [ ] **Step 5: Run all XTProfile tests and verify GREEN.**

Run: `./gradlew test --tests 'com.xt9y.features.xtprofile.*'`

Expected: all XTProfile tests pass.

- [ ] **Step 6: Commit.**

Commit message: `fix: use AE2 return latency for route time`

---

### Task 5: Full verification and artifact

**Files:**
- No production file changes expected.

**Interfaces:**
- Consumes: completed implementation from Tasks 1-4.
- Produces: verified CI artifact for in-game Assembly Line testing.

- [ ] **Step 1: Run/observe the full repository CI for the final `xtprofile` head.**

Required successful stages: workspace setup, compile, post-build checks/tests, dedicated server up to 90 seconds, no-server-errors check, prerelease-dependency check, asset check, artifact upload.

- [ ] **Step 2: If CI fails, inspect the failing job logs and fix only the demonstrated issue, then rerun CI.**

- [ ] **Step 3: Download the main non-dev/non-sources/non-predowngrade JAR artifact.**

- [ ] **Step 4: Verify the artifact digest and provide it for in-game testing.**

The in-game success criterion is that the Assembly Line row's `Time` becomes the measured interval from successful ingredient/pattern dispatch until AE2 accepts the expected Assembly Line output back, independent of the Assembly Line controller's active/progress flags.
