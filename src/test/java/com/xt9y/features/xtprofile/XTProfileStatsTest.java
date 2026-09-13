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

    @Test
    void convertsMsptToRealTpsAndCapsAtTwenty() {
        assertEquals(20.0, XTProfileStats.tpsFromMspt(12.5), 0.0001);
        assertEquals(20.0, XTProfileStats.tpsFromMspt(50.0), 0.0001);
        assertEquals(10.0, XTProfileStats.tpsFromMspt(100.0), 0.0001);
        assertEquals(5.0, XTProfileStats.tpsFromMspt(200.0), 0.0001);
    }

    @Test
    void averagesOnlyPopulatedTickSamples() {
        assertEquals(20_000_000L, XTProfileStats.averageNs(new long[] { 10_000_000L, 20_000_000L, 30_000_000L, 0 }, 3));
        assertEquals(0L, XTProfileStats.averageNs(new long[] { 0, 0 }, 0));
    }
}
