package com.xt9y.features.xtprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class XTProfileRouteTimeAccountingTest {

    @Test
    void completedOperationCountsOnlyMachineTicksAndUnionsParallelCoverage() {
        XTProfileData.MediumRecord medium = new XTProfileData.MediumRecord();
        XTProfileIntervalUnion all = new XTProfileIntervalUnion();
        XTProfileIntervalUnion cpu = new XTProfileIntervalUnion();

        XTProfileRouteTracker.accountCompletedMachineTicks(
            medium,
            1L,
            "medium:assline",
            "machine:assline",
            10L,
            50L,
            8_000L,
            all,
            cpu);
        XTProfileRouteTracker.accountCompletedMachineTicks(
            medium,
            1L,
            "medium:assline",
            "machine:assline",
            20L,
            50L,
            6_000L,
            all,
            cpu);

        assertEquals(2_000_000_000L, medium.busyNs);
        assertEquals(2_000_000_000L, medium.busyNsByCpu.get(1L));
        assertEquals(8_000L, medium.tickCostNs);
        assertEquals(40L, medium.activeTicks);
        assertEquals(8_000L, medium.tickCostNsByCpu.get(1L));
        assertEquals(40L, medium.activeTicksByCpu.get(1L));
    }

    @Test
    void partiallyOverlappingOperationsOnlyChargeNewMachineTicks() {
        XTProfileData.MediumRecord medium = new XTProfileData.MediumRecord();
        XTProfileIntervalUnion all = new XTProfileIntervalUnion();
        XTProfileIntervalUnion cpu = new XTProfileIntervalUnion();

        XTProfileRouteTracker.accountCompletedMachineTicks(
            medium,
            1L,
            "medium:assline",
            "machine:assline",
            10L,
            50L,
            8_000L,
            all,
            cpu);
        XTProfileRouteTracker.accountCompletedMachineTicks(
            medium,
            1L,
            "medium:assline",
            "machine:assline",
            40L,
            60L,
            4_000L,
            all,
            cpu);

        assertEquals(2_500_000_000L, medium.busyNs);
        assertEquals(50L, medium.activeTicks);
        assertEquals(10_000L, medium.tickCostNs);
        assertEquals(2_500_000_000L, medium.busyNsByCpu.get(1L));
        assertEquals(50L, medium.activeTicksByCpu.get(1L));
        assertEquals(10_000L, medium.tickCostNsByCpu.get(1L));
    }

    @Test
    void zeroMachineTicksNeverTurnsQueueLatencyIntoCraftTime() {
        XTProfileData.MediumRecord medium = new XTProfileData.MediumRecord();
        XTProfileIntervalUnion all = new XTProfileIntervalUnion();
        XTProfileIntervalUnion cpu = new XTProfileIntervalUnion();

        XTProfileRouteTracker.accountCompletedMachineTicks(
            medium,
            7L,
            "medium:queued",
            "machine:mixer",
            100L,
            100L,
            999_999_999L,
            all,
            cpu);

        assertEquals(0L, medium.busyNs);
        assertEquals(0L, medium.busyNsByCpu.getOrDefault(7L, 0L));
        assertEquals(0L, medium.tickCostNs);
        assertEquals(0L, medium.activeTicks);
    }
}
