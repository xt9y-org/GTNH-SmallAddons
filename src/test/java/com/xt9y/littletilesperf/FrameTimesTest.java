package com.xt9y.littletilesperf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FrameTimesTest {

    @Test
    void nearestRankPercentilesAreMonotonic() {
        long[] sorted = {10, 10, 11, 12, 13, 15, 16, 20, 25, 40};
        assertEquals(13, FrameTimes.percentileSorted(sorted, 0.50));
        assertEquals(40, FrameTimes.percentileSorted(sorted, 0.95));
        assertEquals(40, FrameTimes.percentileSorted(sorted, 0.99));
    }

    @Test
    void emptyFrameSampleIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> FrameTimes.percentileSorted(new long[0], 0.95));
    }
}
