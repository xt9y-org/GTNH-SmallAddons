package com.xt9y.features.xtprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class XTProfileRouteTimeAccountingTest {

    @Test
    void runningSamplesAddOnlyActualMachineWallClockTime() {
        XTProfileData.MediumRecord medium = new XTProfileData.MediumRecord();

        XTProfileRouteTracker.accountRunningTick(medium, 1L, 100L, 300L, 10L);
        XTProfileRouteTracker.accountRunningTick(medium, 1L, 300L, 450L, 20L);

        assertEquals(350L, medium.busyNs);
        assertEquals(350L, medium.busyNsByCpu.get(1L));
        assertEquals(30L, medium.tickCostNs);
        assertEquals(2L, medium.activeTicks);
    }

    @Test
    void cpuMachineTimeStaysIndependentWhileAllTimeAccumulatesBoth() {
        XTProfileData.MediumRecord medium = new XTProfileData.MediumRecord();

        XTProfileRouteTracker.accountRunningTick(medium, 1L, 100L, 300L, 10L);
        XTProfileRouteTracker.accountRunningTick(medium, 2L, 300L, 450L, 20L);

        assertEquals(350L, medium.busyNs);
        assertEquals(200L, medium.busyNsByCpu.get(1L));
        assertEquals(150L, medium.busyNsByCpu.get(2L));
    }
}
