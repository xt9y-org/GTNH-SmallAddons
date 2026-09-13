package com.xt9y.features.xtprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class XTProfileStatsTest {

    @Test
    void ignoresEmptyGregTechTimingSlots() {
        XTProfileStats.Timing timing = XTProfileStats.summarize(new int[] { 0, 100, 300, 0, 200 });

        assertEquals(3, timing.samples);
        assertEquals(200, timing.averageNs);
        assertEquals(300, timing.worstNs);
    }

    @Test
    void busyTimeOnlyAccumulatesWhileActiveAndClampsLongPauses() {
        assertEquals(500, XTProfileStats.busyDelta(1_000, 1_500, true));
        assertEquals(0, XTProfileStats.busyDelta(1_000, 1_500, false));
        assertEquals(1_000_000_000L, XTProfileStats.busyDelta(1_000, 3_000_000_000L, true));
    }
}
