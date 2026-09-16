package com.xt9y.features.xtprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class XTProfileRouteTimeAccountingTest {

    @Test
    void ae2DispatchToReturnLatencyIsTimeEvenWithoutVisibleMachineTicks() {
        XTProfileData.MediumRecord medium = new XTProfileData.MediumRecord();
        XTProfileIntervalUnion all = new XTProfileIntervalUnion();
        XTProfileIntervalUnion cpu = new XTProfileIntervalUnion();

        XTProfileRouteTracker
            .accountCompletedLatency(medium, 7L, "medium:assline", 1_000_000_000L, 1_650_000_000L, all, cpu);

        assertEquals(650_000_000L, medium.busyNs);
        assertEquals(650_000_000L, medium.busyNsByCpu.get(7L));
        assertEquals(0L, medium.activeTicks);
        assertEquals(0L, medium.tickCostNs);
    }

    @Test
    void overlappingAe2LatencyIsUnionedPerInterface() {
        XTProfileData.MediumRecord medium = new XTProfileData.MediumRecord();
        XTProfileIntervalUnion all = new XTProfileIntervalUnion();
        XTProfileIntervalUnion cpu = new XTProfileIntervalUnion();

        XTProfileRouteTracker
            .accountCompletedLatency(medium, 1L, "medium:assline", 100_000_000L, 500_000_000L, all, cpu);
        XTProfileRouteTracker
            .accountCompletedLatency(medium, 1L, "medium:assline", 300_000_000L, 700_000_000L, all, cpu);

        assertEquals(600_000_000L, medium.busyNs);
        assertEquals(600_000_000L, medium.busyNsByCpu.get(1L));
    }

    @Test
    void machineTicksOnlyProvideTpsAccounting() {
        XTProfileData.MediumRecord medium = new XTProfileData.MediumRecord();
        XTProfileIntervalUnion all = new XTProfileIntervalUnion();
        XTProfileIntervalUnion cpu = new XTProfileIntervalUnion();

        XTProfileRouteTracker
            .accountCompletedMachineTicks(medium, 1L, "medium:assline", "machine:assline", 10L, 50L, 8_000L, all, cpu);
        XTProfileRouteTracker
            .accountCompletedMachineTicks(medium, 1L, "medium:assline", "machine:assline", 20L, 50L, 6_000L, all, cpu);

        assertEquals(0L, medium.busyNs);
        assertEquals(0L, medium.busyNsByCpu.getOrDefault(1L, 0L));
        assertEquals(8_000L, medium.tickCostNs);
        assertEquals(40L, medium.activeTicks);
        assertEquals(8_000L, medium.tickCostNsByCpu.get(1L));
        assertEquals(40L, medium.activeTicksByCpu.get(1L));
    }

    @Test
    void partiallyOverlappingMachineTicksOnlyChargeNewTpsCoverage() {
        XTProfileData.MediumRecord medium = new XTProfileData.MediumRecord();
        XTProfileIntervalUnion all = new XTProfileIntervalUnion();
        XTProfileIntervalUnion cpu = new XTProfileIntervalUnion();

        XTProfileRouteTracker
            .accountCompletedMachineTicks(medium, 1L, "medium:assline", "machine:assline", 10L, 50L, 8_000L, all, cpu);
        XTProfileRouteTracker
            .accountCompletedMachineTicks(medium, 1L, "medium:assline", "machine:assline", 40L, 60L, 4_000L, all, cpu);

        assertEquals(0L, medium.busyNs);
        assertEquals(50L, medium.activeTicks);
        assertEquals(10_000L, medium.tickCostNs);
        assertEquals(0L, medium.busyNsByCpu.getOrDefault(1L, 0L));
        assertEquals(50L, medium.activeTicksByCpu.get(1L));
        assertEquals(10_000L, medium.tickCostNsByCpu.get(1L));
    }
}
