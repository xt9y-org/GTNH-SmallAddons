package com.xt9y.features.xtprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class XTProfileRouteTimeAccountingTest {

    @Test
    void overlappingOperationsOnOneCpuOnlyAddNewWallClockCoverage() {
        XTProfileData.MediumRecord medium = new XTProfileData.MediumRecord();
        XTProfileIntervalUnion all = new XTProfileIntervalUnion();
        XTProfileIntervalUnion cpu = new XTProfileIntervalUnion();

        XTProfileRouteTracker.accountCompletedInterval(medium, 1L, "medium:mixer", 0L, 300L, all, cpu);
        XTProfileRouteTracker.accountCompletedInterval(medium, 1L, "medium:mixer", 30L, 330L, all, cpu);

        assertEquals(330L, medium.busyNs);
        assertEquals(330L, medium.busyNsByCpu.get(1L));
        assertEquals(0L, medium.tickCostNs);
        assertTrue(medium.tickCostNsByCpu.isEmpty());
    }

    @Test
    void allTimeMergesOverlapAcrossCpusWhileCpuTimeStaysIndependent() {
        XTProfileData.MediumRecord medium = new XTProfileData.MediumRecord();
        XTProfileIntervalUnion all = new XTProfileIntervalUnion();
        XTProfileIntervalUnion cpu1 = new XTProfileIntervalUnion();
        XTProfileIntervalUnion cpu2 = new XTProfileIntervalUnion();

        XTProfileRouteTracker.accountCompletedInterval(medium, 1L, "medium:assline", 0L, 300L, all, cpu1);
        XTProfileRouteTracker.accountCompletedInterval(medium, 2L, "medium:assline", 30L, 330L, all, cpu2);

        assertEquals(330L, medium.busyNs);
        assertEquals(300L, medium.busyNsByCpu.get(1L));
        assertEquals(300L, medium.busyNsByCpu.get(2L));
    }
}
