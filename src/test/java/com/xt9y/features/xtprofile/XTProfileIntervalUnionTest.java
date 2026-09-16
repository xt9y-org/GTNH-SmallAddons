package com.xt9y.features.xtprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class XTProfileIntervalUnionTest {

    @Test
    void overlappingParallelOperationsOnlyCountWallClockTimeOnce() {
        XTProfileIntervalUnion intervals = new XTProfileIntervalUnion();

        assertEquals(300L, intervals.add("medium:mixer", 0L, 300L));
        assertEquals(30L, intervals.add("medium:mixer", 30L, 330L));
        assertEquals(330L, intervals.covered("medium:mixer"));
    }

    @Test
    void sequentialOperationsStillAccumulateTheirFullDurations() {
        XTProfileIntervalUnion intervals = new XTProfileIntervalUnion();

        assertEquals(100L, intervals.add("medium:assline", 0L, 100L));
        assertEquals(100L, intervals.add("medium:assline", 200L, 300L));
        assertEquals(200L, intervals.covered("medium:assline"));
    }

    @Test
    void lateLongOperationCanFillPreviouslyUncoveredGaps() {
        XTProfileIntervalUnion intervals = new XTProfileIntervalUnion();

        assertEquals(100L, intervals.add("medium:mixer", 100L, 200L));
        assertEquals(100L, intervals.add("medium:mixer", 300L, 400L));
        assertEquals(300L, intervals.add("medium:mixer", 0L, 500L));
        assertEquals(500L, intervals.covered("medium:mixer"));
    }
}
